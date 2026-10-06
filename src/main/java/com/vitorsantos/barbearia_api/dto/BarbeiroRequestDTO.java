package com.vitorsantos.barbearia_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param ativo opcional; no cadastro o padrão é true. Desativar um barbeiro
 *              com agendamentos em aberto é bloqueado.
 */
public record BarbeiroRequestDTO(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    String nome,

    Boolean ativo) {

  public BarbeiroRequestDTO {
    nome = nome == null ? null : nome.strip();
  }
}
