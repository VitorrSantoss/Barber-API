package com.vitorsantos.barbearia_api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vitorsantos.barbearia_api.models.Servico;

public interface ServicoRepository extends JpaRepository<Servico, Long> {

  List<Servico> findAllByOrderByNomeAsc();

  List<Servico> findByAtivoTrueOrderByNomeAsc();

  boolean existsByNomeIgnoreCase(String nome);

  boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
