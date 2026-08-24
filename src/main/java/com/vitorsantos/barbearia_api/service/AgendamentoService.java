package com.vitorsantos.barbearia_api.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.vitorsantos.barbearia_api.dto.AgendamentoRequestDTO;
import com.vitorsantos.barbearia_api.dto.AgendamentoResponseDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.exception.ResourceNotFoundException;
import com.vitorsantos.barbearia_api.models.Agendamento;
import com.vitorsantos.barbearia_api.models.Barbeiro;
import com.vitorsantos.barbearia_api.models.Cliente;
import com.vitorsantos.barbearia_api.repository.AgendamentoRepository;
import com.vitorsantos.barbearia_api.repository.BarbeiroRepository;
import com.vitorsantos.barbearia_api.repository.ClienteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

  private final AgendamentoRepository agendamentoRepository;
  private final ClienteRepository clienteRepository;
  private final BarbeiroRepository barbeiroRepository;

  public List<AgendamentoResponseDTO> listarAgendamentos() {
    return agendamentoRepository.findAll().stream()
        .map(AgendamentoResponseDTO::fromEntity)
        .toList();
  }

  public AgendamentoResponseDTO cadastrarAgendamento(AgendamentoRequestDTO dto) {
    Cliente cliente = clienteRepository.findById(dto.clienteId())
        .orElseThrow(() -> new ResourceNotFoundException(
            "Cliente não encontrado com o ID: " + dto.clienteId(),
            ErrorCode.CLIENTE_NAO_ENCONTRADO));

    Barbeiro barbeiro = barbeiroRepository.findById(dto.barbeiroId())
        .orElseThrow(() -> new ResourceNotFoundException(
            "Barbeiro não encontrado com o ID: " + dto.barbeiroId(),
            ErrorCode.BARBEIRO_NAO_ENCONTRADO));

    Agendamento agendamento = new Agendamento();
    agendamento.setCliente(cliente);
    agendamento.setBarbeiro(barbeiro);
    agendamento.setDataHora(dto.dataHora());
    agendamento.setStatusAgendamento(StatusAgendamento.AGENDADO); // todo agendamento nasce assim

    Agendamento agendamentoSalvo = agendamentoRepository.save(agendamento);
    return AgendamentoResponseDTO.fromEntity(agendamentoSalvo);
  }

  /**
   * Aplica uma transição de status. A validação de "essa transição é
   * permitida?" mora na própria entidade (Agendamento#mudarStatus) —
   * aqui só busca o agendamento e delega.
   */
  public AgendamentoResponseDTO atualizarStatus(Long id, StatusAgendamento novoStatus) {
    Agendamento agendamento = agendamentoRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Agendamento não encontrado com o ID: " + id,
            ErrorCode.AGENDAMENTO_NAO_ENCONTRADO));

    agendamento.mudarStatus(novoStatus); // lança ValidacaoException se a transição for inválida

    Agendamento agendamentoAtualizado = agendamentoRepository.save(agendamento);
    return AgendamentoResponseDTO.fromEntity(agendamentoAtualizado);
  }

}