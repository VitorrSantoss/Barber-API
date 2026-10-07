package com.vitorsantos.barbearia_api.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.vitorsantos.barbearia_api.dto.ClienteRequestDTO;
import com.vitorsantos.barbearia_api.dto.ClienteResponseDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.exception.ConflitoDeRegraException;
import com.vitorsantos.barbearia_api.exception.ResourceNotFoundException;
import com.vitorsantos.barbearia_api.models.Cliente;
import com.vitorsantos.barbearia_api.repository.AgendamentoRepository;
import com.vitorsantos.barbearia_api.repository.ClienteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteService {

  private final ClienteRepository clienteRepository;
  private final AgendamentoRepository agendamentoRepository;

  @Transactional(readOnly = true)
  public List<ClienteResponseDTO> listarClientes() {
    return clienteRepository.findByRemovidoEmIsNullOrderByNomeAsc().stream()
        .map(ClienteResponseDTO::fromEntity)
        .toList();
  }

  @Transactional(readOnly = true)
  public ClienteResponseDTO buscarPorId(Long id) {
    return ClienteResponseDTO.fromEntity(buscarOuFalhar(id));
  }

  @Transactional(readOnly = true)
  public ClienteResponseDTO buscarPorTelefone(String telefone) {
    String numero = ClienteRequestDTO.normalizarTelefone(telefone);
    return clienteRepository.findByNumeroAndRemovidoEmIsNull(numero)
        .map(ClienteResponseDTO::fromEntity)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Cliente não encontrado com o telefone: " + numero,
            ErrorCode.CLIENTE_NAO_ENCONTRADO));
  }

  @Transactional
  public ClienteResponseDTO cadastrarCliente(ClienteRequestDTO dto) {
    // dto.numero() já chega normalizado (só dígitos) pelo compact
    // constructor do ClienteRequestDTO.
    if (clienteRepository.existsByNumero(dto.numero())) {
      throw telefoneDuplicado(dto.numero());
    }

    try {
      // saveAndFlush: se outra requisição cadastrou o mesmo telefone entre
      // o exists acima e aqui, a constraint única do banco barra agora.
      Cliente clienteSalvo = clienteRepository.saveAndFlush(Cliente.novo(dto.nome(), dto.numero()));
      return ClienteResponseDTO.fromEntity(clienteSalvo);
    } catch (DataIntegrityViolationException ex) {
      throw telefoneDuplicado(dto.numero());
    }
  }

  /**
   * Remove o cliente: apaga de vez se ele nunca teve atendimento; se tem
   * histórico, anonimiza (para não quebrar o histórico dos barbeiros).
   * Bloqueado enquanto houver agendamento em aberto.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void deletarCliente(Long id) {
    Cliente cliente = clienteRepository.findByIdForUpdate(id)
        .orElseThrow(() -> clienteNaoEncontrado(id));

    if (agendamentoRepository.existsByClienteIdAndStatusAgendamentoIn(id, StatusAgendamento.EM_ABERTO)) {
      throw new ConflitoDeRegraException(
          "O cliente possui agendamento em aberto. Cancele ou finalize antes de removê-lo.",
          ErrorCode.CLIENTE_COM_ATENDIMENTO_ATIVO);
    }

    removerOuAnonimizar(cliente);
  }

  /**
   * Usado pelo job de limpeza: remove um cliente inativo SE, depois de
   * travado, ele ainda estiver inativo e sem agendamento em aberto (ele
   * pode ter entrado na fila entre a busca dos candidatos e agora).
   *
   * @return true se o cliente foi removido/anonimizado
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public boolean removerSeInativo(Long id, LocalDateTime dataLimite) {
    Cliente cliente = clienteRepository.findByIdForUpdate(id).orElse(null);

    if (cliente == null
        || !cliente.getUltimoLogin().isBefore(dataLimite)
        || agendamentoRepository.existsByClienteIdAndStatusAgendamentoIn(id, StatusAgendamento.EM_ABERTO)) {
      return false;
    }

    removerOuAnonimizar(cliente);
    return true;
  }

  /** Busca um cliente não removido ou lança 404. */
  Cliente buscarOuFalhar(Long id) {
    return clienteRepository.findByIdAndRemovidoEmIsNull(id)
        .orElseThrow(() -> clienteNaoEncontrado(id));
  }

  /** Mesmo que {@link #buscarOuFalhar}, mas com SELECT ... FOR UPDATE. */
  Cliente buscarParaAtualizacaoOuFalhar(Long id) {
    return clienteRepository.findByIdForUpdate(id)
        .orElseThrow(() -> clienteNaoEncontrado(id));
  }

  private void removerOuAnonimizar(Cliente cliente) {
    if (agendamentoRepository.existsByClienteId(cliente.getId())) {
      cliente.anonimizar();
      log.info("Cliente {} anonimizado (possui histórico de atendimentos)", cliente.getId());
    } else {
      clienteRepository.delete(cliente);
      log.info("Cliente {} removido", cliente.getId());
    }
  }

  private ResourceNotFoundException clienteNaoEncontrado(Long id) {
    return new ResourceNotFoundException("Cliente não encontrado com o ID: " + id, ErrorCode.CLIENTE_NAO_ENCONTRADO);
  }

  private ConflitoDeRegraException telefoneDuplicado(String numero) {
    return new ConflitoDeRegraException(
        "Já existe um cliente cadastrado com o telefone: " + numero,
        ErrorCode.TELEFONE_DUPLICADO);
  }
}
