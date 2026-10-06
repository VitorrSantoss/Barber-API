package com.vitorsantos.barbearia_api.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vitorsantos.barbearia_api.dto.AgendamentoRequestDTO;
import com.vitorsantos.barbearia_api.dto.AgendamentoResponseDTO;
import com.vitorsantos.barbearia_api.dto.AtualizarStatusAgendamentoDTO;
import com.vitorsantos.barbearia_api.dto.ErrorResponseDTO;
import com.vitorsantos.barbearia_api.dto.PosicaoFilaDTO;
import com.vitorsantos.barbearia_api.service.AgendamentoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Agendamentos (horário marcado ou entrada na fila) e controle de status.
 * Fluxo: AGENDADO -> AGUARDANDO -> EM_ATENDIMENTO -> FINALIZADO, com
 * CANCELADO a partir de AGENDADO ou AGUARDANDO.
 */
@Tag(name = "Agendamentos", description = "Cadastro de agendamentos e controle de status (fila)")
@RestController
@RequestMapping("/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {

  private final AgendamentoService agendamentoService;

  @Operation(summary = "Lista todos os agendamentos (mais recentes primeiro)")
  @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
  @GetMapping
  public List<AgendamentoResponseDTO> listarAgendamentos() {
    return agendamentoService.listarAgendamentos();
  }

  @Operation(summary = "Busca um agendamento pelo ID")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Agendamento encontrado"),
      @ApiResponse(
          responseCode = "404",
          description = "Agendamento não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @GetMapping("/{id}")
  public AgendamentoResponseDTO buscarAgendamento(@PathVariable Long id) {
    return agendamentoService.buscarPorId(id);
  }

  @Operation(
      summary = "Consulta a posição do agendamento na fila do barbeiro",
      description = "Posição 1 = próximo a ser atendido. Posição e pessoasAFrente vêm nulas quando o "
          + "agendamento não está AGUARDANDO (ex: já em atendimento ou finalizado).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Posição retornada"),
      @ApiResponse(
          responseCode = "404",
          description = "Agendamento não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @GetMapping("/{id}/posicao")
  public PosicaoFilaDTO consultarPosicao(@PathVariable Long id) {
    return agendamentoService.consultarPosicao(id);
  }

  @Operation(
      summary = "Cria um agendamento com horário marcado",
      description = "O agendamento nasce AGENDADO e entra sozinho na fila (AGUARDANDO) quando a data/hora "
          + "chega. Para entrar na fila na hora, use POST /barbeiros/{id}/fila.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Agendamento criado com sucesso",
          content = @Content(schema = @Schema(implementation = AgendamentoResponseDTO.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Dados inválidos",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Cliente, barbeiro ou serviço não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Barbeiro ou serviço inativo",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PostMapping
  public ResponseEntity<AgendamentoResponseDTO> cadastrarAgendamento(
      @RequestBody @Valid AgendamentoRequestDTO dto) {
    AgendamentoResponseDTO agendamentoCriado = agendamentoService.cadastrarAgendamento(dto);
    return ResponseEntity
        .created(URI.create("/agendamentos/" + agendamentoCriado.id()))
        .body(agendamentoCriado);
  }

  @Operation(
      summary = "Atualiza o status de um agendamento",
      description = "Ex: { \"novoStatus\": \"FINALIZADO\" } para concluir o atendimento, ou "
          + "{ \"novoStatus\": \"CANCELADO\" } para tirar o cliente da fila. Iniciar atendimento "
          + "(EM_ATENDIMENTO) só é permitido para o primeiro da fila e com o barbeiro livre.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Status atualizado com sucesso",
          content = @Content(schema = @Schema(implementation = AgendamentoResponseDTO.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Transição de status inválida (ex: FINALIZADO -> AGUARDANDO)",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Agendamento não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Regra da fila violada (fora da ordem, barbeiro ocupado, cliente já na fila...)",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PatchMapping("/{id}/status")
  public AgendamentoResponseDTO atualizarStatus(
      @PathVariable Long id,
      @RequestBody @Valid AtualizarStatusAgendamentoDTO dto) {
    return agendamentoService.atualizarStatus(id, dto.novoStatus());
  }

  @Operation(
      summary = "Cliente confirma presença antes do dia do agendamento",
      description = "Não muda o status do agendamento — só marca um selo que a fila "
          + "exibe pro barbeiro, indicando que esse cliente confirmou que vai comparecer.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Presença confirmada com sucesso",
          content = @Content(schema = @Schema(implementation = AgendamentoResponseDTO.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Agendamento já FINALIZADO ou CANCELADO, não pode ser confirmado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Agendamento não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PatchMapping("/{id}/confirmar-presenca")
  public AgendamentoResponseDTO confirmarPresenca(@PathVariable Long id) {
    return agendamentoService.confirmarPresenca(id);
  }

}
