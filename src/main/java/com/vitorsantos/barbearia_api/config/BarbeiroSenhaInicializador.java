package com.vitorsantos.barbearia_api.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.vitorsantos.barbearia_api.exception.BusinessException;
import com.vitorsantos.barbearia_api.models.Barbeiro;
import com.vitorsantos.barbearia_api.repository.BarbeiroRepository;
import com.vitorsantos.barbearia_api.service.BarbeiroAuthService;

import lombok.extern.slf4j.Slf4j;

/**
 * Define as senhas iniciais dos barbeiros a partir de
 * {@code app.barbeiros.senhas-iniciais} ("Nome:123456,Outro Nome:654321",
 * variável BARBEIROS_SENHAS_INICIAIS no .env). As senhas ficam fora do
 * código e do git; no banco só vai o hash.
 *
 * Só preenche barbeiros que ainda NÃO têm senha — reiniciar a API não
 * sobrescreve senhas já definidas.
 */
@Slf4j
@Component
public class BarbeiroSenhaInicializador implements ApplicationRunner {

  private final BarbeiroRepository barbeiroRepository;
  private final BarbeiroAuthService authService;
  private final String senhasIniciais;

  public BarbeiroSenhaInicializador(
      BarbeiroRepository barbeiroRepository,
      BarbeiroAuthService authService,
      @Value("${app.barbeiros.senhas-iniciais:}") String senhasIniciais) {
    this.barbeiroRepository = barbeiroRepository;
    this.authService = authService;
    this.senhasIniciais = senhasIniciais;
  }

  @Override
  public void run(ApplicationArguments args) {
    Arrays.stream(senhasIniciais.split(","))
        .map(String::strip)
        .filter(entrada -> !entrada.isEmpty())
        .forEach(this::aplicar);
  }

  private void aplicar(String entrada) {
    int separador = entrada.lastIndexOf(':');
    if (separador <= 0) {
      log.warn("Entrada inválida em app.barbeiros.senhas-iniciais (use Nome:senha)");
      return;
    }
    String nome = entrada.substring(0, separador).strip();
    String senha = entrada.substring(separador + 1).strip();

    if (!senha.matches("\\d{6}")) {
      log.warn("A senha inicial do barbeiro '{}' precisa ter 6 dígitos; ignorada", nome);
      return;
    }

    Barbeiro barbeiro = barbeiroRepository.findAllByOrderByNomeAsc().stream()
        .filter(b -> b.getNome().equalsIgnoreCase(nome))
        .findFirst()
        .orElse(null);
    if (barbeiro == null) {
      log.warn("Senha inicial ignorada: não existe barbeiro chamado '{}'", nome);
      return;
    }
    if (barbeiro.getSenhaHash() != null) {
      return;
    }

    try {
      authService.definirSenha(barbeiro, senha);
      log.info("Senha inicial definida para o barbeiro '{}'", nome);
    } catch (BusinessException ex) {
      log.warn("Senha inicial do barbeiro '{}' não definida: {}", nome, ex.getMessage());
    }
  }
}
