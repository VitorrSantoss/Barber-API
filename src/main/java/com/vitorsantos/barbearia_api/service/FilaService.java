package com.vitorsantos.barbearia_api.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.vitorsantos.barbearia_api.dto.AgendamentoResponseDTO;
import com.vitorsantos.barbearia_api.dto.ClienteNaFilaDTO;
import com.vitorsantos.barbearia_api.dto.EntradaFilaRequestDTO;
import com.vitorsantos.barbearia_api.dto.FilaResponseDTO;
import com.vitorsantos.barbearia_api.dto.PosicaoFilaDTO;
import com.vitorsantos.barbearia_api.enums.ErrorCode;
import com.vitorsantos.barbearia_api.enums.StatusAgendamento;
import com.vitorsantos.barbearia_api.event.FilaAtualizadaEvent;
import com.vitorsantos.barbearia_api.exception.ConflitoDeRegraException;
import com.vitorsantos.barbearia_api.models.Agendamento;
import com.vitorsantos.barbearia_api.models.Barbeiro;
import com.vitorsantos.barbearia_api.models.Cliente;
import com.vitorsantos.barbearia_api.models.Servico;
import com.vitorsantos.barbearia_api.repository.AgendamentoRepository;
import com.vitorsantos.barbearia_api.repository.BarbeiroRepository;

import lombok.RequiredArgsConstructor;

/**
 * Regras da fila de atendimento. Cada barbeiro tem a sua fila: clientes
 * AGUARDANDO, ordenados por ordem de chegada (horaChegada, desempate por
 * id), mais no máximo um cliente EM_ATENDIMENTO.
 *
 * Concorrência: toda operação que altera uma fila trava antes a linha do
 * barbeiro (SELECT ... FOR UPDATE) — e a do cliente, quando ele entra na
 * fila. Assim operações na mesma fila são serializadas pelo banco (inclusive
 * entre várias instâncias da API), enquanto filas de barbeiros diferentes
 * seguem independentes. O isolamento READ_COMMITTED garante que as checagens
 * feitas depois do lock enxergam o que a transação anterior acabou de gravar
 * (no REPEATABLE READ padrão do MySQL elas leriam um snapshot antigo).
 */
@Service
@RequiredArgsConstructor
public class FilaService {

  private final AgendamentoRepository agendamentoRepository;
  private final BarbeiroRepository barbeiroRepository;
  private final BarbeiroService barbeiroService;
  private final ClienteService clienteService;
  private final ServicoService servicoService;
  private final ApplicationEventPublisher eventPublisher;

  // ───────────────────────────── Consultas ─────────────────────────────

  @Transactional(readOnly = true)
  public FilaResponseDTO consultarFilaPorBarbeiro(Long barbeiroId) {
    Barbeiro barbeiro = barbeiroService.buscarOuFalhar(barbeiroId);
    List<Agendamento> fila = agendamentoRepository.findFilaDoBarbeiro(barbeiroId, StatusAgendamento.NA_FILA);
    return montarFila(barbeiro, fila);
  }

  /**
   * Painel geral: fila de todos os barbeiros ATIVOS, com duas queries no
   * total (barbeiros + agendamentos), independente de quantos barbeiros há.
   */
  @Transactional(readOnly = true)
  public List<FilaResponseDTO> consultarFilaDeTodosBarbeiros() {
    Map<Long, List<Agendamento>> filaPorBarbeiro = new LinkedHashMap<>();
    agendamentoRepository.findFilaDosBarbeirosAtivos(StatusAgendamento.NA_FILA)
        .forEach(agendamento -> filaPorBarbeiro
            .computeIfAbsent(agendamento.getBarbeiro().getId(), id -> new ArrayList<>())
            .add(agendamento));

    return barbeiroRepository.findByAtivoTrueOrderByNomeAsc().stream()
        .map(barbeiro -> montarFila(barbeiro, filaPorBarbeiro.getOrDefault(barbeiro.getId(), List.of())))
        .toList();
  }

