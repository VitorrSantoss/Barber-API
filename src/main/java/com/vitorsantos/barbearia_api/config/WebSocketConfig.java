package com.vitorsantos.barbearia_api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Habilita WebSocket com o protocolo STOMP por cima.
 *
 * Endpoint de conexão: /ws (o front conecta aqui, com fallback SockJS
 * para navegadores/redes que bloqueiam WebSocket puro).
 *
 * Tópicos: tudo que começa com /topic é broadcast — qualquer cliente
 * inscrito recebe. A fila de cada barbeiro é publicada em
 * /topic/fila/{barbeiroId} (ver FilaEventListener).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final List<String> origensPermitidas;

  public WebSocketConfig(@Value("${app.cors.allowed-origins}") List<String> origensPermitidas) {
    this.origensPermitidas = origensPermitidas;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    // Broker simples em memória — suficiente para uma instância só da
    // aplicação. Com múltiplas instâncias, trocar por um broker externo
    // (RabbitMQ etc.), porque o simples não propaga mensagens entre elas.
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // As mesmas origens do CORS HTTP; a própria API (ex: /teste-websocket.html)
    // é sempre aceita por ser same-origin.
    registry.addEndpoint("/ws")
        .setAllowedOrigins(origensPermitidas.toArray(String[]::new))
        .withSockJS();
  }
}
