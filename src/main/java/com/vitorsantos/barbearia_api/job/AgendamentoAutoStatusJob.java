package com.vitorsantos.barbearia_api.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.models.Agendamento;
import com.vitorsantos.barbearia_api.repository.AgendamentoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Move automaticamente os agendamentos de AGENDADO para AGUARDANDO
 * quando a data/hora marcada chega — sem depender de nenhuma ação manual.
 *
 * IMPORTANTE: isso acontece independente de confirmadoPeloCliente. A
 * confirmação é só uma informação extra pro barbeiro (ver Agendamento);
 * ela não bloqueia nem acelera a entrada na fila. Se quiser mudar esse
 * comportamento no futuro (ex: só entrar na fila se confirmado), é aqui
 * que se ajusta a query.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgendamentoAutoStatusJob {

  private final AgendamentoRepository agendamentoRepository;

  /**
   * Roda a cada 1 minuto (60_000 ms). Esse intervalo é um chute razoável
   * pro estágio atual do projeto — se um dia isso rodar com muito volume
   * de agendamento, vale revisar a frequência.
   */
  @Scheduled(fixedRate = 60_000)
  public void moverAgendamentosParaFila() {
    List<Agendamento> agendamentosNaHora = agendamentoRepository
        .findByStatusAgendamentoAndDataHoraLessThanEqual(StatusAgendamento.AGENDADO, LocalDateTime.now());

    if (agendamentosNaHora.isEmpty()) {
      return;
    }

    agendamentosNaHora.forEach(agendamento -> agendamento.mudarStatus(StatusAgendamento.AGUARDANDO));

    agendamentoRepository.saveAll(agendamentosNaHora);

    log.info("{} agendamento(s) movido(s) automaticamente para AGUARDANDO", agendamentosNaHora.size());
  }

}