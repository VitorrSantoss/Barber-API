package com.vitorsantos.barbearia_api.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.exception.ValidacaoException;

/** Máquina de estados do agendamento, sem Spring. */
class AgendamentoTest {

  private final Cliente cliente = Cliente.novo("João", "81999990000");
  private final Barbeiro barbeiro = new Barbeiro();
  private final Servico servico = new Servico();

  @Test
  void entradaNaFilaNasceAguardandoComHoraDeChegada() {
    Agendamento agendamento = Agendamento.entrarNaFila(cliente, barbeiro, servico);

    assertThat(agendamento.getStatusAgendamento()).isEqualTo(StatusAgendamento.AGUARDANDO);
    assertThat(agendamento.getHoraChegada()).isNotNull();
  }

  @Test
  void agendamentoNasceAgendadoSemHoraDeChegada() {
    Agendamento agendamento = Agendamento.agendar(cliente, barbeiro, servico, LocalDateTime.now().plusDays(1));

    assertThat(agendamento.getStatusAgendamento()).isEqualTo(StatusAgendamento.AGENDADO);
    assertThat(agendamento.getHoraChegada()).isNull();
  }

  @Test
  void fluxoCompletoRegistraOsHorarios() {
    Agendamento agendamento = Agendamento.agendar(cliente, barbeiro, servico, LocalDateTime.now().plusDays(1));

    agendamento.mudarStatus(StatusAgendamento.AGUARDANDO);
    agendamento.mudarStatus(StatusAgendamento.EM_ATENDIMENTO);
    agendamento.mudarStatus(StatusAgendamento.FINALIZADO);

    assertThat(agendamento.getStatusAgendamento()).isEqualTo(StatusAgendamento.FINALIZADO);
    assertThat(agendamento.getHoraChegada()).isNotNull();
    assertThat(agendamento.getHoraInicioAtendimento()).isNotNull();
    assertThat(agendamento.getHoraFimAtendimento()).isNotNull();
  }

  @Test
  void cancelamentoRegistraHorario() {
    Agendamento agendamento = Agendamento.entrarNaFila(cliente, barbeiro, servico);

    agendamento.mudarStatus(StatusAgendamento.CANCELADO);

    assertThat(agendamento.getHoraCancelamento()).isNotNull();
  }

  @ParameterizedTest
  @CsvSource({
      "FINALIZADO, AGUARDANDO",
      "CANCELADO, AGUARDANDO",
      "EM_ATENDIMENTO, CANCELADO",
      "EM_ATENDIMENTO, AGUARDANDO",
      "AGUARDANDO, FINALIZADO",
      "AGUARDANDO, AGENDADO"
  })
  void bloqueiaTransicoesInvalidas(StatusAgendamento origem, StatusAgendamento destino) {
    Agendamento agendamento = agendamentoNoStatus(origem);

    assertThatThrownBy(() -> agendamento.mudarStatus(destino))
        .isInstanceOf(ValidacaoException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.TRANSICAO_STATUS_INVALIDA);
    assertThat(agendamento.getStatusAgendamento()).isEqualTo(origem);
  }

  @Test
  void naoConfirmaPresencaDeAgendamentoEncerrado() {
    Agendamento agendamento = agendamentoNoStatus(StatusAgendamento.CANCELADO);

    assertThatThrownBy(agendamento::confirmarPresenca)
        .isInstanceOf(ValidacaoException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.CONFIRMACAO_INVALIDA);
  }

  private Agendamento agendamentoNoStatus(StatusAgendamento status) {
    Agendamento agendamento = Agendamento.agendar(cliente, barbeiro, servico, LocalDateTime.now().plusDays(1));
    switch (status) {
      case AGENDADO -> { }
      case AGUARDANDO -> agendamento.mudarStatus(StatusAgendamento.AGUARDANDO);
      case EM_ATENDIMENTO -> {
        agendamento.mudarStatus(StatusAgendamento.AGUARDANDO);
        agendamento.mudarStatus(StatusAgendamento.EM_ATENDIMENTO);
      }
      case FINALIZADO -> {
        agendamento.mudarStatus(StatusAgendamento.AGUARDANDO);
        agendamento.mudarStatus(StatusAgendamento.EM_ATENDIMENTO);
        agendamento.mudarStatus(StatusAgendamento.FINALIZADO);
      }
      case CANCELADO -> agendamento.mudarStatus(StatusAgendamento.CANCELADO);
    }
    return agendamento;
  }
}
