package com.vitorsantos.barbearia_api.models;

import java.time.LocalDateTime;

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
@Table(name = "tb_clientes")
public class Cliente {

  /** Nome exibido no histórico de atendimentos de um cliente anonimizado. */
  public static final String NOME_ANONIMIZADO = "Cliente removido";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "nome", nullable = false, length = 100)
  private String nome;

  /** Só fica nulo para clientes anonimizados (ver {@link #anonimizar()}). */
  @Column(name = "numero_telefone", unique = true, length = 20)
  private String numero;

  @Column(name = "data_cadastro", updatable = false)
  private LocalDateTime dataCadastro;

  /**
   * Último acesso/atividade do cliente (cadastro, entrada na fila,
   * agendamento, confirmação de presença). É a base da remoção por
   * inatividade.
   */
  @Column(name = "ultimo_login")
  private LocalDateTime ultimoLogin;

  @Column(name = "removido_em")
  private LocalDateTime removidoEm;

  public static Cliente novo(String nome, String numero) {
    Cliente cliente = new Cliente();
    LocalDateTime agora = LocalDateTime.now();
    cliente.nome = nome;
    cliente.numero = numero;
    cliente.dataCadastro = agora;
    cliente.ultimoLogin = agora;
    return cliente;
  }

  public void registrarAcesso() {
    this.ultimoLogin = LocalDateTime.now();
  }

  public boolean isRemovido() {
    return removidoEm != null;
  }

  /**
   * Remove os dados pessoais mas mantém o registro, para não quebrar o
   * histórico de atendimentos que aponta para ele. O telefone fica livre
   * para um novo cadastro.
   */
  public void anonimizar() {
    this.nome = NOME_ANONIMIZADO;
    this.numero = null;
    this.removidoEm = LocalDateTime.now();
  }
}
