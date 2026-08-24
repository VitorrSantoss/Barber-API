package com.vitorsantos.barbearia_api.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.vitorsantos.barbearia_api.dto.ClienteNaFilaDTO;
import com.vitorsantos.barbearia_api.dto.FilaResponseDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.exception.ResourceNotFoundException;
import com.vitorsantos.barbearia_api.models.Agendamento;
import com.vitorsantos.barbearia_api.models.Barbeiro;
import com.vitorsantos.barbearia_api.repository.AgendamentoRepository;
import com.vitorsantos.barbearia_api.repository.BarbeiroRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FilaService {

  private final AgendamentoRepository agendamentoRepository;
  private final BarbeiroRepository barbeiroRepository;

  /**
   * Monta a fila de um barbeiro: todos os agendamentos com status
   * AGUARDANDO, ordenados por ordem de chegada, com a posição de cada
   * cliente já calculada (1 = próximo a ser atendido).
   */
  public FilaResponseDTO consultarFilaPorBarbeiro(Long barbeiroId) {
    if (!barbeiroRepository.existsById(barbeiroId)) {
      throw new ResourceNotFoundException(
          "Barbeiro não encontrado com o ID: " + barbeiroId,
          ErrorCode.BARBEIRO_NAO_ENCONTRADO);
    }

    return montarFilaResponse(barbeiroId);
  }

  /**
   * Painel geral: fila de TODOS os barbeiros de uma vez, para uma tela
   * tipo "quadro da barbearia" mostrando todo mundo ao mesmo tempo.
   */
  public List<FilaResponseDTO> consultarFilaDeTodosBarbeiros() {
    return barbeiroRepository.findAll().stream()
        .map(Barbeiro::getId)
        .map(this::montarFilaResponse)
        .toList();
  }

  private FilaResponseDTO montarFilaResponse(Long barbeiroId) {
    List<Agendamento> agendamentosNaFila = agendamentoRepository
        .findByBarbeiroIdAndStatusAgendamentoOrderByHoraChegadaAsc(barbeiroId, StatusAgendamento.AGUARDANDO);

    List<ClienteNaFilaDTO> clientes = montarClientesComPosicao(agendamentosNaFila);

    return new FilaResponseDTO(barbeiroId, clientes.size(), clientes);
  }

  private List<ClienteNaFilaDTO> montarClientesComPosicao(List<Agendamento> agendamentosNaFila) {
    return java.util.stream.IntStream.range(0, agendamentosNaFila.size())
        .mapToObj(indice -> {
          Agendamento agendamento = agendamentosNaFila.get(indice);
          return new ClienteNaFilaDTO(
              agendamento.getCliente().getId(),
              agendamento.getCliente().getNome(),
              indice + 1, // posição começa em 1, não em 0
              agendamento.getHoraChegada(),
              agendamento.isConfirmadoPeloCliente());
        })
        .toList();
  }
}