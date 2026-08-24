package com.vitorsantos.barbearia_api.models;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.exception.ValidacaoException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_agendamentos")
public class Agendamento {

  private static final Map<StatusAgendamento, Set<StatusAgendamento>> TRANSICOES_PERMITIDAS = Map.of(
      StatusAgendamento.AGENDADO, EnumSet.of(StatusAgendamento.AGUARDANDO, StatusAgendamento.CANCELADO),
      StatusAgendamento.AGUARDANDO, EnumSet.of(StatusAgendamento.EM_ATENDIMENTO, StatusAgendamento.CANCELADO),
      StatusAgendamento.EM_ATENDIMENTO, EnumSet.of(StatusAgendamento.FINALIZADO),
      StatusAgendamento.FINALIZADO, EnumSet.noneOf(StatusAgendamento.class),
      StatusAgendamento.CANCELADO, EnumSet.noneOf(StatusAgendamento.class));

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cliente_id", nullable = false)
  private Cliente cliente;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "barbeiro_id", nullable = false)
  private Barbeiro barbeiro;

  @Column(name = "data_hora", nullable = false)
  private LocalDateTime dataHora;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_agendamento", nullable = false, length = 20)
  private StatusAgendamento statusAgendamento;

  @Column(name = "hora_chegada")
  private LocalDateTime horaChegada;

  /**
   * Selo de confiança: o cliente confirmou, ANTES do dia chegar, que vai
   * comparecer. Não influencia a transição automática de status (que
   * acontece só pela data) — é uma informação extra pro barbeiro decidir
   * o quanto confiar que aquele cliente na fila vai aparecer de verdade.
   * Default false — todo agendamento nasce sem confirmação.
   */
  @Column(name = "confirmado_pelo_cliente", nullable = false)
  private boolean confirmadoPeloCliente = false;

  /**
   * Único jeito "correto" de mudar o status de um agendamento.
   *
   * @throws ValidacaoException se a transição não for permitida
   */
  public void mudarStatus(StatusAgendamento novoStatus) {
    Set<StatusAgendamento> permitidos = TRANSICOES_PERMITIDAS.get(this.statusAgendamento);

    if (permitidos == null || !permitidos.contains(novoStatus)) {
      throw new ValidacaoException(
          "Não é possível mudar o agendamento de %s para %s"
              .formatted(this.statusAgendamento, novoStatus),
          ErrorCode.TRANSICAO_STATUS_INVALIDA);
    }

    if (novoStatus == StatusAgendamento.AGUARDANDO) {
      this.horaChegada = LocalDateTime.now();
    }

    this.statusAgendamento = novoStatus;
  }

  /**
   * Confirmação de presença feita pelo cliente ANTES do dia do
   * agendamento chegar (ex: respondeu "sim" num lembrete por WhatsApp).
   * Só faz sentido enquanto o agendamento ainda está em andamento —
   * confirmar um agendamento já FINALIZADO ou CANCELADO não tem efeito
   * útil, então bloqueamos pra evitar dado inconsistente.
   *
   * @throws ValidacaoException se o agendamento já estiver FINALIZADO ou
   *                            CANCELADO
   */
  public void confirmarPresenca() {
    if (this.statusAgendamento == StatusAgendamento.FINALIZADO
        || this.statusAgendamento == StatusAgendamento.CANCELADO) {
      throw new ValidacaoException(
          "Não é possível confirmar presença de um agendamento %s".formatted(this.statusAgendamento),
          ErrorCode.CONFIRMACAO_INVALIDA);
    }

    this.confirmadoPeloCliente = true;
  }
}