package com.vitorsantos.barbearia_api.dto;

import java.time.LocalDateTime;

/**
 * Representa um cliente dentro da fila de um barbeiro específico.
 * confirmadoPeloCliente é o selo que diz pro barbeiro: esse cliente
 * confirmou presença antes do dia chegar (mais confiável que quem
 * entrou na fila só pela transição automática do job).
 */
public record ClienteNaFilaDTO(
    Long clienteId,
    String nomeCliente,
    int posicao,
    LocalDateTime horaChegada,
    boolean confirmadoPeloCliente) {
}