package com.vitorsantos.barbearia_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vitorsantos.barbearia_api.models.Barbeiro;

import jakarta.persistence.LockModeType;

public interface BarbeiroRepository extends JpaRepository<Barbeiro, Long> {

  List<Barbeiro> findAllByOrderByNomeAsc();

  List<Barbeiro> findByAtivoTrueOrderByNomeAsc();

  /**
   * SELECT ... FOR UPDATE na linha do barbeiro. Toda operação que mexe na
   * fila de um barbeiro passa por aqui primeiro, então operações na MESMA
   * fila acontecem uma de cada vez, enquanto filas de barbeiros diferentes
   * continuam independentes.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Barbeiro b WHERE b.id = :id")
  Optional<Barbeiro> findByIdForUpdate(@Param("id") Long id);
}
