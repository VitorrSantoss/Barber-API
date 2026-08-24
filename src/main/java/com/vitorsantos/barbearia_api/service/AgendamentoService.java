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
    agendamento.setStatusAgendamento(StatusAgendamento.AGENDADO);
    agendamento.setConfirmadoPeloCliente(false);

    Agendamento agendamentoSalvo = agendamentoRepository.save(agendamento);
    return AgendamentoResponseDTO.fromEntity(agendamentoSalvo);
  }

  /**
   * Transição manual de status (ex: EM_ATENDIMENTO -> FINALIZADO, feita
   * pelo barbeiro). A transição AGENDADO -> AGUARDANDO também pode
   * acontecer por aqui, mas na prática o job automático já faz isso —
   * esse endpoint continua existindo para os outros passos do fluxo.
   */
  public AgendamentoResponseDTO atualizarStatus(Long id, StatusAgendamento novoStatus) {
    Agendamento agendamento = buscarOuFalhar(id);
    agendamento.mudarStatus(novoStatus);
    Agendamento agendamentoAtualizado = agendamentoRepository.save(agendamento);
    return AgendamentoResponseDTO.fromEntity(agendamentoAtualizado);
  }

  /**
   * Confirmação de presença feita pelo cliente ANTES do dia chegar.
   * Não muda o status — só marca o selo que a fila exibe pro barbeiro.
   */
  public AgendamentoResponseDTO confirmarPresenca(Long id) {
    Agendamento agendamento = buscarOuFalhar(id);
    agendamento.confirmarPresenca();
    Agendamento agendamentoAtualizado = agendamentoRepository.save(agendamento);
    return AgendamentoResponseDTO.fromEntity(agendamentoAtualizado);
  }

  private Agendamento buscarOuFalhar(Long id) {
    return agendamentoRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Agendamento não encontrado com o ID: " + id,
            ErrorCode.AGENDAMENTO_NAO_ENCONTRADO));
  }

}