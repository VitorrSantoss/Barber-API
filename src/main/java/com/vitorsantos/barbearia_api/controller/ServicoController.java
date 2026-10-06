package com.vitorsantos.barbearia_api.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vitorsantos.barbearia_api.dto.ErrorResponseDTO;
import com.vitorsantos.barbearia_api.dto.ServicoRequestDTO;
import com.vitorsantos.barbearia_api.dto.ServicoResponseDTO;
import com.vitorsantos.barbearia_api.service.ServicoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Serviços", description = "Serviços oferecidos pela barbearia (corte, barba...)")
@RestController
@RequestMapping("/servicos")
@RequiredArgsConstructor
public class ServicoController {

  private final ServicoService servicoService;

  @Operation(summary = "Lista os serviços")
  @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
  @GetMapping
  public List<ServicoResponseDTO> listarServicos(
      @Parameter(description = "true para listar apenas serviços ativos")
      @RequestParam(defaultValue = "false") boolean ativos) {
    return servicoService.listarServicos(ativos);
  }

  @Operation(summary = "Busca um serviço pelo ID")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Serviço encontrado"),
      @ApiResponse(
          responseCode = "404",
          description = "Serviço não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @GetMapping("/{id}")
  public ServicoResponseDTO buscarServico(@PathVariable Long id) {
    return servicoService.buscarPorId(id);
  }

  @Operation(summary = "Cadastra um novo serviço")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Serviço cadastrado com sucesso",
          content = @Content(schema = @Schema(implementation = ServicoResponseDTO.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Dados inválidos",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Já existe um serviço com esse nome",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PostMapping
  public ResponseEntity<ServicoResponseDTO> cadastrarServico(@RequestBody @Valid ServicoRequestDTO dto) {
    ServicoResponseDTO servicoCriado = servicoService.cadastrarServico(dto);
    return ResponseEntity
        .created(URI.create("/servicos/" + servicoCriado.id()))
        .body(servicoCriado);
  }

  @Operation(summary = "Atualiza um serviço", description = "Omitir \"ativo\" mantém a situação atual.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Serviço atualizado"),
      @ApiResponse(
          responseCode = "400",
          description = "Dados inválidos",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Serviço não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Já existe outro serviço com esse nome",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PutMapping("/{id}")
  public ServicoResponseDTO atualizarServico(@PathVariable Long id, @RequestBody @Valid ServicoRequestDTO dto) {
    return servicoService.atualizarServico(id, dto);
  }

  @Operation(
      summary = "Remove (desativa) um serviço",
      description = "Exclusão lógica: o serviço deixa de ser oferecido, mas o histórico de atendimentos é preservado.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Serviço desativado com sucesso"),
      @ApiResponse(
          responseCode = "404",
          description = "Serviço não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletarServico(@PathVariable Long id) {
    servicoService.desativarServico(id);
    return ResponseEntity.noContent().build();
  }

}
