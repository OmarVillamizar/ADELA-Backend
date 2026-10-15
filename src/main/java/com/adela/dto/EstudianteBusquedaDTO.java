package com.adela.dto;

import com.adela.entities.Estudiante;

/**
 * Lo que un profesor ve al buscar estudiantes para su grupo: basta para
 * reconocerlo y agregarlo. Código, género y fecha de nacimiento no salen.
 */
public record EstudianteBusquedaDTO(String email, String nombre) {

    public static EstudianteBusquedaDTO from(Estudiante e) {
        return new EstudianteBusquedaDTO(e.getEmail(), e.getNombre());
    }
}
