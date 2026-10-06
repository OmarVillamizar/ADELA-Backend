package com.adela.dto;

/**
 * Puntos de una respuesta en un estilo, ya sumados en la base de datos.
 * Una fila por (respuesta, estilo) con al menos una opción elegida.
 */
public record PuntajeRespuestaCapsulaDTO(Long respuestaId, Long estiloId, Double total) {
}
