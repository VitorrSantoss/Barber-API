package com.vitorsantos.barbearia_api.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.models.Agendamento;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

  @EntityGraph(attributePaths = { "cliente", "barbeiro", "servico" })
  List<Agendamento> findAllByOrderByDataHoraDesc();

  @EntityGraph(attributePaths = { "cliente", "barbeiro", "servico" })
  Optional<Agendamento> findComDetalhesById(Long id);

  /**
   * Fila de UM barbeiro (quem está nos status informados), já na ordem de
   * chegada. O desempate por id garante ordem estável mesmo com duas
   * chegadas no mesmo instante.
   */
  @Query("""
      SELECT a FROM Agendamento a
      JOIN FETCH a.cliente
      JOIN FETCH a.barbeiro
      LEFT JOIN FETCH a.servico
      WHERE a.barbeiro.id = :barbeiroId
        AND a.statusAgendamento IN :status
      ORDER BY a.horaChegada ASC, a.id ASC
      """)
  List<Agendamento> findFilaDoBarbeiro(
      @Param("barbeiroId") Long barbeiroId,
      @Param("status") Collection<StatusAgendamento> status);

  /** Mesma fila, para todos os barbeiros ativos de uma vez (uma query só). */
  @Query("""
      SELECT a FROM Agendamento a
      JOIN FETCH a.cliente
      JOIN FETCH a.barbeiro b
      LEFT JOIN FETCH a.servico
      WHERE b.ativo = true
        AND a.statusAgendamento IN :status
      ORDER BY a.horaChegada ASC, a.id ASC
      """)
  List<Agendamento> findFilaDosBarbeirosAtivos(@Param("status") Collection<StatusAgendamento> status);

  /** Quantos clientes AGUARDANDO chegaram antes deste agendamento. */
  @Query("""
      SELECT COUNT(a) FROM Agendamento a
      WHERE a.barbeiro.id = :barbeiroId
        AND a.statusAgendamento = :status
        AND (a.horaChegada < :horaChegada OR (a.horaChegada = :horaChegada AND a.id < :id))
      """)
  long countAFrenteNaFila(
      @Param("barbeiroId") Long barbeiroId,
      @Param("status") StatusAgendamento status,
      @Param("horaChegada") LocalDateTime horaChegada,
      @Param("id") Long id);

  long countByBarbeiroIdAndStatusAgendamento(Long barbeiroId, StatusAgendamento status);

  boolean existsByBarbeiroIdAndStatusAgendamento(Long barbeiroId, StatusAgendamento status);

  long countByBarbeiroIdAndStatusAgendamentoIn(Long barbeiroId, Collection<StatusAgendamento> status);

  boolean existsByClienteIdAndStatusAgendamentoIn(Long clienteId, Collection<StatusAgendamento> status);

  boolean existsByClienteIdAndStatusAgendamentoInAndIdNot(
      Long clienteId, Collection<StatusAgendamento> status, Long id);

  boolean existsByClienteId(Long clienteId);

  /**
   * Usada pelo job automático (AgendamentoAutoStatusJob): IDs de todo
   * agendamento ainda AGENDADO cuja data/hora já chegou ou passou.
   */
  @Query("""
      SELECT a.id FROM Agendamento a
      WHERE a.statusAgendamento = :status AND a.dataHora <= :dataHora
      ORDER BY a.dataHora ASC, a.id ASC
      """)
  List<Long> findIdsByStatusAndDataHoraAte(
      @Param("status") StatusAgendamento status,
      @Param("dataHora") LocalDateTime dataHora);
}
