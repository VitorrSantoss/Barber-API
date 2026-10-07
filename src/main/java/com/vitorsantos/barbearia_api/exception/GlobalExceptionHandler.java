package com.vitorsantos.barbearia_api.exception;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.vitorsantos.barbearia_api.dto.ErrorResponseDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler global de exceções. Todo erro lançado por qualquer controller
 * passa por aqui e sai no mesmo formato (ErrorResponseDTO), sem stack trace.
 *
 * - BusinessException (e subclasses) -> status definido pela própria exceção
 * - @Valid / corpo ilegível / parâmetro de tipo errado -> 400
 * - rota inexistente -> 404, método não suportado -> 405 etc. (herdados de
 *   ResponseEntityExceptionHandler, que cobre todas as exceções do Spring MVC)
 * - conflito de concorrência ou de integridade no banco -> 409
 * - Exception (fallback, bug real) -> 500
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponseDTO> handleBusinessException(BusinessException ex, WebRequest request) {
    log.warn("Erro de negócio [{}]: {}", ex.getErrorCode(), ex.getMessage());
    return responder(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request, null);
  }

  @ExceptionHandler({ OptimisticLockingFailureException.class, PessimisticLockingFailureException.class })
  public ResponseEntity<ErrorResponseDTO> handleConcorrencia(RuntimeException ex, WebRequest request) {
    log.warn("Conflito de concorrência: {}", ex.getMessage());
    return responder(HttpStatus.CONFLICT, ErrorCode.CONFLITO_CONCORRENCIA,
        "O registro foi alterado por outra operação ao mesmo tempo. Consulte novamente e tente de novo.",
        request, null);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponseDTO> handleDataIntegrityViolation(DataIntegrityViolationException ex,
      WebRequest request) {
    log.warn("Violação de integridade no banco: {}", ex.getMostSpecificCause().getMessage());
    return responder(HttpStatus.CONFLICT, ErrorCode.VIOLACAO_INTEGRIDADE,
        "A operação viola uma restrição de integridade dos dados", request, null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex, WebRequest request) {
    log.error("Erro inesperado", ex);
    return responder(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.ERRO_INTERNO,
        "Ocorreu um erro interno. Tente novamente mais tarde.", request, null);
  }

  // ─────────── Exceções padrão do Spring MVC (sobrescritas do pai) ───────────

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    List<String> erros = ex.getBindingResult().getFieldErrors().stream()
        .map(this::formatarErroDeCampo)
        .toList();
    log.warn("Erro de validação: {}", erros);
    return comoObject(responder(HttpStatus.BAD_REQUEST, ErrorCode.ERRO_VALIDACAO,
        "Um ou mais campos estão inválidos", request, erros));
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    List<String> erros = ex.getAllErrors().stream()
        .map(erro -> erro.getDefaultMessage())
        .toList();
    return comoObject(responder(HttpStatus.BAD_REQUEST, ErrorCode.ERRO_VALIDACAO,
        "Um ou mais parâmetros estão inválidos", request, erros));
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    log.warn("Corpo da requisição ilegível: {}", ex.getMessage());
    return comoObject(responder(HttpStatus.BAD_REQUEST, ErrorCode.REQUISICAO_INVALIDA,
        "Corpo da requisição inválido ou mal formatado (verifique o JSON, datas e valores de enum)",
        request, null));
  }

  /** Ex: GET /clientes/abc, onde o id deveria ser numérico. */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
      HttpStatusCode status, WebRequest request) {
    String mensagem = "Valor inválido para o parâmetro '%s': %s".formatted(ex.getPropertyName(), ex.getValue());
    return comoObject(responder(HttpStatus.BAD_REQUEST, ErrorCode.REQUISICAO_INVALIDA, mensagem, request, null));
  }

  /** Demais exceções do Spring MVC (404 de rota, 405, 415...). */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
      HttpStatusCode statusCode, WebRequest request) {
    HttpStatus status = HttpStatus.resolve(statusCode.value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    ErrorCode codigo = switch (status) {
      case NOT_FOUND -> ErrorCode.RECURSO_NAO_ENCONTRADO;
      case METHOD_NOT_ALLOWED -> ErrorCode.METODO_NAO_PERMITIDO;
      default -> status.is5xxServerError() ? ErrorCode.ERRO_INTERNO : ErrorCode.REQUISICAO_INVALIDA;
    };
    String mensagem = switch (status) {
      case NOT_FOUND -> "Recurso não encontrado";
      case METHOD_NOT_ALLOWED -> "Método HTTP não suportado para este endpoint";
      case UNSUPPORTED_MEDIA_TYPE -> "Content-Type não suportado. Envie application/json";
      default -> status.is5xxServerError() ? "Ocorreu um erro interno. Tente novamente mais tarde."
          : "Requisição inválida";
    };

    if (status.is5xxServerError()) {
      log.error("Erro interno do Spring MVC", ex);
    }

    ErrorResponseDTO corpo = montarCorpo(status, codigo, mensagem, request, null);
    return ResponseEntity.status(status).headers(headers).body(corpo);
  }

  // ───────────────────────────── utilitários ─────────────────────────────

  private ResponseEntity<ErrorResponseDTO> responder(HttpStatus status, ErrorCode codigo, String mensagem,
      WebRequest request, List<String> erros) {
    return ResponseEntity.status(status).body(montarCorpo(status, codigo, mensagem, request, erros));
  }

  private ErrorResponseDTO montarCorpo(HttpStatus status, ErrorCode codigo, String mensagem, WebRequest request,
      List<String> erros) {
    return ErrorResponseDTO.builder()
        .timestamp(LocalDateTime.now())
        .status(status.value())
        .erro(status.getReasonPhrase())
        .codigo(codigo)
        .mensagem(mensagem)
        .path(request.getDescription(false).replace("uri=", ""))
        .erros(erros)
        .build();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private ResponseEntity<Object> comoObject(ResponseEntity<ErrorResponseDTO> response) {
    return (ResponseEntity) response;
  }

  private String formatarErroDeCampo(FieldError erro) {
    return erro.getField() + ": " + erro.getDefaultMessage();
  }
}
