package com.vitorsantos.barbearia_api.listener;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.vitorsantos.barbearia_api.dto.FilaResponseDTO;
import com.vitorsantos.barbearia_api.event.FilaAtualizadaEvent;
import com.vitorsantos.barbearia_api.service.FilaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Transforma um FilaAtualizadaEvent em uma mensagem WebSocket real,
 * publicada em /topic/fila/{barbeiroId}.
 *
 * O @TransactionalEventListener(phase = AFTER_COMMIT) garante que essa
 * notificação só dispara DEPOIS que a mudança foi realmente salva no banco.
 * Sem isso, o listener poderia buscar a fila antes do commit e mandar a
 * fila ANTIGA para o front achando que é a nova.
 *
 * Pré-requisito: quem publica o evento (FilaService) precisa estar dentro
 * de uma transação; sem transação ativa o listener não é chamado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FilaEventListener {

  private final FilaService filaService;
  private final SimpMessagingTemplate messagingTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void aoAtualizarFila(FilaAtualizadaEvent evento) {
    String destino = "/topic/fila/" + evento.barbeiroId();
    try {
      FilaResponseDTO filaAtualizada = filaService.consultarFilaPorBarbeiro(evento.barbeiroId());
      messagingTemplate.convertAndSend(destino, filaAtualizada);
      log.debug("Fila do barbeiro {} publicada em {} ({} cliente(s) aguardando)",
          evento.barbeiroId(), destino, filaAtualizada.totalNaFila());
    } catch (RuntimeException ex) {
      // A alteração já foi gravada; uma falha na notificação não pode virar
      // erro para quem fez a requisição. O front ainda pode consultar a fila
      // via GET.
      log.error("Falha ao publicar a fila do barbeiro {} em {}", evento.barbeiroId(), destino, ex);
    }
  }
}
