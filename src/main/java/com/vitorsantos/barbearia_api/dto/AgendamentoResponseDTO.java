package com.vitorsantos.barbearia_api.dto;

import java.time.LocalDateTime;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.models.Agendamento;
import com.vitorsantos.barbearia_api.models.Servico;

public record AgendamentoResponseDTO(
    Long id,
    Long clienteId,
    String nomeCliente,
    Long barbeiroId,
    String nomeBarbeiro,
    Long servicoId,
    String nomeServico,
    LocalDateTime dataHora,
    StatusAgendamento status,
    boolean confirmadoPeloCliente,
    LocalDateTime horaChegada,
    LocalDateTime horaInicioAtendimento,
    LocalDateTime horaFimAtendimento,
    LocalDateTime horaCancelamento) {

  public static AgendamentoResponseDTO fromEntity(Agendamento agendamento) {
    Servico servico = agendamento.getServico();
    return new AgendamentoResponseDTO(
        agendamento.getId(),
        agendamento.getCliente().getId(),
        agendamento.getCliente().getNome(),
        agendamento.getBarbeiro().getId(),
        agendamento.getBarbeiro().getNome(),
        servico == null ? null : servico.getId(),
        servico == null ? null : servico.getNome(),
        agendamento.getDataHora(),
        agendamento.getStatusAgendamento(),
        agendamento.isConfirmadoPeloCliente(),
        agendamento.getHoraChegada(),
        agendamento.getHoraInicioAtendimento(),
        agendamento.getHoraFimAtendimento(),
        agendamento.getHoraCancelamento());
  }
}
