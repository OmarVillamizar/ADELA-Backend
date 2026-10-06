package com.adela.dto;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de una resolución de cápsula. Lo lee cualquiera que tenga el código,
 * así que no lleva más dato personal que el nombre que la persona escribió.
 */
public record ResultadoCapsulaDTO(String codigo, String capsulaNombre, CuestionarioResumidoDTO cuestionario,
        String nombre, Instant respondidaEn, List<CategoriaResultadoDTO> categorias,
        List<PreguntaResueltaDTO> preguntas) {
}
