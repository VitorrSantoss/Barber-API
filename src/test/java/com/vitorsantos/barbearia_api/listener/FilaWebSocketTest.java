package com.vitorsantos.barbearia_api.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.vitorsantos.barbearia_api.dto.BarbeiroRequestDTO;
import com.vitorsantos.barbearia_api.dto.ClienteRequestDTO;
import com.vitorsantos.barbearia_api.dto.EntradaFilaRequestDTO;
import com.vitorsantos.barbearia_api.dto.FilaResponseDTO;
import com.vitorsantos.barbearia_api.dto.ServicoRequestDTO;
import com.vitorsantos.barbearia_api.service.BarbeiroService;
import com.vitorsantos.barbearia_api.service.ClienteService;
import com.vitorsantos.barbearia_api.service.FilaService;
import com.vitorsantos.barbearia_api.service.ServicoService;

/**
 * Um cliente STOMP de verdade se inscreve na fila de um barbeiro e recebe
 * a fila atualizada quando alguém entra nela e quando o atendimento avança.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FilaWebSocketTest {

  @LocalServerPort
  private int porta;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private ClienteService clienteService;

  @Autowired
  private BarbeiroService barbeiroService;

  @Autowired
  private ServicoService servicoService;

  @Autowired
  private FilaService filaService;

  private WebSocketStompClient stompClient;
  private StompSession sessao;

  @BeforeEach
  void conectar() throws Exception {
    jdbcTemplate.execute("DELETE FROM tb_agendamentos");
    jdbcTemplate.execute("DELETE FROM tb_clientes");
    jdbcTemplate.execute("DELETE FROM tb_barbeiros");
    jdbcTemplate.execute("DELETE FROM tb_servicos");

    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());

    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setOrigin("http://localhost:5173");
    sessao = stompClient
        .connectAsync("ws://localhost:" + porta + "/ws/websocket", headers, new StompSessionHandlerAdapter() {
        })
        .get(10, TimeUnit.SECONDS);
  }

  @AfterEach
  void desconectar() {
    if (sessao != null && sessao.isConnected()) {
      sessao.disconnect();
    }
    stompClient.stop();
  }

  @Test
  void publicaFilaAtualizadaAposCadaMudanca() throws Exception {
    Long barbeiro = barbeiroService.cadastrarBarbeiro(new BarbeiroRequestDTO("Anderson", null)).id();
    Long servico = servicoService.cadastrarServico(
        new ServicoRequestDTO("Corte", null, new java.math.BigDecimal("40.00"), 30, null)).id();
    Long joao = clienteService.cadastrarCliente(new ClienteRequestDTO("João", "81988887777")).id();

    BlockingQueue<FilaResponseDTO> recebidas = new LinkedBlockingQueue<>();
    sessao.subscribe("/topic/fila/" + barbeiro, new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return FilaResponseDTO.class;
      }

      @Override
      public void handleFrame(StompHeaders headers, Object payload) {
        recebidas.add((FilaResponseDTO) payload);
      }
    });
    Thread.sleep(300); // garante que a inscrição chegou ao broker antes da primeira mudança

    filaService.entrarNaFila(barbeiro, new EntradaFilaRequestDTO(joao, servico));

    FilaResponseDTO aposEntrada = recebidas.poll(10, TimeUnit.SECONDS);
    assertThat(aposEntrada).isNotNull();
    assertThat(aposEntrada.totalNaFila()).isEqualTo(1);
    assertThat(aposEntrada.clientes().getFirst().nomeCliente()).isEqualTo("João");

    filaService.chamarProximo(barbeiro);

    FilaResponseDTO aposChamar = recebidas.poll(10, TimeUnit.SECONDS);
    assertThat(aposChamar).isNotNull();
    assertThat(aposChamar.totalNaFila()).isZero();
    assertThat(aposChamar.emAtendimento().nomeCliente()).isEqualTo("João");
  }
}
