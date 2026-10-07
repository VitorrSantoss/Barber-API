package com.vitorsantos.barbearia_api.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.vitorsantos.barbearia_api.IntegrationTestSupport;

/** Fluxo da fila de ponta a ponta, pela API. */
class FilaControllerTest extends IntegrationTestSupport {

  private long barbeiroA;
  private long barbeiroB;
  private long corte;

  @BeforeEach
  void prepararBarbeariaVazia() throws Exception {
    barbeiroA = criarBarbeiro("Barbeiro A");
    barbeiroB = criarBarbeiro("Barbeiro B");
    corte = criarServico("Corte");
  }

  @Test
  void fluxoCompletoComFilasIndependentesPorBarbeiro() throws Exception {
    long joao = criarCliente("João");
    long pedro = criarCliente("Pedro");
    long carlos = criarCliente("Carlos");
    long ana = criarCliente("Ana");
    long lucas = criarCliente("Lucas");

    long agJoao = entrarNaFilaComSucesso(barbeiroA, joao, corte);
    long agPedro = entrarNaFilaComSucesso(barbeiroA, pedro, corte);
    long agCarlos = entrarNaFilaComSucesso(barbeiroA, carlos, corte);
    entrarNaFilaComSucesso(barbeiroB, ana, corte);
    entrarNaFilaComSucesso(barbeiroB, lucas, corte);

    // Barbeiro A: João 1, Pedro 2, Carlos 3
    mockMvc.perform(get("/barbeiros/" + barbeiroA + "/fila"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nomeBarbeiro").value("Barbeiro A"))
        .andExpect(jsonPath("$.totalNaFila").value(3))
        .andExpect(jsonPath("$.emAtendimento").doesNotExist())
        .andExpect(jsonPath("$.proximo.nomeCliente").value("João"))
        .andExpect(jsonPath("$.clientes[*].nomeCliente", contains("João", "Pedro", "Carlos")))
        .andExpect(jsonPath("$.clientes[*].posicao", contains(1, 2, 3)))
        .andExpect(jsonPath("$.clientes[0].nomeServico").value("Corte"));

    // Barbeiro B: Ana 1, Lucas 2 — não enxerga a fila do A
    mockMvc.perform(get("/barbeiros/" + barbeiroB + "/fila"))
        .andExpect(jsonPath("$.totalNaFila").value(2))
        .andExpect(jsonPath("$.clientes[*].nomeCliente", contains("Ana", "Lucas")))
        .andExpect(jsonPath("$.clientes[*].posicao", contains(1, 2)));

    mockMvc.perform(get("/agendamentos/" + agCarlos + "/posicao"))
        .andExpect(jsonPath("$.posicao").value(3))
        .andExpect(jsonPath("$.pessoasAFrente").value(2))
        .andExpect(jsonPath("$.totalNaFila").value(3));

    // Barbeiro A chama o próximo: João vai para atendimento
    mockMvc.perform(post("/barbeiros/" + barbeiroA + "/fila/proximo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(agJoao))
        .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
        .andExpect(jsonPath("$.horaInicioAtendimento").value(notNullValue()));

    mockMvc.perform(get("/barbeiros/" + barbeiroA + "/fila"))
        .andExpect(jsonPath("$.emAtendimento.nomeCliente").value("João"))
        .andExpect(jsonPath("$.emAtendimento.posicao").doesNotExist())
        .andExpect(jsonPath("$.totalNaFila").value(2))
        .andExpect(jsonPath("$.clientes[*].nomeCliente", contains("Pedro", "Carlos")))
        .andExpect(jsonPath("$.clientes[*].posicao", contains(1, 2)));

    mockMvc.perform(get("/agendamentos/" + agJoao + "/posicao"))
        .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
        .andExpect(jsonPath("$.posicao").doesNotExist());

    // Conclui o atendimento do João
    mudarStatus(agJoao, "FINALIZADO")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FINALIZADO"))
        .andExpect(jsonPath("$.horaFimAtendimento").value(notNullValue()));

    // Pedro cancela; Carlos sobe para a posição 1 (sem buracos)
    mudarStatus(agPedro, "CANCELADO")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.horaCancelamento").value(notNullValue()));

    mockMvc.perform(get("/barbeiros/" + barbeiroA + "/fila"))
        .andExpect(jsonPath("$.emAtendimento").doesNotExist())
        .andExpect(jsonPath("$.totalNaFila").value(1))
        .andExpect(jsonPath("$.clientes[*].nomeCliente", contains("Carlos")))
        .andExpect(jsonPath("$.clientes[*].posicao", contains(1)));

    mockMvc.perform(get("/agendamentos/" + agCarlos + "/posicao"))
        .andExpect(jsonPath("$.posicao").value(1))
        .andExpect(jsonPath("$.pessoasAFrente").value(0));

    // A fila do B continua intacta
    mockMvc.perform(get("/barbeiros/" + barbeiroB + "/fila"))
        .andExpect(jsonPath("$.clientes[*].nomeCliente", contains("Ana", "Lucas")));

    // Painel geral: os dois barbeiros ativos
    mockMvc.perform(get("/barbeiros/fila"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].nomeBarbeiro").value("Barbeiro A"))
        .andExpect(jsonPath("$[0].totalNaFila").value(1))
        .andExpect(jsonPath("$[1].totalNaFila").value(2));
  }

  @Test
  void novoClienteEntraSempreNoFimDaFila() throws Exception {
    long primeiro = criarCliente("Primeiro");
    long segundo = criarCliente("Segundo");
    entrarNaFilaComSucesso(barbeiroA, primeiro, corte);
    entrarNaFilaComSucesso(barbeiroA, segundo, corte);

    long agTerceiro = entrarNaFilaComSucesso(barbeiroA, criarCliente("Terceiro"), corte);

    mockMvc.perform(get("/agendamentos/" + agTerceiro + "/posicao"))
        .andExpect(jsonPath("$.posicao").value(3));
  }

  @Test
  void clienteNaoPodeEstarEmDuasFilasAoMesmoTempo() throws Exception {
    long joao = criarCliente("João");
    entrarNaFilaComSucesso(barbeiroA, joao, corte);

    entrarNaFila(barbeiroA, joao, corte)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CLIENTE_JA_NA_FILA"));
    entrarNaFila(barbeiroB, joao, corte)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CLIENTE_JA_NA_FILA"));
  }

  @Test
  void clientePodeVoltarParaFilaDepoisDeAtendido() throws Exception {
    long joao = criarCliente("João");
    long ag = entrarNaFilaComSucesso(barbeiroA, joao, corte);
    mockMvc.perform(post("/barbeiros/" + barbeiroA + "/fila/proximo")).andExpect(status().isOk());
    mudarStatus(ag, "FINALIZADO").andExpect(status().isOk());

    entrarNaFila(barbeiroB, joao, corte).andExpect(status().isCreated());
  }

  @Test
  void naoChamaProximoComBarbeiroOcupadoNemComFilaVazia() throws Exception {
    mockMvc.perform(post("/barbeiros/" + barbeiroA + "/fila/proximo"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("FILA_VAZIA"));

    entrarNaFilaComSucesso(barbeiroA, criarCliente("João"), corte);
    entrarNaFilaComSucesso(barbeiroA, criarCliente("Pedro"), corte);
    mockMvc.perform(post("/barbeiros/" + barbeiroA + "/fila/proximo")).andExpect(status().isOk());

    mockMvc.perform(post("/barbeiros/" + barbeiroA + "/fila/proximo"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("BARBEIRO_OCUPADO"));
  }

  @Test
  void naoIniciaAtendimentoForaDaOrdemDeChegada() throws Exception {
    entrarNaFilaComSucesso(barbeiroA, criarCliente("João"), corte);
    long agPedro = entrarNaFilaComSucesso(barbeiroA, criarCliente("Pedro"), corte);

    mudarStatus(agPedro, "EM_ATENDIMENTO")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("FORA_DA_ORDEM_DA_FILA"));
  }

  @Test
  void transicaoInvalidaRetorna400() throws Exception {
    long ag = entrarNaFilaComSucesso(barbeiroA, criarCliente("João"), corte);
    mudarStatus(ag, "CANCELADO").andExpect(status().isOk());

    mudarStatus(ag, "AGUARDANDO")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("TRANSICAO_STATUS_INVALIDA"));
  }

  @Test
  void statusInexistenteNoCorpoRetorna400() throws Exception {
    long ag = entrarNaFilaComSucesso(barbeiroA, criarCliente("João"), corte);

    mudarStatus(ag, "VOANDO")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
  }

  @Test
  void barbeiroOuServicoInativoNaoRecebeClientes() throws Exception {
    long joao = criarCliente("João");

    mockMvc.perform(delete("/barbeiros/" + barbeiroA)).andExpect(status().isNoContent());
    entrarNaFila(barbeiroA, joao, corte)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("BARBEIRO_INATIVO"));

    mockMvc.perform(delete("/servicos/" + corte)).andExpect(status().isNoContent());
    entrarNaFila(barbeiroB, joao, corte)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("SERVICO_INATIVO"));
  }

  @Test
  void recursosInexistentesNaFilaRetornam404() throws Exception {
    long joao = criarCliente("João");

    entrarNaFila(999_999, joao, corte)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("BARBEIRO_NAO_ENCONTRADO"));
    entrarNaFila(barbeiroA, 999_999, corte)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("CLIENTE_NAO_ENCONTRADO"));
    entrarNaFila(barbeiroA, joao, 999_999)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("SERVICO_NAO_ENCONTRADO"));
    mockMvc.perform(get("/barbeiros/999999/fila"))
        .andExpect(status().isNotFound());
    mudarStatus(999_999, "CANCELADO")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("AGENDAMENTO_NAO_ENCONTRADO"));
  }

  @Test
  void entradaNaFilaSemServicoRetorna400() throws Exception {
    postJson("/barbeiros/" + barbeiroA + "/fila", "{ \"clienteId\": 1 }")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.erros[0]").value("servicoId: O servicoId é obrigatório"));
  }

  @Test
  void agendamentoFuturoNasceAgendadoEPodeSerConfirmado() throws Exception {
    long joao = criarCliente("João");
    String dataHora = LocalDateTime.now().plusDays(2).withNano(0).toString();

    long ag = idDe(postJson("/agendamentos", """
        { "clienteId": %d, "barbeiroId": %d, "servicoId": %d, "dataHora": "%s" }
        """.formatted(joao, barbeiroA, corte, dataHora))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("AGENDADO"))
        .andExpect(jsonPath("$.nomeServico").value("Corte")));

    // agendado ainda não ocupa a fila
    mockMvc.perform(get("/barbeiros/" + barbeiroA + "/fila"))
        .andExpect(jsonPath("$.totalNaFila").value(0));

    mockMvc.perform(patch("/agendamentos/" + ag + "/confirmar-presenca"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmadoPeloCliente").value(true));

    // check-in manual: entra no fim da fila com o selo de confirmado
    mudarStatus(ag, "AGUARDANDO").andExpect(status().isOk());
    mockMvc.perform(get("/barbeiros/" + barbeiroA + "/fila"))
        .andExpect(jsonPath("$.clientes[0].confirmadoPeloCliente").value(true));
  }

  @Test
  void agendamentoNoPassadoRetorna400() throws Exception {
    long joao = criarCliente("João");
    postJson("/agendamentos", """
        { "clienteId": %d, "barbeiroId": %d, "servicoId": %d, "dataHora": "2020-01-01T10:00:00" }
        """.formatted(joao, barbeiroA, corte))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("ERRO_VALIDACAO"));
  }

  @Test
  void listagemDeAgendamentosTrazOsDados() throws Exception {
    entrarNaFilaComSucesso(barbeiroA, criarCliente("João"), corte);

    mockMvc.perform(get("/agendamentos").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].nomeCliente").value("João"))
        .andExpect(jsonPath("$[0].nomeBarbeiro").value("Barbeiro A"));
  }
}
