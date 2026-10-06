package com.vitorsantos.barbearia_api.dto;

import jakarta.validation.constraints.NotNull;

/** Cliente que chegou na barbearia e entra no fim da fila do barbeiro. */
public record EntradaFilaRequestDTO(
    @NotNull(message = "O clienteId é obrigatório") Long clienteId,
    @NotNull(message = "O servicoId é obrigatório") Long servicoId) {
}
