package com.example.chaea.dto;

import java.util.Comparator;
import java.util.List;

import com.example.chaea.entities.Grupo;

/**
 * Vista completa de un grupo. Sustituye a devolver la entidad: el contrato deja
 * de estar atado al modelo y la respuesta no arrastra las relaciones LAZY que
 * solo se serializaban porque open-in-view mantenía la sesión abierta durante el
 * render.
 */
public record GrupoDetalleDTO(int id, String nombre, String profesorEmail, String profesorNombre,
        List<EstudianteDTO> estudiantes) {

    public static GrupoDetalleDTO from(Grupo g) {
        List<EstudianteDTO> estudiantes = g.getEstudiantes() == null ? List.of()
                : g.getEstudiantes().stream().map(EstudianteDTO::from)
                        .sorted(Comparator.comparing(EstudianteDTO::getNombre,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
        return new GrupoDetalleDTO(g.getId(), g.getNombre(), g.getProfesor().getEmail(), g.getProfesor().getNombre(),
                estudiantes);
    }
}
