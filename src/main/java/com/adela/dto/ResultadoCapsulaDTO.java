package com.adela.dto;

import java.time.Instant;
import java.util.List;

import com.adela.entities.PreferenciaMultimodal;

/**
 * Resultado de una resolución de cápsula. Lo lee cualquiera que tenga el código,
 * así que no lleva más dato personal que el nombre que la persona escribió.
 * pidePreferencia: aplica la pregunta selectivo/integrativo; preferenciaMultimodal
 * es la respuesta, null mientras no se declare.
 */
public record ResultadoCapsulaDTO(String codigo, String capsulaNombre, CuestionarioResumidoDTO cuestionario,
        String nombre, Instant respondidaEn, List<EstiloResultadoDTO> estilos, CalificacionDTO calificacion,
        List<PreguntaResueltaDTO> preguntas, boolean pidePreferencia, PreferenciaMultimodal preferenciaMultimodal) {
}
