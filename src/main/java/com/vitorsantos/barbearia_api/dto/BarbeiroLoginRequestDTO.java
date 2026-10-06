package com.vitorsantos.barbearia_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A senha é numérica e única por barbeiro, então ela mesma identifica quem está entrando. */
public record BarbeiroLoginRequestDTO(
    @NotBlank(message = "A senha é obrigatória")
    @Pattern(regexp = "^[0-9]{6}$", message = "A senha deve ter 6 dígitos")
    String senha) {
}
