package com.vitorsantos.barbearia_api.exception;

import org.springframework.http.HttpStatus;

import com.vitorsantos.barbearia_api.enums.ErrorCode;

import lombok.Getter;

/** Muitas tentativas de login erradas seguidas. Sempre resulta em 429. */
@Getter
public class LimiteTentativasException extends BusinessException {

  private final long segundosRestantes;

  public LimiteTentativasException(long segundosRestantes) {
    super("Muitas tentativas incorretas. Tente de novo em %ds.".formatted(segundosRestantes),
        HttpStatus.TOO_MANY_REQUESTS, ErrorCode.MUITAS_TENTATIVAS);
    this.segundosRestantes = segundosRestantes;
  }
}
