package com.vitorsantos.barbearia_api.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vitorsantos.barbearia_api.dto.ServicoRequestDTO;
import com.vitorsantos.barbearia_api.dto.ServicoResponseDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.exception.ConflitoDeRegraException;
import com.vitorsantos.barbearia_api.exception.ResourceNotFoundException;
import com.vitorsantos.barbearia_api.models.Servico;
import com.vitorsantos.barbearia_api.repository.ServicoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ServicoService {

  private final ServicoRepository servicoRepository;

  @Transactional(readOnly = true)
  public List<ServicoResponseDTO> listarServicos(boolean apenasAtivos) {
    List<Servico> servicos = apenasAtivos
        ? servicoRepository.findByAtivoTrueOrderByNomeAsc()
        : servicoRepository.findAllByOrderByNomeAsc();
    return servicos.stream().map(ServicoResponseDTO::fromEntity).toList();
  }

  @Transactional(readOnly = true)
  public ServicoResponseDTO buscarPorId(Long id) {
    return ServicoResponseDTO.fromEntity(buscarOuFalhar(id));
  }

  @Transactional
  public ServicoResponseDTO cadastrarServico(ServicoRequestDTO dto) {
    if (servicoRepository.existsByNomeIgnoreCase(dto.nome())) {
      throw nomeDuplicado(dto.nome());
    }
    Servico servico = new Servico();
    preencher(servico, dto);
    servico.setAtivo(dto.ativo() == null || dto.ativo());
    return ServicoResponseDTO.fromEntity(salvar(servico));
  }

  @Transactional
  public ServicoResponseDTO atualizarServico(Long id, ServicoRequestDTO dto) {
    Servico servico = buscarOuFalhar(id);
    if (servicoRepository.existsByNomeIgnoreCaseAndIdNot(dto.nome(), id)) {
      throw nomeDuplicado(dto.nome());
    }
    preencher(servico, dto);
    if (dto.ativo() != null) {
      servico.setAtivo(dto.ativo());
    }
    return ServicoResponseDTO.fromEntity(salvar(servico));
  }

  /**
   * Exclusão lógica: o serviço some das opções, mas os atendimentos
   * antigos continuam apontando para ele.
   */
  @Transactional
  public void desativarServico(Long id) {
    buscarOuFalhar(id).setAtivo(false);
  }

  Servico buscarOuFalhar(Long id) {
    return servicoRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Serviço não encontrado com o ID: " + id,
            ErrorCode.SERVICO_NAO_ENCONTRADO));
  }

  private void preencher(Servico servico, ServicoRequestDTO dto) {
    servico.setNome(dto.nome());
    servico.setDescricao(dto.descricao());
    servico.setPreco(dto.preco());
    servico.setDuracaoMinutos(dto.duracaoMinutos());
  }

  private Servico salvar(Servico servico) {
    try {
      return servicoRepository.saveAndFlush(servico);
    } catch (DataIntegrityViolationException ex) {
      throw nomeDuplicado(servico.getNome());
    }
  }

  private ConflitoDeRegraException nomeDuplicado(String nome) {
    return new ConflitoDeRegraException(
        "Já existe um serviço cadastrado com o nome: " + nome,
        ErrorCode.NOME_SERVICO_DUPLICADO);
  }
}
