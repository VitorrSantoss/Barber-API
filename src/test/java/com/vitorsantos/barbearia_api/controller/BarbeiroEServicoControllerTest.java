package com.vitorsantos.barbearia_api.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.vitorsantos.barbearia_api.IntegrationTestSupport;

class BarbeiroEServicoControllerTest extends IntegrationTestSupport {

  // ───────────────────────────── Barbeiros ─────────────────────────────

  @Test
  void cadastraConsultaEAtualizaBarbeiro() throws Exception {
    long id = criarBarbeiro("Anderson");

    mockMvc.perform(get("/barbeiros/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nome").value("Anderson"))
        .andExpect(jsonPath("$.ativo").value(true));

    mockMvc.perform(put("/barbeiros/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{ \"nome\": \"Anderson Lima\" }"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nome").value("Anderson Lima"))
        .andExpect(jsonPath("$.ativo").value(true));
  }

  @Test
  void barbeiroSemNomeRetorna400() throws Exception {
    postJson("/barbeiros", "{ \"nome\": \"  \" }")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("ERRO_VALIDACAO"));
  }

  @Test
  void deleteDesativaBarbeiroESomeDaListaDeAtivos() throws Exception {
    long id = criarBarbeiro("Linda");
    criarBarbeiro("Mago");

    mockMvc.perform(delete("/barbeiros/" + id)).andExpect(status().isNoContent());

    mockMvc.perform(get("/barbeiros/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ativo").value(false));
    mockMvc.perform(get("/barbeiros").param("ativos", "true"))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[*].nome", not(hasItem("Linda"))));
    mockMvc.perform(get("/barbeiros"))
        .andExpect(jsonPath("$", hasSize(2)));
  }

  @Test
  void naoDesativaBarbeiroComClienteNaFila() throws Exception {
    long barbeiro = criarBarbeiro("Anderson");
    entrarNaFilaComSucesso(barbeiro, criarCliente("João"), criarServico("Corte"));

    mockMvc.perform(delete("/barbeiros/" + barbeiro))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("BARBEIRO_COM_FILA_ATIVA"));
  }

  @Test
  void barbeiroInexistenteRetorna404() throws Exception {
    mockMvc.perform(get("/barbeiros/424242"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("BARBEIRO_NAO_ENCONTRADO"));
    mockMvc.perform(delete("/barbeiros/424242"))
        .andExpect(status().isNotFound());
  }

  // ───────────────────────────── Serviços ──────────────────────────────

  @Test
  void cadastraEConsultaServico() throws Exception {
    postJson("/servicos", """
        { "nome": "Corte + Barba", "descricao": "Combo", "preco": 65.50, "duracaoMinutos": 50 }
        """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nome").value("Corte + Barba"))
        .andExpect(jsonPath("$.preco").value(65.50))
        .andExpect(jsonPath("$.duracaoMinutos").value(50))
        .andExpect(jsonPath("$.ativo").value(true));

    mockMvc.perform(get("/servicos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].nome", hasItem("Corte + Barba")));
  }

  @Test
  void servicoComDadosInvalidosRetorna400() throws Exception {
    postJson("/servicos", """
        { "nome": "Barba", "preco": -1, "duracaoMinutos": 0 }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros", hasSize(2)));
  }

  @Test
  void nomeDeServicoDuplicadoRetorna409() throws Exception {
    criarServico("Sobrancelha");

    postJson("/servicos", """
        { "nome": "sobrancelha", "preco": 15, "duracaoMinutos": 10 }
        """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("NOME_SERVICO_DUPLICADO"));
  }

  @Test
  void atualizaEDesativaServico() throws Exception {
    long id = criarServico("Barba");

    mockMvc.perform(put("/servicos/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            { "nome": "Barba completa", "preco": 35.00, "duracaoMinutos": 25 }
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nome").value("Barba completa"))
        .andExpect(jsonPath("$.descricao").doesNotExist());

    mockMvc.perform(delete("/servicos/" + id)).andExpect(status().isNoContent());

    mockMvc.perform(get("/servicos").param("ativos", "true"))
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void servicoInexistenteRetorna404() throws Exception {
    mockMvc.perform(get("/servicos/987654"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("SERVICO_NAO_ENCONTRADO"));
  }

  // ───────────────────────────── Erros HTTP genéricos ──────────────────

  @Test
  void rotaInexistenteRetorna404Padronizado() throws Exception {
    mockMvc.perform(get("/nao-existe"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
  }

  @Test
  void metodoNaoSuportadoRetorna405Padronizado() throws Exception {
    mockMvc.perform(put("/clientes"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.codigo").value("METODO_NAO_PERMITIDO"));
  }
}
