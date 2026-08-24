package com.vitorsantos.barbearia_api.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vitorsantos.barbearia_api.dto.BarbeiroRequestDTO;
import com.vitorsantos.barbearia_api.dto.BarbeiroResponseDTO;
import com.vitorsantos.barbearia_api.dto.ErrorResponseDTO;
import com.vitorsantos.barbearia_api.service.BarbeiroService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * CRUD básico de Barbeiro. Existe principalmente para dar suporte às
 * issues #7/#8 (fila) e #9 (WebSocket) — sem isso não tem como popular
 * barbeiro nenhum para testar a fila via API.
 */
@Tag(name = "Barbeiros", description = "Cadastro de barbeiros")
@RestController
@RequestMapping("/barbeiros")
@RequiredArgsConstructor
public class BarbeiroController {

  private final BarbeiroService barbeiroService;

  @Operation(summary = "Lista todos os barbeiros cadastrados")
  @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
  @GetMapping
  public List<BarbeiroResponseDTO> listarBarbeiros() {
    return barbeiroService.listarBarbeiros();
  }

  @Operation(summary = "Cadastra um novo barbeiro")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Barbeiro cadastrado com sucesso",
          content = @Content(schema = @Schema(implementation = BarbeiroResponseDTO.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Dados inválidos",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PostMapping
  public ResponseEntity<BarbeiroResponseDTO> cadastrarBarbeiro(@RequestBody @Valid BarbeiroRequestDTO dto) {
    BarbeiroResponseDTO barbeiroCriado = barbeiroService.cadastrarBarbeiro(dto);
    return ResponseEntity
        .created(URI.create("/barbeiros/" + barbeiroCriado.id()))
        .body(barbeiroCriado);
  }

  @Operation(summary = "Remove um barbeiro pelo ID")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Barbeiro removido com sucesso"),
      @ApiResponse(
          responseCode = "404",
          description = "Barbeiro não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletarBarbeiro(@PathVariable Long id) {
    barbeiroService.deletarBarbeiro(id);
    return ResponseEntity.noContent().build();
  }

}