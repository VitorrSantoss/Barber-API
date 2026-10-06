package com.vitorsantos.barbearia_api.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * Horário marcado para o futuro. Todo agendamento nasce com status
 * AGENDADO — por isso não tem campo de status aqui. Para entrar direto na
 * fila (sem horário), use POST /barbeiros/{id}/fila.
 */
public record AgendamentoRequestDTO(

    @NotNull(message = "O clienteId é obrigatório") Long clienteId,

    @NotNull(message = "O barbeiroId é obrigatório") Long barbeiroId,

    @NotNull(message = "O servicoId é obrigatório") Long servicoId,

    @NotNull(message = "A dataHora é obrigatória") @Future(message = "A dataHora do agendamento deve ser no futuro") LocalDateTime dataHora

) {
}
