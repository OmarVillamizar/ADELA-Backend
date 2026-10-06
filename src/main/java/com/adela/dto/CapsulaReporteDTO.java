package com.adela.dto;

import java.time.Instant;
import java.util.List;

/**
 * Reporte agregado de una cápsula, con la misma estadística que el de un grupo.
 * El detalle por pregunta de cada persona no se expone: el nombre no está
 * verificado, y el seguimiento individual queda para los grupos.
 *
 * participantes es null en modo ANONIMO. perfil es null si el cuestionario no
 * define dominancia o la respuesta no tiene puntos.
 */
public record CapsulaReporteDTO(CapsulaDTO capsula, long totalRespuestas, List<EstiloResultadoDTO> estilos,
        CalificacionDTO calificacion, List<ParticipanteDTO> participantes) {

    public record ParticipanteDTO(String nombre, Instant respondidaEn, String perfil) {
    }
}
