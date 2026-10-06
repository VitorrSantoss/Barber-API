package com.vitorsantos.barbearia_api.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vitorsantos.barbearia_api.dto.ClienteRequestDTO;
import com.vitorsantos.barbearia_api.dto.ClienteResponseDTO;
import com.vitorsantos.barbearia_api.dto.ErrorResponseDTO;
import com.vitorsantos.barbearia_api.service.ClienteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Clientes", description = "Cadastro e consulta de clientes da barbearia")
@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

  private final ClienteService clienteService;

  @Operation(summary = "Lista todos os clientes cadastrados")
  @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
  @GetMapping
  public List<ClienteResponseDTO> listarClientes() {
    return clienteService.listarClientes();
  }

  @Operation(summary = "Busca um cliente pelo ID")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
      @ApiResponse(
          responseCode = "404",
          description = "Cliente não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @GetMapping("/{id}")
  public ClienteResponseDTO buscarCliente(@PathVariable Long id) {
    return clienteService.buscarPorId(id);
  }

  @Operation(
      summary = "Busca um cliente pelo telefone",
      description = "O telefone é a chave de identificação do cliente. Aceita com ou sem máscara.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
      @ApiResponse(
          responseCode = "404",
          description = "Nenhum cliente com esse telefone",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @GetMapping("/telefone/{numero}")
  public ClienteResponseDTO buscarClientePorTelefone(@PathVariable String numero) {
    return clienteService.buscarPorTelefone(numero);
  }

  @Operation(
      summary = "Cadastra um novo cliente",
      description = "O telefone é a chave de identificação do cliente e deve ser único. "
          + "Aceita telefone com ou sem máscara - a API normaliza para apenas dígitos antes de salvar.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Cliente cadastrado com sucesso",
          content = @Content(schema = @Schema(implementation = ClienteResponseDTO.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Dados inválidos (ex: nome em branco, telefone fora do formato esperado)",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Já existe um cliente cadastrado com esse telefone",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ClienteResponseDTO> cadastrarCliente(@RequestBody @Valid ClienteRequestDTO dto) {
    ClienteResponseDTO clienteCriado = clienteService.cadastrarCliente(dto);
    return ResponseEntity
        .created(URI.create("/clientes/" + clienteCriado.id()))
        .body(clienteCriado);
  }

  @Operation(
      summary = "Remove um cliente pelo ID",
      description = "Cliente sem histórico é apagado; cliente com histórico de atendimentos é anonimizado "
          + "(o histórico do barbeiro continua íntegro e o telefone fica livre para novo cadastro).")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Cliente removido com sucesso"),
      @ApiResponse(
          responseCode = "404",
          description = "Cliente não encontrado",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Cliente possui agendamento em aberto",
          content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletarCliente(@PathVariable Long id) {
    clienteService.deletarCliente(id);
    return ResponseEntity.noContent().build();
  }

}
