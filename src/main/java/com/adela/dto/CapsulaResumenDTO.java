package com.adela.dto;

import java.time.Instant;

import com.adela.entities.ModoIdentificacion;

/**
 * Fila del listado de cápsulas del profesor. numRespuestas sale de un COUNT en
 * la consulta (CapsulaRepository.resumirPorProfesor), no de recorrer la relación.
 */
public record CapsulaResumenDTO(Long id, String codigo, String nombre, String cuestionarioNombre,
        String cuestionarioSiglas, ModoIdentificacion modoIdentificacion, boolean abierta, Instant creadaEn,
        long numRespuestas) {
}
