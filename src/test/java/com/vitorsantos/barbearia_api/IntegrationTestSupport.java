package com.vitorsantos.barbearia_api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Base dos testes de integração: contexto Spring completo, H2 em modo MySQL
 * com as migrations reais do Flyway, e banco limpo antes de cada teste.
 * Os testes NÃO são @Transactional de propósito: assim as transações,
 * locks e eventos AFTER_COMMIT funcionam como em produção.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

  private static final AtomicLong SEQUENCIA_TELEFONE = new AtomicLong(10_000_000);

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  protected JdbcTemplate jdbcTemplate;

  @BeforeEach
  void limparBanco() {
    jdbcTemplate.execute("DELETE FROM tb_agendamentos");
    jdbcTemplate.execute("DELETE FROM tb_clientes");
    jdbcTemplate.execute("DELETE FROM tb_barbeiros");
    jdbcTemplate.execute("DELETE FROM tb_servicos");
  }

  protected static String novoTelefone() {
    return "819" + SEQUENCIA_TELEFONE.incrementAndGet();
  }

  protected long criarCliente(String nome) throws Exception {
    String json = """
        { "nome": "%s", "numero": "%s" }
        """.formatted(nome, novoTelefone());
    return idDe(postJson("/clientes", json).andExpect(status().isCreated()));
  }

  protected long criarBarbeiro(String nome) throws Exception {
    return idDe(postJson("/barbeiros", "{ \"nome\": \"%s\" }".formatted(nome)).andExpect(status().isCreated()));
  }

  protected long criarServico(String nome) throws Exception {
    String json = """
        { "nome": "%s", "descricao": "Serviço de teste", "preco": 40.00, "duracaoMinutos": 30 }
        """.formatted(nome);
    return idDe(postJson("/servicos", json).andExpect(status().isCreated()));
  }

  protected ResultActions entrarNaFila(long barbeiroId, long clienteId, long servicoId) throws Exception {
    return postJson("/barbeiros/" + barbeiroId + "/fila",
        "{ \"clienteId\": %d, \"servicoId\": %d }".formatted(clienteId, servicoId));
  }

  protected long entrarNaFilaComSucesso(long barbeiroId, long clienteId, long servicoId) throws Exception {
    return idDe(entrarNaFila(barbeiroId, clienteId, servicoId).andExpect(status().isCreated()));
  }

  protected ResultActions mudarStatus(long agendamentoId, String novoStatus) throws Exception {
    return mockMvc.perform(patch("/agendamentos/" + agendamentoId + "/status")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{ \"novoStatus\": \"%s\" }".formatted(novoStatus)));
  }

  protected ResultActions postJson(String url, String json) throws Exception {
    return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  protected static long idDe(ResultActions resultado) throws Exception {
    Number id = JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
    return id.longValue();
  }
}
