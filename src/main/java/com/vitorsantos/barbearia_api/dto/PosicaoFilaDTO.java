package com.vitorsantos.barbearia_api.dto;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;

/**
 * Situação de um agendamento na fila do barbeiro.
 *
 * @param posicao        posição na fila (1 = próximo); nulo se o
 *                       agendamento não está AGUARDANDO
 * @param pessoasAFrente quantos clientes serão atendidos antes; nulo se não
 *                       está AGUARDANDO
 * @param totalNaFila    total de clientes AGUARDANDO com esse barbeiro
 */
public record PosicaoFilaDTO(
    Long agendamentoId,
    Long barbeiroId,
    String nomeBarbeiro,
    StatusAgendamento status,
    Integer posicao,
    Integer pessoasAFrente,
    int totalNaFila) {
}
