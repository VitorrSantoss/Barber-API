package com.vitorsantos.barbearia_api.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vitorsantos.barbearia_api.enums.ErrorCode;

import lombok.Builder;
import lombok.Getter;

/**
 * Formato ÚNICO de corpo de erro devolvido por qualquer endpoint da API.
 * O campo "erros" só é preenchido quando há falha de validação em
 * múltiplos campos (ex: @Valid no corpo da requisição).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDTO {

  private LocalDateTime timestamp;
  private int status;
  private String erro;
  private ErrorCode codigo;
  private String mensagem;
  private String path;
  private List<String> erros;
}
