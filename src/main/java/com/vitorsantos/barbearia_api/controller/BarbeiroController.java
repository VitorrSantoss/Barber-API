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

import com.vitorsantos.barbearia_api.dto.BarbeiroLoginRequestDTO;
import com.vitorsantos.barbearia_api.dto.BarbeiroRequestDTO;
import com.vitorsantos.barbearia_api.dto.BarbeiroResponseDTO;
import com.vitorsantos.barbearia_api.dto.ErrorResponseDTO;
import com.vitorsantos.barbearia_api.service.BarbeiroAuthService;
import com.vitorsantos.barbearia_api.service.BarbeiroService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Barbeiros", description = "Cadastro de barbeiros")
@RestController
@RequestMapping("/barbeiros")
@RequiredArgsConstructor
public class BarbeiroController {

  private final BarbeiroService barbeiroService;
  private final BarbeiroAuthService barbeiroAuthService;

  @Operation(summary = "Lista os barbeiros cadastrados")
  @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
  @GetMapping
  public List<BarbeiroResponseDTO> listarBarbeiros(
      @Parameter(description = "true para listar apenas barbeiros ativos")
      @RequestParam(defaultValue = "false") boolean ativos) {
    return barbeiroService.listarBarbeiros(ativos);
  }

  @Operation(summary = "Busca um barbeiro pelo ID")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Barbeiro encontrado"),
      @ApiResponse(
          responseCode = "404",
          description = "Barbeiro não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @GetMapping("/{id}")
  public BarbeiroResponseDTO buscarBarbeiro(@PathVariable Long id) {
    return barbeiroService.buscarPorId(id);
  }

  @Operation(
      summary = "Login do barbeiro pela senha de 6 dígitos",
      description = "A senha é única por barbeiro e identifica quem está entrando. Devolve o barbeiro "
          + "autenticado. Depois de várias senhas erradas seguidas o IP fica bloqueado por alguns segundos.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Senha correta",
          content = @Content(schema = @Schema(implementation = BarbeiroResponseDTO.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Senha fora do formato (6 dígitos)",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "401",
          description = "Senha incorreta",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "429",
          description = "Muitas tentativas incorretas; aguarde o bloqueio terminar",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PostMapping("/login")
  public BarbeiroResponseDTO login(@RequestBody @Valid BarbeiroLoginRequestDTO dto, HttpServletRequest request) {
    // getRemoteAddr de propósito: X-Forwarded-For pode ser forjado pelo cliente para fugir do bloqueio.
    return barbeiroAuthService.login(dto.senha(), request.getRemoteAddr());
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

  @Operation(
      summary = "Atualiza nome e/ou situação (ativo) de um barbeiro",
      description = "Omitir \"ativo\" mantém a situação atual. Desativar exige que não haja agendamentos em aberto.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Barbeiro atualizado"),
      @ApiResponse(
          responseCode = "400",
          description = "Dados inválidos",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Barbeiro não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Barbeiro possui agendamentos em aberto",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PutMapping("/{id}")
  public BarbeiroResponseDTO atualizarBarbeiro(@PathVariable Long id, @RequestBody @Valid BarbeiroRequestDTO dto) {
    return barbeiroService.atualizarBarbeiro(id, dto);
  }

  @Operation(
      summary = "Remove (desativa) um barbeiro",
      description = "Exclusão lógica: o barbeiro fica inativo e o histórico de atendimentos é preservado.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Barbeiro desativado com sucesso"),
      @ApiResponse(
          responseCode = "404",
          description = "Barbeiro não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Barbeiro possui agendamentos em aberto",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletarBarbeiro(@PathVariable Long id) {
    barbeiroService.desativarBarbeiro(id);
    return ResponseEntity.noContent().build();
  }

}
