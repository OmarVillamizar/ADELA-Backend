package com.adela.dto;

import com.adela.entities.ModoIdentificacion;

/**
 * Lo que ve quien abre el enlace de una cápsula. Sin datos del profesor y con
 * el cuestionario sin baremo (CuestionarioParaResponderDTO, SEC-05).
 */
public record CapsulaPublicaDTO(String codigo, String nombre, ModoIdentificacion modoIdentificacion,
        CuestionarioParaResponderDTO cuestionario) {
}
