package com.vitorsantos.barbearia_api.dto;

import java.time.LocalDateTime;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.models.Agendamento;

public record AgendamentoResponseDTO(
    Long id,
    Long clienteId,
    String nomeCliente,
    Long barbeiroId,
    String nomeBarbeiro,
    LocalDateTime dataHora,
    StatusAgendamento status,
    LocalDateTime horaChegada) {

  public static AgendamentoResponseDTO fromEntity(Agendamento agendamento) {
    return new AgendamentoResponseDTO(
        agendamento.getId(),
        agendamento.getCliente().getId(),
        agendamento.getCliente().getNome(),
        agendamento.getBarbeiro().getId(),
        agendamento.getBarbeiro().getNome(),
        agendamento.getDataHora(),
        agendamento.getStatusAgendamento(),
        agendamento.getHoraChegada());
  }
}