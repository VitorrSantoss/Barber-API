package com.vitorsantos.barbearia_api.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de um agendamento. A ordem "natural" do fluxo é:
 *
 *   AGENDADO -> AGUARDANDO -> EM_ATENDIMENTO -> FINALIZADO
 *
 * Quem entra direto na fila (sem horário marcado) já nasce AGUARDANDO.
 * CANCELADO pode acontecer a partir de AGENDADO ou AGUARDANDO.
 * As transições permitidas são validadas em Agendamento#mudarStatus,
 * não aqui — o enum só declara os estados possíveis.
 */
public enum StatusAgendamento {

  /** Cliente marcou horário, mas ainda não chegou na barbearia. */
  AGENDADO,

  /** Cliente chegou e está fisicamente na fila esperando o barbeiro. */
  AGUARDANDO,

  /** Barbeiro está atendendo o cliente agora. */
  EM_ATENDIMENTO,

  /** Atendimento concluído. Estado final. */
  FINALIZADO,

  /** Agendamento cancelado, por qualquer motivo. Estado final. */
  CANCELADO;

  /** Estados que ocupam a fila do barbeiro (esperando ou sendo atendido). */
  public static final Set<StatusAgendamento> NA_FILA = EnumSet.of(AGUARDANDO, EM_ATENDIMENTO);

  /** Estados ainda em aberto, ou seja, compromissos que não terminaram. */
  public static final Set<StatusAgendamento> EM_ABERTO = EnumSet.of(AGENDADO, AGUARDANDO, EM_ATENDIMENTO);
}
