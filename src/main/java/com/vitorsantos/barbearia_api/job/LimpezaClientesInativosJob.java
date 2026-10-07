package com.vitorsantos.barbearia_api.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.repository.ClienteRepository;
import com.vitorsantos.barbearia_api.service.ClienteService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Remove clientes sem nenhum acesso há mais de N dias (padrão 90) e sem
 * agendamento em aberto. Cliente sem histórico é apagado; cliente com
 * histórico de atendimentos é anonimizado (ver ClienteService), para não
 * quebrar o histórico dos barbeiros.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimpezaClientesInativosJob {

  private final ClienteRepository clienteRepository;
  private final ClienteService clienteService;

  @Value("${app.clientes.dias-inatividade}")
  private int diasInatividade;

  @Scheduled(cron = "${app.clientes.limpeza-cron}")
  public void removerClientesInativos() {
    LocalDateTime dataLimite = LocalDateTime.now().minusDays(diasInatividade);
    List<Long> candidatos = clienteRepository
        .findIdsInativosSemAgendamentoEmAberto(dataLimite, StatusAgendamento.EM_ABERTO);

    int removidos = 0;
    for (Long id : candidatos) {
      try {
        if (clienteService.removerSeInativo(id, dataLimite)) {
          removidos++;
        }
      } catch (RuntimeException ex) {
        log.error("Erro ao remover o cliente inativo {}", id, ex);
      }
    }

    log.info("Limpeza de inativos (> {} dias): {} cliente(s) removido(s)/anonimizado(s)", diasInatividade, removidos);
  }
}
