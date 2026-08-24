package com.vitorsantos.barbearia_api.enums;

/**
 * Códigos de erro que o front pode usar para identificar o problema
 * programaticamente, sem depender do texto da mensagem (que pode mudar).
 */
public enum ErrorCode {
  TELEFONE_DUPLICADO,
  CLIENTE_NAO_ENCONTRADO,
  BARBEIRO_NAO_ENCONTRADO,
  AGENDAMENTO_NAO_ENCONTRADO,
  TRANSICAO_STATUS_INVALIDA,
  CONFIRMACAO_INVALIDA,
  ERRO_VALIDACAO,
  ERRO_INTERNO
}