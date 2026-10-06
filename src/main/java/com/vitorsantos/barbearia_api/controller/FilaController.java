package com.vitorsantos.barbearia_api.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vitorsantos.barbearia_api.dto.AgendamentoResponseDTO;
import com.vitorsantos.barbearia_api.dto.EntradaFilaRequestDTO;
import com.vitorsantos.barbearia_api.dto.ErrorResponseDTO;
import com.vitorsantos.barbearia_api.dto.FilaResponseDTO;
import com.vitorsantos.barbearia_api.service.FilaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Fila", description = "Fila de atendimento de cada barbeiro. Atualizações em tempo real são "
    + "publicadas via WebSocket (STOMP em /ws) no tópico /topic/fila/{barbeiroId}.")
@RestController
@RequestMapping("/barbeiros")
@RequiredArgsConstructor
public class FilaController {

  private final FilaService filaService;

  @Operation(
      summary = "Consulta a fila de um barbeiro",
      description = "Retorna quem está em atendimento, o próximo, o total aguardando e os clientes "
          + "AGUARDANDO em ordem de chegada, com a posição de cada um (1 = próximo).")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Fila retornada com sucesso",
          content = @Content(schema = @Schema(implementation = FilaResponseDTO.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Barbeiro não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @GetMapping("/{id}/fila")
  public FilaResponseDTO consultarFilaPorBarbeiro(@PathVariable Long id) {
    return filaService.consultarFilaPorBarbeiro(id);
  }

  @Operation(
      summary = "Consulta a fila de todos os barbeiros ativos de uma vez",
      description = "Pensado para um painel geral da barbearia, mostrando a fila de todo mundo ao mesmo tempo.")
  @ApiResponse(responseCode = "200", description = "Lista de filas retornada com sucesso")
  @GetMapping("/fila")
  public List<FilaResponseDTO> consultarFilaDeTodosBarbeiros() {
    return filaService.consultarFilaDeTodosBarbeiros();
  }

  @Operation(
      summary = "Coloca um cliente no fim da fila do barbeiro",
      description = "Para o cliente que chegou sem horário marcado. O agendamento é criado direto como "
          + "AGUARDANDO. Um cliente só pode estar em uma fila por vez.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Cliente entrou na fila",
          content = @Content(schema = @Schema(implementation = AgendamentoResponseDTO.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Dados inválidos",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Barbeiro, cliente ou serviço não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Cliente já está em uma fila, ou barbeiro/serviço inativo",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PostMapping("/{id}/fila")
  public ResponseEntity<AgendamentoResponseDTO> entrarNaFila(
      @PathVariable Long id,
      @RequestBody @Valid EntradaFilaRequestDTO dto) {
    AgendamentoResponseDTO entrada = filaService.entrarNaFila(id, dto);
    return ResponseEntity
        .created(URI.create("/agendamentos/" + entrada.id()))
        .body(entrada);
  }

  @Operation(
      summary = "Barbeiro chama o próximo cliente da fila",
      description = "Move o primeiro cliente AGUARDANDO para EM_ATENDIMENTO. Para concluir o atendimento, "
          + "use PATCH /agendamentos/{id}/status com FINALIZADO.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Atendimento iniciado",
          content = @Content(schema = @Schema(implementation = AgendamentoResponseDTO.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Barbeiro não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Fila vazia ou barbeiro já está atendendo alguém",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PostMapping("/{id}/fila/proximo")
  public AgendamentoResponseDTO chamarProximo(@PathVariable Long id) {
    return filaService.chamarProximo(id);
  }

}
