package com.vitorsantos.barbearia_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados que o cliente da API envia para cadastrar um Cliente.
 * Propositalmente NÃO tem id, dataCadastro nem ultimoLogin — esses campos
 * são responsabilidade do sistema, nunca devem vir do request.
 */
public record ClienteRequestDTO(

    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    String nome,

    @NotBlank(message = "O telefone é obrigatório")
    @Pattern(regexp = "^[0-9]{11}$", message = "O telefone deve conter 11 dígitos (DDD + número)")
    String numero

) {

  /**
   * Normaliza antes da validação: o nome perde espaços nas pontas e o
   * telefone fica só com dígitos, então "(81) 99999-0000" e "81999990000"
   * são o mesmo cliente.
   */
  public ClienteRequestDTO {
    nome = nome == null ? null : nome.strip();
    numero = normalizarTelefone(numero);
  }

  public static String normalizarTelefone(String telefone) {
    return telefone == null ? null : telefone.replaceAll("\\D", "");
  }
}
