package com.vitorsantos.barbearia_api.dto;

import com.vitorsantos.barbearia_api.models.Barbeiro;

public record BarbeiroResponseDTO(
    Long id,
    String nome,
    boolean ativo) {

  public static BarbeiroResponseDTO fromEntity(Barbeiro barbeiro) {
    return new BarbeiroResponseDTO(barbeiro.getId(), barbeiro.getNome(), barbeiro.isAtivo());
  }
}