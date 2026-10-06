package com.adela.dto;

import com.adela.entities.ModoIdentificacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CapsulaCrearDTO(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "Máximo 100 caracteres") String nombre,
        @NotNull(message = "Elige un cuestionario") Long cuestionarioId,
        @NotNull(message = "Elige si la cápsula pide nombre") ModoIdentificacion modoIdentificacion) {
}
