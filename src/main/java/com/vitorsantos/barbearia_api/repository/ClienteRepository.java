package com.vitorsantos.barbearia_api.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.models.Cliente;

import jakarta.persistence.LockModeType;

/**
 * Clientes anonimizados (removidoEm preenchido) só existem para manter o
 * histórico de atendimentos — as buscas da API os ignoram.
 */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

  List<Cliente> findByRemovidoEmIsNullOrderByNomeAsc();

  Optional<Cliente> findByIdAndRemovidoEmIsNull(Long id);

  Optional<Cliente> findByNumeroAndRemovidoEmIsNull(String numero);

  boolean existsByNumero(String numero);

  /**
   * SELECT ... FOR UPDATE no cliente: impede que o mesmo cliente entre em
   * duas filas ao mesmo tempo, ou entre na fila enquanto está sendo removido.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Cliente c WHERE c.id = :id AND c.removidoEm IS NULL")
  Optional<Cliente> findByIdForUpdate(@Param("id") Long id);

  /**
   * IDs de clientes candidatos à remoção por inatividade: último acesso
   * anterior à data limite E nenhum agendamento ainda em aberto (AGENDADO,
   * AGUARDANDO ou EM_ATENDIMENTO), não importa a data dele.
   */
  @Query("""
      SELECT c.id FROM Cliente c
      WHERE c.removidoEm IS NULL
        AND c.ultimoLogin < :dataLimite
        AND NOT EXISTS (
            SELECT a.id FROM Agendamento a
            WHERE a.cliente = c
              AND a.statusAgendamento IN :statusEmAberto
        )
      """)
  List<Long> findIdsInativosSemAgendamentoEmAberto(
      @Param("dataLimite") LocalDateTime dataLimite,
      @Param("statusEmAberto") Collection<StatusAgendamento> statusEmAberto);
}
