package com.vitorsantos.barbearia_api.config;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Conveniência de desenvolvimento: abre o Swagger no navegador quando a
 * aplicação sobe. Ligado por app.swagger.auto-open (true no profile dev).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.swagger.auto-open", havingValue = "true")
public class SwaggerAutoOpen {

  @EventListener(ApplicationReadyEvent.class)
  public void abrirSwagger(ApplicationReadyEvent event) {
    String porta = event.getApplicationContext().getEnvironment().getProperty("local.server.port", "8080");
    String url = "http://localhost:" + porta + "/swagger-ui/index.html";

    if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
      log.info("Swagger disponível em: {}", url);
      return;
    }

    try {
      new ProcessBuilder("cmd", "/c", "start", "", url).start();
      log.info("Abrindo Swagger em: {}", url);
    } catch (IOException e) {
      log.warn("Não foi possível abrir o navegador automaticamente. Acesse manualmente: {}", url, e);
    }
  }
}
