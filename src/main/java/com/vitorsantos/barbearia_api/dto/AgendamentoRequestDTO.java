package com.vitorsantos.barbearia_api.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * Todo agendamento nasce com status AGENDADO — por isso não tem campo
 * de status aqui. Pra mudar o status depois, usa o endpoint de
 * atualização de status (PATCH /agendamentos/{id}/status).
 */
public record AgendamentoRequestDTO(

    @NotNull(message = "O clienteId é obrigatório") Long clienteId,

    @NotNull(message = "O barbeiroId é obrigatório") Long barbeiroId,

    @NotNull(message = "A dataHora é obrigatória") @Future(message = "A dataHora do agendamento deve ser no futuro") LocalDateTime dataHora

) {
}