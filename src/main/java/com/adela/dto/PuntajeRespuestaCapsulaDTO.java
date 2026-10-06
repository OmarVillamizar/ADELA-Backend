package com.adela.dto;

/**
 * Puntos de una respuesta en una categoría, ya sumados en la base de datos.
 * Una fila por (respuesta, categoría) con al menos una opción elegida.
 */
public record PuntajeRespuestaCapsulaDTO(Long respuestaId, Long categoriaId, Double total) {
}
