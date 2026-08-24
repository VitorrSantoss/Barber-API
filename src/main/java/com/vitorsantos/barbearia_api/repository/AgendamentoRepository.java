package com.vitorsantos.barbearia_api.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.models.Agendamento;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

  long countByBarbeiroIdAndStatusAgendamento(Long barbeiroId, StatusAgendamento status);

  List<Agendamento> findByBarbeiroIdAndStatusAgendamentoOrderByHoraChegadaAsc(
      Long barbeiroId, StatusAgendamento status);

  /**
   * Usada pelo job automático (AgendamentoAutoStatusJob): busca todo
   * agendamento ainda AGENDADO cuja data/hora já chegou ou passou.
   */
  List<Agendamento> findByStatusAgendamentoAndDataHoraLessThanEqual(
      StatusAgendamento status, LocalDateTime dataHora);
}