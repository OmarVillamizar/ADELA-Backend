package com.adela.dto;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de una resolución de cápsula. Lo lee cualquiera que tenga el código,
 * así que no lleva más dato personal que el nombre que la persona escribió.
 * complementaria: la pregunta extra, solo si le toca; respuestaComplementaria
 * es lo elegido, null mientras no se responda.
 */
public record ResultadoCapsulaDTO(String codigo, String capsulaNombre, CuestionarioResumidoDTO cuestionario,
        String nombre, Instant respondidaEn, List<EstiloResultadoDTO> estilos, CalificacionDTO calificacion,
        List<PreguntaResueltaDTO> preguntas, ComplementariaDTO.Pregunta complementaria,
        ComplementariaDTO.Respuesta respuestaComplementaria) {
}
