package com.adela.dto;

import java.time.Instant;
import java.util.List;

/**
 * Reporte agregado de una cápsula. El detalle por pregunta de cada persona no
 * se expone: el nombre no está verificado, y el seguimiento individual queda
 * para los grupos.
 *
 * participantes es null en modo ANONIMO. predominantes cuenta cuántas
 * respuestas tienen ese estilo como predominante; con empates una
 * respuesta cuenta en varias, así que la suma puede superar totalRespuestas.
 */
public record CapsulaReporteDTO(CapsulaDTO capsula, long totalRespuestas, List<EstiloReporteDTO> estilos,
        List<ParticipanteDTO> participantes) {

    public record EstiloReporteDTO(String nombre, Double valorMinimo, Double valorMaximo, double promedio,
            long predominantes) {
    }

    public record ParticipanteDTO(String nombre, Instant respondidaEn, List<String> predominantes) {
    }
}
