package com.vitorsantos.barbearia_api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tb_barbeiros")
public class Barbeiro {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "nome", nullable = false, length = 100)
  private String nome;

  /**
   * Controla se o barbeiro está ativo/trabalhando. Um barbeiro inativo não
   * aparece no painel de filas nem recebe novos clientes/agendamentos.
   * Default true — todo barbeiro cadastrado começa ativo.
   */
  @Column(name = "ativo", nullable = false)
  private boolean ativo = true;

  /** Hash BCrypt da senha de acesso ao balcão; nunca é exposto pela API. */
  @Column(name = "senha_hash", length = 100)
  private String senhaHash;
}
