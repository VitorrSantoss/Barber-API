package com.vitorsantos.barbearia_api.event;

/**
 * Publicado sempre que uma mudança de status afeta a fila de um barbeiro
 * (alguém entrou ou saiu do status AGUARDANDO). Quem publica não sabe
 * (nem precisa saber) quem está "escutando" — hoje é o FilaEventListener
 * que transforma isso em uma mensagem WebSocket, mas amanhã poderia ser
 * qualquer outra coisa (log, métrica, notificação por SMS...) sem mudar
 * quem publica o evento.
 */
public record FilaAtualizadaEvent(Long barbeiroId) {
}