  @Transactional(readOnly = true)
  public PosicaoFilaDTO consultarPosicao(Agendamento agendamento) {
    Barbeiro barbeiro = agendamento.getBarbeiro();
    int totalNaFila = (int) agendamentoRepository
        .countByBarbeiroIdAndStatusAgendamento(barbeiro.getId(), StatusAgendamento.AGUARDANDO);

    Integer pessoasAFrente = null;
    Integer posicao = null;
    if (agendamento.getStatusAgendamento() == StatusAgendamento.AGUARDANDO) {
      pessoasAFrente = (int) agendamentoRepository.countAFrenteNaFila(
          barbeiro.getId(), StatusAgendamento.AGUARDANDO, agendamento.getHoraChegada(), agendamento.getId());
      posicao = pessoasAFrente + 1;
    }

    return new PosicaoFilaDTO(
        agendamento.getId(),
        barbeiro.getId(),
        barbeiro.getNome(),
        agendamento.getStatusAgendamento(),
        posicao,
        pessoasAFrente,
        totalNaFila);
  }

  // ───────────────────────────── Alterações ────────────────────────────

  /** Cliente chegou na barbearia e entra no fim da fila do barbeiro. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public AgendamentoResponseDTO entrarNaFila(Long barbeiroId, EntradaFilaRequestDTO dto) {
    // Ordem fixa dos locks (cliente -> barbeiro) em todo o sistema, para
    // não haver deadlock entre transações concorrentes.
    Cliente cliente = clienteService.buscarParaAtualizacaoOuFalhar(dto.clienteId());
    Barbeiro barbeiro = barbeiroService.buscarParaAtualizacaoOuFalhar(barbeiroId);
    Servico servico = servicoService.buscarOuFalhar(dto.servicoId());

    validarBarbeiroAtivo(barbeiro);
    validarServicoAtivo(servico);
    validarClienteForaDaFila(cliente, null);

    cliente.registrarAcesso();
    Agendamento agendamento = agendamentoRepository.save(Agendamento.entrarNaFila(cliente, barbeiro, servico));

    eventPublisher.publishEvent(new FilaAtualizadaEvent(barbeiroId));
    return AgendamentoResponseDTO.fromEntity(agendamento);
  }

  /** O barbeiro chama o primeiro da fila para ser atendido. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public AgendamentoResponseDTO chamarProximo(Long barbeiroId) {
    Barbeiro barbeiro = barbeiroService.buscarParaAtualizacaoOuFalhar(barbeiroId);
    validarBarbeiroLivre(barbeiro);

    Agendamento proximo = agendamentoRepository
        .findFilaDoBarbeiro(barbeiroId, List.of(StatusAgendamento.AGUARDANDO))
        .stream()
        .findFirst()
        .orElseThrow(() -> new ConflitoDeRegraException(
            "Não há clientes aguardando na fila do barbeiro " + barbeiro.getNome(),
            ErrorCode.FILA_VAZIA));

    proximo.mudarStatus(StatusAgendamento.EM_ATENDIMENTO);

    eventPublisher.publishEvent(new FilaAtualizadaEvent(barbeiroId));
    return AgendamentoResponseDTO.fromEntity(agendamentoRepository.saveAndFlush(proximo));
  }

  /**
   * Toda mudança de status de agendamento passa por aqui, para que as
   * regras da fila valham igual para o endpoint de status, para o "chamar
   * próximo" e para o job automático.
   *
   * O agendamento recebido pode ter sido lido antes do lock; se outra
   * transação o alterou nesse meio tempo, o @Version faz o save falhar com
   * conflito (409) em vez de sobrescrever a alteração.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Agendamento mudarStatus(Agendamento agendamento, StatusAgendamento novoStatus) {
    StatusAgendamento statusAnterior = agendamento.getStatusAgendamento();

    if (novoStatus == StatusAgendamento.AGUARDANDO) {
      clienteService.buscarParaAtualizacaoOuFalhar(agendamento.getCliente().getId());
    }
    Barbeiro barbeiro = barbeiroService.buscarParaAtualizacaoOuFalhar(agendamento.getBarbeiro().getId());

    switch (novoStatus) {
      case AGUARDANDO -> {
        validarBarbeiroAtivo(barbeiro);
        validarClienteForaDaFila(agendamento.getCliente(), agendamento.getId());
      }
      case EM_ATENDIMENTO -> {
        validarBarbeiroLivre(barbeiro);
        validarPrimeiroDaFila(agendamento);
      }
      default -> {
        // FINALIZADO / CANCELADO: a máquina de estados da entidade basta
      }
    }

    agendamento.mudarStatus(novoStatus);
    Agendamento salvo = agendamentoRepository.saveAndFlush(agendamento);

    if (StatusAgendamento.NA_FILA.contains(statusAnterior) || StatusAgendamento.NA_FILA.contains(novoStatus)) {
      eventPublisher.publishEvent(new FilaAtualizadaEvent(barbeiro.getId()));
    }
    return salvo;
  }

  // ───────────────────────────── Regras ────────────────────────────────

  void validarBarbeiroAtivo(Barbeiro barbeiro) {
    if (!barbeiro.isAtivo()) {
      throw new ConflitoDeRegraException(
          "O barbeiro %s está inativo e não pode receber clientes".formatted(barbeiro.getNome()),
          ErrorCode.BARBEIRO_INATIVO);
    }
  }

  void validarServicoAtivo(Servico servico) {
    if (!servico.isAtivo()) {
      throw new ConflitoDeRegraException(
          "O serviço %s está inativo".formatted(servico.getNome()),
          ErrorCode.SERVICO_INATIVO);
    }
  }

  private void validarClienteForaDaFila(Cliente cliente, Long agendamentoIgnorado) {
    boolean jaNaFila = agendamentoIgnorado == null
        ? agendamentoRepository.existsByClienteIdAndStatusAgendamentoIn(cliente.getId(), StatusAgendamento.NA_FILA)
        : agendamentoRepository.existsByClienteIdAndStatusAgendamentoInAndIdNot(
            cliente.getId(), StatusAgendamento.NA_FILA, agendamentoIgnorado);

    if (jaNaFila) {
      throw new ConflitoDeRegraException(
          "O cliente %s já está em uma fila de atendimento".formatted(cliente.getNome()),
          ErrorCode.CLIENTE_JA_NA_FILA);
    }
  }

  private void validarBarbeiroLivre(Barbeiro barbeiro) {
    if (agendamentoRepository.existsByBarbeiroIdAndStatusAgendamento(
        barbeiro.getId(), StatusAgendamento.EM_ATENDIMENTO)) {
      throw new ConflitoDeRegraException(
          "O barbeiro %s já está com um cliente em atendimento. Finalize-o antes de chamar o próximo."
              .formatted(barbeiro.getNome()),
          ErrorCode.BARBEIRO_OCUPADO);
    }
  }

  private void validarPrimeiroDaFila(Agendamento agendamento) {
    if (agendamento.getStatusAgendamento() != StatusAgendamento.AGUARDANDO) {
      return; // a transição inválida é reportada pela própria entidade
    }
    long aFrente = agendamentoRepository.countAFrenteNaFila(
        agendamento.getBarbeiro().getId(), StatusAgendamento.AGUARDANDO,
        agendamento.getHoraChegada(), agendamento.getId());
    if (aFrente > 0) {
      throw new ConflitoDeRegraException(
          "A ordem de chegada deve ser respeitada: há %d cliente(s) antes deste na fila".formatted(aFrente),
          ErrorCode.FORA_DA_ORDEM_DA_FILA);
    }
  }

  // ───────────────────────────── Montagem ──────────────────────────────

  private FilaResponseDTO montarFila(Barbeiro barbeiro, List<Agendamento> agendamentosNaFila) {
    ClienteNaFilaDTO emAtendimento = agendamentosNaFila.stream()
        .filter(a -> a.getStatusAgendamento() == StatusAgendamento.EM_ATENDIMENTO)
        .findFirst()
        .map(a -> ClienteNaFilaDTO.fromEntity(a, null))
        .orElse(null);

    List<Agendamento> aguardando = agendamentosNaFila.stream()
        .filter(a -> a.getStatusAgendamento() == StatusAgendamento.AGUARDANDO)
        .toList();

    // a lista já vem ordenada por chegada: posição = índice + 1
    List<ClienteNaFilaDTO> clientes = IntStream.range(0, aguardando.size())
        .mapToObj(indice -> ClienteNaFilaDTO.fromEntity(aguardando.get(indice), indice + 1))
        .toList();

    return new FilaResponseDTO(
        barbeiro.getId(),
        barbeiro.getNome(),
        clientes.size(),
        emAtendimento,
        clientes.isEmpty() ? null : clientes.getFirst(),
        clientes);
  }
}
