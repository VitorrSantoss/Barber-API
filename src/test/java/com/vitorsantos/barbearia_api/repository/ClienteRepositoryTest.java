package com.vitorsantos.barbearia_api.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.models.Agendamento;
import com.vitorsantos.barbearia_api.models.Barbeiro;
import com.vitorsantos.barbearia_api.models.Cliente;

/**
 * Query de candidatos à remoção por inatividade
 * (findIdsInativosSemAgendamentoEmAberto).
 *
 * @DataJpaTest roda cada teste numa transação desfeita ao final.
 * Replace.NONE: usa o H2 em modo MySQL configurado para os testes (com as
 * migrations do Flyway), e não um banco embutido genérico.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ClienteRepositoryTest {

  private static final LocalDateTime DATA_LIMITE = LocalDateTime.now().minusDays(90);

  @Autowired
  private ClienteRepository clienteRepository;

  @Autowired
  private BarbeiroRepository barbeiroRepository;

  @Autowired
  private AgendamentoRepository agendamentoRepository;

  private Barbeiro barbeiro;

  @BeforeEach
  void criarBarbeiro() {
    barbeiro = new Barbeiro();
    barbeiro.setNome("Barbeiro Teste");
    barbeiroRepository.save(barbeiro);
  }

  @Test
  void listaClienteInativoSemAgendamento() {
    Cliente cliente = salvarCliente("81900000001", 91);

    assertThat(buscarCandidatos()).contains(cliente.getId());
  }

  @Test
  void naoListaClienteComAcessoRecente() {
    Cliente cliente = salvarCliente("81900000002", 89);

    assertThat(buscarCandidatos()).doesNotContain(cliente.getId());
  }

  @Test
  void naoListaClienteInativoComAgendamentoFuturo() {
    Cliente cliente = salvarCliente("81900000003", 120);
    agendamentoRepository.save(Agendamento.agendar(cliente, barbeiro, null, LocalDateTime.now().plusDays(5)));

    assertThat(buscarCandidatos()).doesNotContain(cliente.getId());
  }

  @Test
  void naoListaClienteInativoQueEstaNaFila() {
    // a data do agendamento já passou, mas ele segue AGUARDANDO: não pode sumir
    Cliente cliente = salvarCliente("81900000004", 120);
    agendamentoRepository.save(Agendamento.entrarNaFila(cliente, barbeiro, null));

    assertThat(buscarCandidatos()).doesNotContain(cliente.getId());
  }

  @Test
  void listaClienteInativoComAgendamentoFuturoCancelado() {
    // Agendamento CANCELADO não protege o cliente da remoção
    Cliente cliente = salvarCliente("81900000005", 120);
    Agendamento cancelado = Agendamento.agendar(cliente, barbeiro, null, LocalDateTime.now().plusDays(5));
    cancelado.mudarStatus(StatusAgendamento.CANCELADO);
    agendamentoRepository.save(cancelado);

    assertThat(buscarCandidatos()).contains(cliente.getId());
  }

  @Test
  void naoListaClienteJaAnonimizado() {
    Cliente cliente = salvarCliente("81900000006", 120);
    cliente.anonimizar();

    assertThat(buscarCandidatos()).doesNotContain(cliente.getId());
  }

  private List<Long> buscarCandidatos() {
    return clienteRepository.findIdsInativosSemAgendamentoEmAberto(DATA_LIMITE, StatusAgendamento.EM_ABERTO);
  }

  private Cliente salvarCliente(String numero, int diasSemAcesso) {
    Cliente cliente = Cliente.novo("Cliente " + numero, numero);
    cliente.setUltimoLogin(LocalDateTime.now().minusDays(diasSemAcesso));
    return clienteRepository.saveAndFlush(cliente);
  }
}
