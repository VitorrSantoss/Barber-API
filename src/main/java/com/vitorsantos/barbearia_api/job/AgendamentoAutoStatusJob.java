package com.vitorsantos.barbearia_api.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.exception.BusinessException;
import com.vitorsantos.barbearia_api.repository.AgendamentoRepository;
import com.vitorsantos.barbearia_api.service.AgendamentoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Move automaticamente os agendamentos de AGENDADO para AGUARDANDO
 * quando a data/hora marcada chega — sem depender de nenhuma ação manual.
 *
 * IMPORTANTE: isso acontece independente de confirmadoPeloCliente. A
 * confirmação é só uma informação extra pro barbeiro (ver Agendamento).
 *
 * Cada agendamento é movido na sua própria transação, pelo mesmo caminho
 * do endpoint de status (mesmos locks, regras e evento de WebSocket). Se um
 * deles violar uma regra (ex: o cliente já está em outra fila), ele fica
 * AGENDADO e os demais seguem normalmente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgendamentoAutoStatusJob {

  private final AgendamentoRepository agendamentoRepository;
  private final AgendamentoService agendamentoService;

  @Scheduled(
      fixedRateString = "${app.agendamentos.auto-status-intervalo-ms}",
      initialDelayString = "${app.agendamentos.auto-status-intervalo-ms}")
  public void moverAgendamentosParaFila() {
    List<Long> idsNaHora = agendamentoRepository
        .findIdsByStatusAndDataHoraAte(StatusAgendamento.AGENDADO, LocalDateTime.now());

    int movidos = 0;
    for (Long id : idsNaHora) {
      try {
        agendamentoService.atualizarStatus(id, StatusAgendamento.AGUARDANDO);
        movidos++;
      } catch (BusinessException ex) {
        log.warn("Agendamento {} não pôde entrar na fila automaticamente: {}", id, ex.getMessage());
      } catch (RuntimeException ex) {
        log.error("Erro inesperado ao mover o agendamento {} para a fila", id, ex);
      }
    }

    if (movidos > 0) {
      log.info("{} agendamento(s) movido(s) automaticamente para AGUARDANDO", movidos);
    }
  }
}
