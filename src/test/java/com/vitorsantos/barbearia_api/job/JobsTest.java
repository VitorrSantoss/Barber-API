package com.vitorsantos.barbearia_api.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.vitorsantos.barbearia_api.IntegrationTestSupport;
import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.models.Agendamento;
import com.vitorsantos.barbearia_api.repository.AgendamentoRepository;
import com.vitorsantos.barbearia_api.repository.BarbeiroRepository;
import com.vitorsantos.barbearia_api.repository.ClienteRepository;
import com.vitorsantos.barbearia_api.repository.ServicoRepository;

/** Jobs agendados, chamados diretamente (o agendamento automático fica desligado nos testes). */
class JobsTest extends IntegrationTestSupport {

  @Autowired
  private AgendamentoAutoStatusJob autoStatusJob;

  @Autowired
  private LimpezaClientesInativosJob limpezaJob;

  @Autowired
  private AgendamentoRepository agendamentoRepository;

  @Autowired
  private ClienteRepository clienteRepository;

  @Autowired
  private BarbeiroRepository barbeiroRepository;

  @Autowired
  private ServicoRepository servicoRepository;

  // ─────────────────────── AGENDADO -> AGUARDANDO ───────────────────────

  @Test
  void moveAgendamentosVencidosParaAFilaEIgnoraOsFuturos() throws Exception {
    long barbeiro = criarBarbeiro("Anderson");
    long servico = criarServico("Corte");
    long vencido = salvarAgendamento(criarCliente("Vencido"), barbeiro, servico, LocalDateTime.now().minusMinutes(5));
    long futuro = salvarAgendamento(criarCliente("Futuro"), barbeiro, servico, LocalDateTime.now().plusDays(1));

    autoStatusJob.moverAgendamentosParaFila();

    Agendamento movido = agendamentoRepository.findById(vencido).orElseThrow();
    assertThat(movido.getStatusAgendamento()).isEqualTo(StatusAgendamento.AGUARDANDO);
    assertThat(movido.getHoraChegada()).isNotNull();
    assertThat(agendamentoRepository.findById(futuro).orElseThrow().getStatusAgendamento())
        .isEqualTo(StatusAgendamento.AGENDADO);
  }

  @Test
  void umAgendamentoComConflitoNaoImpedeOsDemais() throws Exception {
    long barbeiro = criarBarbeiro("Anderson");
    long servico = criarServico("Corte");
    long jaNaFila = criarCliente("Já na fila");
    entrarNaFilaComSucesso(barbeiro, jaNaFila, servico);

    long conflitante = salvarAgendamento(jaNaFila, barbeiro, servico, LocalDateTime.now().minusMinutes(10));
    long normal = salvarAgendamento(criarCliente("Normal"), barbeiro, servico, LocalDateTime.now().minusMinutes(5));

    autoStatusJob.moverAgendamentosParaFila();

    assertThat(agendamentoRepository.findById(conflitante).orElseThrow().getStatusAgendamento())
        .isEqualTo(StatusAgendamento.AGENDADO);
    assertThat(agendamentoRepository.findById(normal).orElseThrow().getStatusAgendamento())
        .isEqualTo(StatusAgendamento.AGUARDANDO);
  }

  // ─────────────────────── Limpeza de inativos ──────────────────────────

  @Test
  void removeInativoSemHistoricoEAnonimizaInativoComHistorico() throws Exception {
    long barbeiro = criarBarbeiro("Anderson");
    long servico = criarServico("Corte");

    long semHistorico = criarCliente("Sem histórico");
    long comHistorico = criarCliente("Com histórico");
    long agFinalizado = entrarNaFilaComSucesso(barbeiro, comHistorico, servico);
    mudarStatus(agFinalizado, "EM_ATENDIMENTO");
    mudarStatus(agFinalizado, "FINALIZADO");

    long ativoRecente = criarCliente("Ativo recente");

    envelhecerUltimoAcesso(semHistorico, 91);
    envelhecerUltimoAcesso(comHistorico, 91);
    envelhecerUltimoAcesso(ativoRecente, 89);

    limpezaJob.removerClientesInativos();

    assertThat(clienteRepository.findById(semHistorico)).isEmpty();

    var anonimizado = clienteRepository.findById(comHistorico).orElseThrow();
    assertThat(anonimizado.isRemovido()).isTrue();
    assertThat(anonimizado.getNumero()).isNull();
    assertThat(anonimizado.getNome()).isEqualTo("Cliente removido");
    assertThat(agendamentoRepository.findById(agFinalizado)).isPresent();

    assertThat(clienteRepository.findById(ativoRecente).orElseThrow().isRemovido()).isFalse();
  }

  @Test
  void naoRemoveInativoComAgendamentoEmAbertoMesmoComDataNoPassado() throws Exception {
    long barbeiro = criarBarbeiro("Anderson");
    long servico = criarServico("Corte");
    long naFila = criarCliente("Na fila");
    long agendado = criarCliente("Agendado");
    entrarNaFilaComSucesso(barbeiro, naFila, servico);
    salvarAgendamento(agendado, barbeiro, servico, LocalDateTime.now().plusDays(3));
    envelhecerUltimoAcesso(naFila, 120);
    envelhecerUltimoAcesso(agendado, 120);

    limpezaJob.removerClientesInativos();

    assertThat(clienteRepository.findById(naFila).orElseThrow().isRemovido()).isFalse();
    assertThat(clienteRepository.findById(agendado).orElseThrow().isRemovido()).isFalse();
  }

  // ───────────────────────────── utilitários ─────────────────────────────

  private long salvarAgendamento(long clienteId, long barbeiroId, long servicoId, LocalDateTime dataHora) {
    Agendamento agendamento = Agendamento.agendar(
        clienteRepository.findById(clienteId).orElseThrow(),
        barbeiroRepository.findById(barbeiroId).orElseThrow(),
        servicoRepository.findById(servicoId).orElseThrow(),
        dataHora);
    return agendamentoRepository.save(agendamento).getId();
  }

  private void envelhecerUltimoAcesso(long clienteId, int dias) {
    jdbcTemplate.update("UPDATE tb_clientes SET ultimo_login = ? WHERE id = ?",
        LocalDateTime.now().minusDays(dias), clienteId);
  }
}
