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
 * CRUD básico de Agendamento + endpoint de troca de status. É o troca de
 * status (PATCH .../status) que efetivamente move um cliente para a fila
 * (AGUARDANDO) — sem chamar esse endpoint, a fila da issue #7/#8 nunca
 * vai ter ninguém dentro pra você testar.
 */
@Tag(name = "Agendamentos", description = "Cadastro de agendamentos e controle de status (fila)")
@RestController
@RequestMapping("/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {

  private final AgendamentoService agendamentoService;

  @Operation(summary = "Lista todos os agendamentos")
  @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
  @GetMapping
  public List<AgendamentoResponseDTO> listarAgendamentos() {
    return agendamentoService.listarAgendamentos();
  }

  @Operation(
      summary = "Cria um novo agendamento",
      description = "O agendamento sempre nasce com status AGENDADO. Para movê-lo pela fila "
          + "(AGUARDANDO -> EM_ATENDIMENTO -> FINALIZADO), use o endpoint de atualização de status.")
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
          description = "Cliente ou barbeiro não encontrado",
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
      description = "Ex: mandar { \"novoStatus\": \"AGUARDANDO\" } quando o cliente chega na "
          + "barbearia — a partir daí ele passa a aparecer na fila do barbeiro (issues #7/#8).")
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
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PatchMapping("/{id}/status")
  public AgendamentoResponseDTO atualizarStatus(
      @PathVariable Long id,
      @RequestBody @Valid AtualizarStatusAgendamentoDTO dto) {
    return agendamentoService.atualizarStatus(id, dto.novoStatus());
  }

}