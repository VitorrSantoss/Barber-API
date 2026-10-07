package com.vitorsantos.barbearia_api.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vitorsantos.barbearia_api.dto.BarbeiroResponseDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.exception.ConflitoDeRegraException;
import com.vitorsantos.barbearia_api.exception.LimiteTentativasException;
import com.vitorsantos.barbearia_api.exception.NaoAutorizadoException;
import com.vitorsantos.barbearia_api.models.Barbeiro;
import com.vitorsantos.barbearia_api.repository.BarbeiroRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Login do balcão do barbeiro por senha numérica. A tela só pede a senha,
 * então ela identifica o barbeiro: por isso cada senha é única entre os
 * barbeiros. No banco fica apenas o hash BCrypt.
 *
 * Contra força bruta (são só 1 milhão de combinações), depois de
 * {@code app.auth.max-tentativas} erros seguidos vindos do mesmo IP o login
 * fica bloqueado por {@code app.auth.bloqueio-segundos}. O controle é em
 * memória: vale para uma instância da API.
 */
@Slf4j
@Service
public class BarbeiroAuthService {

  private record Tentativas(int falhas, Instant bloqueadoAte) {
  }

  private final BarbeiroRepository barbeiroRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final int maxTentativas;
  private final Duration bloqueio;
  private final Map<String, Tentativas> tentativasPorOrigem = new ConcurrentHashMap<>();

  public BarbeiroAuthService(
      BarbeiroRepository barbeiroRepository,
      PasswordEncoder passwordEncoder,
      Clock clock,
      @Value("${app.auth.max-tentativas}") int maxTentativas,
      @Value("${app.auth.bloqueio-segundos}") long bloqueioSegundos) {
    this.barbeiroRepository = barbeiroRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.maxTentativas = maxTentativas;
    this.bloqueio = Duration.ofSeconds(bloqueioSegundos);
  }

  /**
   * @param origem identificador de quem tenta entrar (IP), para o limite de tentativas
   * @throws LimiteTentativasException se a origem está bloqueada (429)
   * @throws NaoAutorizadoException    se nenhuma senha de barbeiro ativo confere (401)
   */
  @Transactional(readOnly = true)
  public BarbeiroResponseDTO login(String senha, String origem) {
    verificarBloqueio(origem);

    // Confere contra todos de propósito (sem parar no primeiro) para o
    // tempo de resposta não revelar a posição do barbeiro.
    List<Barbeiro> ativos = barbeiroRepository.findByAtivoTrueOrderByNomeAsc();
    Barbeiro encontrado = null;
    for (Barbeiro barbeiro : ativos) {
      if (barbeiro.getSenhaHash() != null && passwordEncoder.matches(senha, barbeiro.getSenhaHash())) {
        encontrado = barbeiro;
      }
    }

    if (encontrado == null) {
      registrarFalha(origem);
      throw new NaoAutorizadoException("Senha incorreta.", ErrorCode.SENHA_INCORRETA);
    }

    tentativasPorOrigem.remove(origem);
    log.info("Barbeiro {} entrou no balcão", encontrado.getId());
    return BarbeiroResponseDTO.fromEntity(encontrado);
  }

  /**
   * Define a senha de um barbeiro. A senha precisa ser única, já que é ela
   * que identifica o barbeiro no login.
   */
  @Transactional
  public void definirSenha(Barbeiro barbeiro, String senha) {
    boolean emUsoPorOutro = barbeiroRepository.findAll().stream()
        .filter(outro -> !outro.getId().equals(barbeiro.getId()))
        .anyMatch(outro -> outro.getSenhaHash() != null && passwordEncoder.matches(senha, outro.getSenhaHash()));
    if (emUsoPorOutro) {
      throw new ConflitoDeRegraException(
          "Essa senha já pertence a outro barbeiro. Escolha outra.", ErrorCode.SENHA_INCORRETA);
    }
    barbeiro.setSenhaHash(passwordEncoder.encode(senha));
    barbeiroRepository.save(barbeiro);
  }

  private void verificarBloqueio(String origem) {
    Tentativas tentativas = tentativasPorOrigem.get(origem);
    Instant agora = clock.instant();
    if (tentativas != null && tentativas.bloqueadoAte() != null) {
      if (tentativas.bloqueadoAte().isAfter(agora)) {
        long restantes = Math.max(1, Duration.between(agora, tentativas.bloqueadoAte()).toSeconds() + 1);
        throw new LimiteTentativasException(restantes);
      }
      tentativasPorOrigem.remove(origem); // bloqueio expirou
    }
  }

  private void registrarFalha(String origem) {
    Instant bloqueioAte = clock.instant().plus(bloqueio);
    tentativasPorOrigem.merge(origem, new Tentativas(1, null), (atual, nova) -> {
      int falhas = atual.falhas() + 1;
      return falhas >= maxTentativas ? new Tentativas(0, bloqueioAte) : new Tentativas(falhas, null);
    });
    if (maxTentativas <= 1) {
      tentativasPorOrigem.put(origem, new Tentativas(0, bloqueioAte));
    }
  }
}
