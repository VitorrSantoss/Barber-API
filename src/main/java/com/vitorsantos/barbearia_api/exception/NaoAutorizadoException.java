package com.vitorsantos.barbearia_api.exception;

import org.springframework.http.HttpStatus;

import com.vitorsantos.barbearia_api.enums.ErrorCode;

/** Credenciais ausentes ou incorretas. Sempre resulta em 401. */
public class NaoAutorizadoException extends BusinessException {

  public NaoAutorizadoException(String message, ErrorCode errorCode) {
    super(message, HttpStatus.UNAUTHORIZED, errorCode);
  }
}
