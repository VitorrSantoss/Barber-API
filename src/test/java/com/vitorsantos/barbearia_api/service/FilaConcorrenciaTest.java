package com.vitorsantos.barbearia_api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.vitorsantos.barbearia_api.IntegrationTestSupport;
import com.vitorsantos.barbearia_api.dto.ClienteNaFilaDTO;
import com.vitorsantos.barbearia_api.dto.EntradaFilaRequestDTO;
import com.vitorsantos.barbearia_api.dto.FilaResponseDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.exception.BusinessException;

/**
 * Várias threads disparando operações na MESMA fila ao mesmo tempo (cada
 * uma com sua transação e conexão), como requisições HTTP simultâneas.
 */
class FilaConcorrenciaTest extends IntegrationTestSupport {

  private static final int THREADS = 12;

  @Autowired
  private FilaService filaService;

  @Test
  void entradasSimultaneasGeramPosicoesUnicasESequenciais() throws Exception {
    long barbeiro = criarBarbeiro("Barbeiro");
    long servico = criarServico("Corte");
    List<Long> clientes = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      clientes.add(criarCliente("Cliente " + i));
    }

    List<Resultado> resultados = executarAoMesmoTempo(THREADS,
        i -> () -> filaService.entrarNaFila(barbeiro, new EntradaFilaRequestDTO(clientes.get(i), servico)));

    assertThat(resultados).allMatch(Resultado::sucesso);

    FilaResponseDTO fila = filaService.consultarFilaPorBarbeiro(barbeiro);
    assertThat(fila.totalNaFila()).isEqualTo(THREADS);
    assertThat(fila.clientes()).extracting(ClienteNaFilaDTO::posicao)
        .containsExactlyElementsOf(IntStream.rangeClosed(1, THREADS).boxed().toList());
    assertThat(fila.clientes()).extracting(ClienteNaFilaDTO::clienteId)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(clientes);
  }

  @Test
  void mesmoClienteEntrandoVariasVezesAoMesmoTempoSoEntraUmaVez() throws Exception {
    long barbeiroA = criarBarbeiro("Barbeiro A");
    long barbeiroB = criarBarbeiro("Barbeiro B");
    long servico = criarServico("Corte");
    long cliente = criarCliente("Apressado");

    // metade tenta a fila do A, metade a do B
    List<Resultado> resultados = executarAoMesmoTempo(THREADS,
        i -> () -> filaService.entrarNaFila(i % 2 == 0 ? barbeiroA : barbeiroB,
            new EntradaFilaRequestDTO(cliente, servico)));

    assertThat(resultados).filteredOn(Resultado::sucesso).hasSize(1);
    assertThat(resultados).filteredOn(r -> !r.sucesso())
        .allMatch(r -> r.codigo() == ErrorCode.CLIENTE_JA_NA_FILA);

    int total = filaService.consultarFilaPorBarbeiro(barbeiroA).totalNaFila()
        + filaService.consultarFilaPorBarbeiro(barbeiroB).totalNaFila();
    assertThat(total).isEqualTo(1);
  }

  @Test
  void chamarProximoSimultaneamenteIniciaApenasUmAtendimento() throws Exception {
    long barbeiro = criarBarbeiro("Barbeiro");
    long servico = criarServico("Corte");
    for (int i = 0; i < 3; i++) {
      entrarNaFilaComSucesso(barbeiro, criarCliente("Cliente " + i), servico);
    }

    List<Resultado> resultados = executarAoMesmoTempo(THREADS, i -> () -> filaService.chamarProximo(barbeiro));

    assertThat(resultados).filteredOn(Resultado::sucesso).hasSize(1);
    assertThat(resultados).filteredOn(r -> !r.sucesso())
        .allMatch(r -> r.codigo() == ErrorCode.BARBEIRO_OCUPADO);

    FilaResponseDTO fila = filaService.consultarFilaPorBarbeiro(barbeiro);
    assertThat(fila.emAtendimento()).isNotNull();
    assertThat(fila.emAtendimento().nomeCliente()).isEqualTo("Cliente 0");
    assertThat(fila.clientes()).extracting(ClienteNaFilaDTO::posicao).containsExactly(1, 2);
  }

  @Test
  void filasDeBarbeirosDiferentesNaoSeBloqueiam() throws Exception {
    long servico = criarServico("Corte");
    List<Long> barbeiros = new ArrayList<>();
    List<Long> clientes = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      barbeiros.add(criarBarbeiro("Barbeiro " + i));
      clientes.add(criarCliente("Cliente " + i));
    }

    List<Resultado> resultados = executarAoMesmoTempo(THREADS,
        i -> () -> filaService.entrarNaFila(barbeiros.get(i), new EntradaFilaRequestDTO(clientes.get(i), servico)));

    assertThat(resultados).allMatch(Resultado::sucesso);
    for (Long barbeiro : barbeiros) {
      FilaResponseDTO fila = filaService.consultarFilaPorBarbeiro(barbeiro);
      assertThat(fila.clientes()).extracting(ClienteNaFilaDTO::posicao).containsExactly(1);
    }
  }

  // ───────────────────────────── utilitários ─────────────────────────────

  private record Resultado(boolean sucesso, ErrorCode codigo) {
  }

  @FunctionalInterface
  private interface Tarefa {
    Callable<?> para(int indice);
  }

  /** Libera todas as threads no mesmo instante e coleta o resultado de cada uma. */
  private List<Resultado> executarAoMesmoTempo(int quantidade, Tarefa tarefa) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(quantidade);
    CountDownLatch largada = new CountDownLatch(1);
    try {
      List<Future<Resultado>> futuros = new ArrayList<>();
      for (int i = 0; i < quantidade; i++) {
        Callable<?> acao = tarefa.para(i);
        futuros.add(executor.submit(() -> {
          largada.await();
          try {
            acao.call();
            return new Resultado(true, null);
          } catch (BusinessException ex) {
            return new Resultado(false, ex.getErrorCode());
          }
        }));
      }

      largada.countDown();

      List<Resultado> resultados = new ArrayList<>();
      for (Future<Resultado> futuro : futuros) {
        resultados.add(futuro.get(30, TimeUnit.SECONDS));
      }
      return resultados;
    } finally {
      executor.shutdownNow();
    }
  }
}
