package com.adela.dto;

import java.time.Instant;

import com.adela.entities.Capsula;
import com.adela.entities.ModoIdentificacion;

/**
 * Cápsula vista por su profesor. numRespuestas sale de un COUNT en la consulta
 * (CapsulaRepository.resumirPorProfesor), no de recorrer la relación.
 */
public record CapsulaDTO(Long id, String codigo, String nombre, Long cuestionarioId, String cuestionarioNombre,
        String cuestionarioSiglas, ModoIdentificacion modoIdentificacion, boolean abierta, Instant creadaEn,
        long numRespuestas) {

    public static CapsulaDTO from(Capsula c, long numRespuestas) {
        return new CapsulaDTO(c.getId(), c.getCodigo(), c.getNombre(), c.getCuestionario().getId(),
                c.getCuestionario().getNombre(), c.getCuestionario().getSiglas(), c.getModoIdentificacion(),
                c.isAbierta(), c.getCreadaEn(), numRespuestas);
    }
}
