package com.vitorsantos.barbearia_api.dto;

import java.math.BigDecimal;

import com.vitorsantos.barbearia_api.models.Servico;

public record ServicoResponseDTO(
    Long id,
    String nome,
    String descricao,
    BigDecimal preco,
    int duracaoMinutos,
    boolean ativo) {

  public static ServicoResponseDTO fromEntity(Servico servico) {
    return new ServicoResponseDTO(
        servico.getId(),
        servico.getNome(),
        servico.getDescricao(),
        servico.getPreco(),
        servico.getDuracaoMinutos(),
        servico.isAtivo());
  }
}
