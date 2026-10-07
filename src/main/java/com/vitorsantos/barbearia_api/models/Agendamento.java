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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Representa tanto um horário marcado (nasce AGENDADO) quanto a entrada
 * direta na fila de um barbeiro (nasce AGUARDANDO).
 *
 * A posição na fila NÃO é gravada: ela é derivada, a cada consulta, da
 * ordem de chegada (horaChegada, desempate por id) entre os agendamentos
 * AGUARDANDO do mesmo barbeiro. Assim a fila é sempre 1, 2, 3... sem
 * buracos nem posições duplicadas, e não precisa ser "renumerada" quando
 * alguém sai dela.
 *
 * O status só muda via {@link #mudarStatus(StatusAgendamento)}, que valida
 * a transição — por isso não existe setter público para ele.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

  /** Nulo apenas em registros anteriores ao cadastro de serviços. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "servico_id")
  private Servico servico;

  @Column(name = "data_hora", nullable = false)
  private LocalDateTime dataHora;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_agendamento", nullable = false, length = 20)
  private StatusAgendamento statusAgendamento;

  @Column(name = "hora_chegada")
  private LocalDateTime horaChegada;

  @Column(name = "hora_inicio_atendimento")
  private LocalDateTime horaInicioAtendimento;

  @Column(name = "hora_fim_atendimento")
  private LocalDateTime horaFimAtendimento;

  @Column(name = "hora_cancelamento")
  private LocalDateTime horaCancelamento;

  /**
   * Selo de confiança: o cliente confirmou, ANTES do dia chegar, que vai
   * comparecer. Não influencia a transição automática de status (que
   * acontece só pela data) — é uma informação extra pro barbeiro decidir
   * o quanto confiar que aquele cliente na fila vai aparecer de verdade.
   */
  @Column(name = "confirmado_pelo_cliente", nullable = false)
  private boolean confirmadoPeloCliente = false;

  /**
   * Controle otimista de concorrência: se duas requisições alteram o mesmo
   * agendamento ao mesmo tempo, a segunda falha em vez de sobrescrever a
   * primeira em silêncio.
   */
  @Version
  @Column(name = "versao", nullable = false)
  private long versao;

  /** Horário marcado para o futuro. */
  public static Agendamento agendar(Cliente cliente, Barbeiro barbeiro, Servico servico, LocalDateTime dataHora) {
    Agendamento agendamento = new Agendamento(cliente, barbeiro, servico);
    agendamento.dataHora = dataHora;
    agendamento.statusAgendamento = StatusAgendamento.AGENDADO;
    return agendamento;
  }

  /** Cliente chegou sem horário marcado e entra direto no fim da fila. */
  public static Agendamento entrarNaFila(Cliente cliente, Barbeiro barbeiro, Servico servico) {
    Agendamento agendamento = new Agendamento(cliente, barbeiro, servico);
    LocalDateTime agora = LocalDateTime.now();
    agendamento.dataHora = agora;
    agendamento.horaChegada = agora;
    agendamento.statusAgendamento = StatusAgendamento.AGUARDANDO;
    return agendamento;
  }

  private Agendamento(Cliente cliente, Barbeiro barbeiro, Servico servico) {
    this.cliente = cliente;
    this.barbeiro = barbeiro;
    this.servico = servico;
  }

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

    LocalDateTime agora = LocalDateTime.now();
    switch (novoStatus) {
      case AGUARDANDO -> this.horaChegada = agora;
      case EM_ATENDIMENTO -> this.horaInicioAtendimento = agora;
      case FINALIZADO -> this.horaFimAtendimento = agora;
      case CANCELADO -> this.horaCancelamento = agora;
      case AGENDADO -> {
        // nunca é destino de transição (bloqueado pela tabela acima)
      }
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
    if (!StatusAgendamento.EM_ABERTO.contains(this.statusAgendamento)) {
      throw new ValidacaoException(
          "Não é possível confirmar presença de um agendamento %s".formatted(this.statusAgendamento),
          ErrorCode.CONFIRMACAO_INVALIDA);
    }

    this.confirmadoPeloCliente = true;
  }
}
