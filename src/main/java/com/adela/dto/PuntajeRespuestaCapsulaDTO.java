package com.adela.dto;

import java.time.Instant;

/**
 * Puntos de una respuesta en una categoría, ya sumados en la base de datos.
 * Una fila por (respuesta, categoría) con al menos una opción elegida.
 */
public record PuntajeRespuestaCapsulaDTO(Long respuestaId, String nombre, Instant respondidaEn, Long categoriaId,
        Double total) {
}
