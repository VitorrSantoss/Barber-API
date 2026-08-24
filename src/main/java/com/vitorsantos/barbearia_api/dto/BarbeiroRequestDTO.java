package com.vitorsantos.barbearia_api.dto;

import jakarta.validation.constraints.NotBlank;

public record BarbeiroRequestDTO(
    @NotBlank(message = "O nome é obrigatório") String nome) {
}