package com.vitorsantos.barbearia_api.dto;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;

import jakarta.validation.constraints.NotNull;

public record AtualizarStatusAgendamentoDTO(
    @NotNull(message = "O novoStatus é obrigatório") StatusAgendamento novoStatus) {
}