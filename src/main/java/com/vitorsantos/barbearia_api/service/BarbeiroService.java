package com.vitorsantos.barbearia_api.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.vitorsantos.barbearia_api.dto.BarbeiroRequestDTO;
import com.vitorsantos.barbearia_api.dto.BarbeiroResponseDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.exception.ConflitoDeRegraException;
import com.vitorsantos.barbearia_api.exception.ResourceNotFoundException;
import com.vitorsantos.barbearia_api.models.Barbeiro;
import com.vitorsantos.barbearia_api.repository.AgendamentoRepository;
import com.vitorsantos.barbearia_api.repository.BarbeiroRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BarbeiroService {

  private final BarbeiroRepository barbeiroRepository;
  private final AgendamentoRepository agendamentoRepository;

  @Transactional(readOnly = true)
  public List<BarbeiroResponseDTO> listarBarbeiros(boolean apenasAtivos) {
    List<Barbeiro> barbeiros = apenasAtivos
        ? barbeiroRepository.findByAtivoTrueOrderByNomeAsc()
        : barbeiroRepository.findAllByOrderByNomeAsc();
    return barbeiros.stream().map(BarbeiroResponseDTO::fromEntity).toList();
  }

  @Transactional(readOnly = true)
  public BarbeiroResponseDTO buscarPorId(Long id) {
    return BarbeiroResponseDTO.fromEntity(buscarOuFalhar(id));
  }

  @Transactional
  public BarbeiroResponseDTO cadastrarBarbeiro(BarbeiroRequestDTO dto) {
    Barbeiro barbeiro = new Barbeiro();
    barbeiro.setNome(dto.nome());
    barbeiro.setAtivo(dto.ativo() == null || dto.ativo());
    return BarbeiroResponseDTO.fromEntity(barbeiroRepository.save(barbeiro));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public BarbeiroResponseDTO atualizarBarbeiro(Long id, BarbeiroRequestDTO dto) {
    Barbeiro barbeiro = buscarParaAtualizacaoOuFalhar(id);
    barbeiro.setNome(dto.nome());
    if (dto.ativo() != null) {
      alterarAtivo(barbeiro, dto.ativo());
    }
    return BarbeiroResponseDTO.fromEntity(barbeiro);
  }

  /**
   * Desativa o barbeiro (exclusão lógica): o histórico de atendimentos
   * dele continua íntegro, ele só deixa de aparecer no painel e de
   * receber clientes.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void desativarBarbeiro(Long id) {
    alterarAtivo(buscarParaAtualizacaoOuFalhar(id), false);
  }

  Barbeiro buscarOuFalhar(Long id) {
    return barbeiroRepository.findById(id).orElseThrow(() -> barbeiroNaoEncontrado(id));
  }

  /** SELECT ... FOR UPDATE: serializa as operações na fila desse barbeiro. */
  Barbeiro buscarParaAtualizacaoOuFalhar(Long id) {
    return barbeiroRepository.findByIdForUpdate(id).orElseThrow(() -> barbeiroNaoEncontrado(id));
  }

  private void alterarAtivo(Barbeiro barbeiro, boolean ativo) {
    if (!ativo && barbeiro.isAtivo()) {
      long emAberto = agendamentoRepository.countByBarbeiroIdAndStatusAgendamentoIn(
          barbeiro.getId(), StatusAgendamento.EM_ABERTO);
      if (emAberto > 0) {
        throw new ConflitoDeRegraException(
            "O barbeiro possui %d agendamento(s) em aberto. Finalize ou cancele antes de desativá-lo."
                .formatted(emAberto),
            ErrorCode.BARBEIRO_COM_FILA_ATIVA);
      }
    }
    barbeiro.setAtivo(ativo);
  }

  private ResourceNotFoundException barbeiroNaoEncontrado(Long id) {
    return new ResourceNotFoundException("Barbeiro não encontrado com o ID: " + id, ErrorCode.BARBEIRO_NAO_ENCONTRADO);
  }
}
