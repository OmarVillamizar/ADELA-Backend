package com.adela.dto;

import jakarta.validation.constraints.Size;

/**
 * Campos editables de una cápsula; null deja el valor actual. El cuestionario y
 * el modo no se editan: cambiarlos a mitad mezclaría respuestas incomparables
 * en el mismo reporte.
 */
public record CapsulaActualizarDTO(@Size(max = 100, message = "Máximo 100 caracteres") String nombre,
        Boolean abierta) {
}
