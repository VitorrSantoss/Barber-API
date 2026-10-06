package com.vitorsantos.barbearia_api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param ativo opcional; no cadastro o padrão é true.
 */
public record ServicoRequestDTO(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    String nome,

    @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
    String descricao,

    @NotNull(message = "O preço é obrigatório")
    @DecimalMin(value = "0.00", message = "O preço não pode ser negativo")
    @Digits(integer = 8, fraction = 2, message = "O preço deve ter no máximo 2 casas decimais")
    BigDecimal preco,

    @NotNull(message = "A duração é obrigatória")
    @Positive(message = "A duração deve ser maior que zero")
    @Max(value = 600, message = "A duração deve ser de no máximo 600 minutos")
    Integer duracaoMinutos,

    Boolean ativo) {

  public ServicoRequestDTO {
    nome = nome == null ? null : nome.strip();
    descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
  }
}
