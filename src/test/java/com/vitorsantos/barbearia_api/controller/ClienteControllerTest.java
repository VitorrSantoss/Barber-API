package com.vitorsantos.barbearia_api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.vitorsantos.barbearia_api.IntegrationTestSupport;

class ClienteControllerTest extends IntegrationTestSupport {

  @Test
  void cadastraClienteNormalizandoTelefone() throws Exception {
    postJson("/clientes", """
        { "nome": "  João Silva ", "numero": "(81) 99876-5432" }
        """)
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", containsString("/clientes/")))
        .andExpect(jsonPath("$.id").value(notNullValue()))
        .andExpect(jsonPath("$.nome").value("João Silva"))
        .andExpect(jsonPath("$.numero").value("81998765432"))
        .andExpect(jsonPath("$.dataCadastro").value(notNullValue()))
        .andExpect(jsonPath("$.ultimoLogin").value(notNullValue()));
  }

  @Test
  void rejeitaDadosInvalidosCom400() throws Exception {
    postJson("/clientes", """
        { "nome": "", "numero": "123" }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.codigo").value("ERRO_VALIDACAO"))
        .andExpect(jsonPath("$.path").value("/clientes"))
        .andExpect(jsonPath("$.erros", hasSize(2)))
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void rejeitaTelefoneDuplicadoMesmoComMascaraDiferenteCom409() throws Exception {
    postJson("/clientes", "{ \"nome\": \"Ana\", \"numero\": \"81911112222\" }").andExpect(status().isCreated());

    postJson("/clientes", "{ \"nome\": \"Outra Ana\", \"numero\": \"(81) 91111-2222\" }")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("TELEFONE_DUPLICADO"));
  }

  @Test
  void rejeitaJsonMalformadoCom400() throws Exception {
    postJson("/clientes", "{ \"nome\": ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
  }

  @Test
  void buscaClientePorIdETelefone() throws Exception {
    long id = criarCliente("Pedro");
    String numero = jdbcTemplate.queryForObject(
        "SELECT numero_telefone FROM tb_clientes WHERE id = ?", String.class, id);

    mockMvc.perform(get("/clientes/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nome").value("Pedro"));

    mockMvc.perform(get("/clientes/telefone/" + numero))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));

    mockMvc.perform(get("/clientes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].nome", hasItem("Pedro")));
  }

  @Test
  void clienteInexistenteRetorna404Padronizado() throws Exception {
    mockMvc.perform(get("/clientes/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.erro").value("Not Found"))
        .andExpect(jsonPath("$.codigo").value("CLIENTE_NAO_ENCONTRADO"))
        .andExpect(jsonPath("$.mensagem").value(containsString("999999")))
        .andExpect(jsonPath("$.path").value("/clientes/999999"))
        .andExpect(jsonPath("$.timestamp").value(notNullValue()));
  }

  @Test
  void idNaoNumericoRetorna400() throws Exception {
    mockMvc.perform(get("/clientes/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
  }

  @Test
  void removeClienteSemHistoricoDeVez() throws Exception {
    long id = criarCliente("Carlos");

    mockMvc.perform(delete("/clientes/" + id)).andExpect(status().isNoContent());

    mockMvc.perform(get("/clientes/" + id)).andExpect(status().isNotFound());
    Integer linhas = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_clientes WHERE id = ?", Integer.class, id);
    assertThat(linhas).isZero();
  }

  @Test
  void removeClienteComHistoricoAnonimizandoELiberandoTelefone() throws Exception {
    long barbeiro = criarBarbeiro("Anderson");
    long servico = criarServico("Corte");
    String numero = novoTelefone();
    long cliente = idDe(postJson("/clientes", "{ \"nome\": \"Lucas\", \"numero\": \"%s\" }".formatted(numero)));
    long agendamento = entrarNaFilaComSucesso(barbeiro, cliente, servico);

    // com atendimento em aberto não pode remover
    mockMvc.perform(delete("/clientes/" + cliente))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CLIENTE_COM_ATENDIMENTO_ATIVO"));

    mudarStatus(agendamento, "CANCELADO").andExpect(status().isOk());
    mockMvc.perform(delete("/clientes/" + cliente)).andExpect(status().isNoContent());

    // some da API, mas o histórico continua apontando para o registro anonimizado
    mockMvc.perform(get("/clientes/" + cliente)).andExpect(status().isNotFound());
    mockMvc.perform(get("/agendamentos/" + agendamento))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nomeCliente").value("Cliente removido"));
    assertThat(jdbcTemplate.queryForObject(
        "SELECT numero_telefone FROM tb_clientes WHERE id = ?", String.class, cliente)).isNull();

    // telefone liberado para novo cadastro
    postJson("/clientes", "{ \"nome\": \"Lucas de novo\", \"numero\": \"%s\" }".formatted(numero))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ultimoLogin").value(notNullValue()));
  }
}
