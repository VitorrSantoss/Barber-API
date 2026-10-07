package com.vitorsantos.barbearia_api.models;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Serviço oferecido pela barbearia (corte, barba, combo...). Serviço
 * inativo não pode ser escolhido em novos atendimentos, mas continua
 * existindo para o histórico.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tb_servicos")
public class Servico {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "nome", nullable = false, unique = true, length = 100)
  private String nome;

  @Column(name = "descricao")
  private String descricao;

  @Column(name = "preco", nullable = false, precision = 10, scale = 2)
  private BigDecimal preco;

  @Column(name = "duracao_minutos", nullable = false)
  private int duracaoMinutos;

  @Column(name = "ativo", nullable = false)
  private boolean ativo = true;
}
