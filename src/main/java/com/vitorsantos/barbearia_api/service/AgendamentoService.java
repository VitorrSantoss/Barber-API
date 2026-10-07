package com.vitorsantos.barbearia_api.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.vitorsantos.barbearia_api.dto.AgendamentoRequestDTO;
import com.vitorsantos.barbearia_api.dto.AgendamentoResponseDTO;
import com.vitorsantos.barbearia_api.dto.PosicaoFilaDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.exception.ResourceNotFoundException;
import com.vitorsantos.barbearia_api.models.Agendamento;
import com.vitorsantos.barbearia_api.models.Barbeiro;
import com.vitorsantos.barbearia_api.models.Cliente;
import com.vitorsantos.barbearia_api.models.Servico;
import com.vitorsantos.barbearia_api.repository.AgendamentoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

  private final AgendamentoRepository agendamentoRepository;
  private final ClienteService clienteService;
  private final BarbeiroService barbeiroService;
  private final ServicoService servicoService;
  private final FilaService filaService;

  @Transactional(readOnly = true)
  public List<AgendamentoResponseDTO> listarAgendamentos() {
    return agendamentoRepository.findAllByOrderByDataHoraDesc().stream()
        .map(AgendamentoResponseDTO::fromEntity)
        .toList();
  }

  @Transactional(readOnly = true)
  public AgendamentoResponseDTO buscarPorId(Long id) {
    return AgendamentoResponseDTO.fromEntity(buscarOuFalhar(id));
  }

  @Transactional(readOnly = true)
  public PosicaoFilaDTO consultarPosicao(Long id) {
    return filaService.consultarPosicao(buscarOuFalhar(id));
  }

  /**
   * Horário marcado: nasce AGENDADO e não afeta a fila ainda — o job
   * automático o coloca na fila quando a data/hora chegar.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public AgendamentoResponseDTO cadastrarAgendamento(AgendamentoRequestDTO dto) {
    Cliente cliente = clienteService.buscarParaAtualizacaoOuFalhar(dto.clienteId());
    Barbeiro barbeiro = barbeiroService.buscarParaAtualizacaoOuFalhar(dto.barbeiroId());
    Servico servico = servicoService.buscarOuFalhar(dto.servicoId());

    filaService.validarBarbeiroAtivo(barbeiro);
    filaService.validarServicoAtivo(servico);

    cliente.registrarAcesso();
    Agendamento agendamento = Agendamento.agendar(cliente, barbeiro, servico, dto.dataHora());
    return AgendamentoResponseDTO.fromEntity(agendamentoRepository.save(agendamento));
  }

  /**
   * Transição manual de status (ex: EM_ATENDIMENTO -> FINALIZADO, feita
   * pelo barbeiro). As regras da fila ficam no FilaService.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public AgendamentoResponseDTO atualizarStatus(Long id, StatusAgendamento novoStatus) {
    Agendamento agendamento = buscarOuFalhar(id);
    return AgendamentoResponseDTO.fromEntity(filaService.mudarStatus(agendamento, novoStatus));
  }

  /**
   * Confirmação de presença não muda status nem a ordem da fila — é só o
   * selo que o barbeiro vê.
   */
  @Transactional
  public AgendamentoResponseDTO confirmarPresenca(Long id) {
    Agendamento agendamento = buscarOuFalhar(id);
    agendamento.confirmarPresenca();
    agendamento.getCliente().registrarAcesso();
    return AgendamentoResponseDTO.fromEntity(agendamentoRepository.saveAndFlush(agendamento));
  }

  private Agendamento buscarOuFalhar(Long id) {
    return agendamentoRepository.findComDetalhesById(id)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Agendamento não encontrado com o ID: " + id,
            ErrorCode.AGENDAMENTO_NAO_ENCONTRADO));
  }
}
