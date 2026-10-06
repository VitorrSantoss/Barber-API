package com.vitorsantos.barbearia_api.dto;

import java.util.List;

/**
 * @param emAtendimento cliente sendo atendido agora (nulo se ninguém)
 * @param proximo       primeiro da fila (nulo se a fila está vazia)
 * @param totalNaFila   quantidade de clientes AGUARDANDO
 * @param clientes      clientes AGUARDANDO, em ordem de posição
 */
public record FilaResponseDTO(
    Long barbeiroId,
    String nomeBarbeiro,
    int totalNaFila,
    ClienteNaFilaDTO emAtendimento,
    ClienteNaFilaDTO proximo,
    List<ClienteNaFilaDTO> clientes) {
}
