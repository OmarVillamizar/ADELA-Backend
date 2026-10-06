package com.adela.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Resolución enviada desde el enlace público.
 *
 * intento lo genera el cliente una vez por resolución: si la red falla y la
 * reenvía, el servidor devuelve el resultado ya guardado en lugar de crear otro.
 * El tope de opciones acota el trabajo que una sola petición anónima puede pedir.
 */
public record RespuestaCapsulaDTO(
        @NotNull(message = "Falta el identificador del intento") UUID intento,
        @Size(max = 60, message = "Máximo 60 caracteres") String nombre,
        @NotNull(message = "Faltan las respuestas") @Size(max = 500, message = "Demasiadas opciones") List<Long> opcionesSeleccionadasId) {
}
