package com.vitorsantos.barbearia_api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.vitorsantos.barbearia_api.IntegrationTestSupport;
import com.vitorsantos.barbearia_api.exception.ConflitoDeRegraException;
import com.vitorsantos.barbearia_api.models.Barbeiro;
import com.vitorsantos.barbearia_api.repository.BarbeiroRepository;
import com.vitorsantos.barbearia_api.service.BarbeiroAuthService;

class BarbeiroLoginTest extends IntegrationTestSupport {

  private static final AtomicInteger PROXIMO_IP = new AtomicInteger(1);

  @Autowired
  private BarbeiroAuthService authService;

  @Autowired
  private BarbeiroRepository barbeiroRepository;

  /** Cada teste "chega" de um IP próprio, para o bloqueio por tentativas não vazar entre eles. */
  private String ip;
  private long anderson;
  private long linda;

  @BeforeEach
  void prepararBarbeiros() throws Exception {
    ip = "10.9.0." + PROXIMO_IP.getAndIncrement();
    anderson = criarBarbeiro("Anderson");
    linda = criarBarbeiro("Linda");
    definirSenha(anderson, "123123");
    definirSenha(linda, "123321");
  }

  @Test
  void senhaCorretaIdentificaOBarbeiro() throws Exception {
    login("123123")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(anderson))
        .andExpect(jsonPath("$.nome").value("Anderson"));

    login("123321")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(linda))
        .andExpect(jsonPath("$.nome").value("Linda"));
  }

  @Test
  void respostaNuncaExpoeSenhaNemHash() throws Exception {
    login("123123")
        .andExpect(jsonPath("$.senha").doesNotExist())
        .andExpect(jsonPath("$.senhaHash").doesNotExist());

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/barbeiros"))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("$2"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("senha"))));
  }

  @Test
  void senhaNoBancoFicaSoComoHash() {
    String hash = jdbcTemplate.queryForObject(
        "SELECT senha_hash FROM tb_barbeiros WHERE id = ?", String.class, anderson);

    assertThat(hash).startsWith("$2").doesNotContain("123123");
  }

  @Test
  void senhaIncorretaRetorna401() throws Exception {
    login("999999")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("SENHA_INCORRETA"))
        .andExpect(jsonPath("$.mensagem").value("Senha incorreta."));
  }

  @Test
  void senhaForaDoFormatoRetorna400() throws Exception {
    login("12345").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ERRO_VALIDACAO"));
    login("abcdef").andExpect(status().isBadRequest());
    postJsonDe(ip, "{ }").andExpect(status().isBadRequest());
  }

  @Test
  void barbeiroSemSenhaOuInativoNaoEntra() throws Exception {
    long semSenha = criarBarbeiro("Sem Senha");
    assertThat(semSenha).isPositive();
    login("000000").andExpect(status().isUnauthorized());

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/barbeiros/" + linda))
        .andExpect(status().isNoContent());
    login("123321").andExpect(status().isUnauthorized());
    login("123123").andExpect(status().isOk());
  }

  @Test
  void bloqueiaDepoisDeVariasSenhasErradasMesmoComASenhaCerta() throws Exception {
    for (int i = 0; i < 3; i++) {
      login("000000").andExpect(status().isUnauthorized());
    }

    login("123123")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("MUITAS_TENTATIVAS"))
        .andExpect(jsonPath("$.status").value(429));

    // outro IP não é afetado
    postJsonDe("10.9.200.200", "{ \"senha\": \"123123\" }").andExpect(status().isOk());
  }

  @Test
  void acertoZeraOContadorDeErros() throws Exception {
    login("000000").andExpect(status().isUnauthorized());
    login("000000").andExpect(status().isUnauthorized());
    login("123123").andExpect(status().isOk());
    login("000000").andExpect(status().isUnauthorized());
    login("000000").andExpect(status().isUnauthorized());

    login("123123").andExpect(status().isOk());
  }

  @Test
  void duasSenhasIguaisNaoSaoPermitidas() {
    Barbeiro outro = barbeiroRepository.findById(linda).orElseThrow();

    assertThatThrownBy(() -> authService.definirSenha(outro, "123123"))
        .isInstanceOf(ConflitoDeRegraException.class);
  }

  // ───────────────────────────── utilitários ─────────────────────────────

  private void definirSenha(long barbeiroId, String senha) {
    authService.definirSenha(barbeiroRepository.findById(barbeiroId).orElseThrow(), senha);
  }

  private ResultActions login(String senha) throws Exception {
    return postJsonDe(ip, "{ \"senha\": \"%s\" }".formatted(senha));
  }

  private ResultActions postJsonDe(String origem, String json) throws Exception {
    return mockMvc.perform(post("/barbeiros/login")
        .with(request -> {
          request.setRemoteAddr(origem);
          return request;
        })
        .contentType(MediaType.APPLICATION_JSON)
        .content(json));
  }
}
