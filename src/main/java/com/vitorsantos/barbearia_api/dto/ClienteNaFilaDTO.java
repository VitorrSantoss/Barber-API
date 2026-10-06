package com.vitorsantos.barbearia_api.dto;

import java.time.LocalDateTime;

import com.vitorsantos.barbearia_api.models.Agendamento;
import com.vitorsantos.barbearia_api.models.Servico;

/**
 * Representa um cliente dentro da fila de um barbeiro específico.
 * confirmadoPeloCliente é o selo que diz pro barbeiro: esse cliente
 * confirmou presença antes do dia chegar (mais confiável que quem
 * entrou na fila só pela transição automática do job).
 *
 * @param posicao 1 = próximo a ser atendido; nulo para quem já está em
 *                atendimento
 */
public record ClienteNaFilaDTO(
    Long agendamentoId,
    Long clienteId,
    String nomeCliente,
    String nomeServico,
    Integer posicao,
    LocalDateTime horaChegada,
    LocalDateTime horaInicioAtendimento,
    boolean confirmadoPeloCliente) {

  public static ClienteNaFilaDTO fromEntity(Agendamento agendamento, Integer posicao) {
    Servico servico = agendamento.getServico();
    return new ClienteNaFilaDTO(
        agendamento.getId(),
        agendamento.getCliente().getId(),
        agendamento.getCliente().getNome(),
        servico == null ? null : servico.getNome(),
        posicao,
        agendamento.getHoraChegada(),
        agendamento.getHoraInicioAtendimento(),
        agendamento.isConfirmadoPeloCliente());
  }
}
