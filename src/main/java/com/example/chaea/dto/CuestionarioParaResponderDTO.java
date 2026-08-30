package com.example.chaea.dto;

import java.util.Comparator;
import java.util.List;

import com.example.chaea.entities.Cuestionario;
import com.example.chaea.entities.Opcion;
import com.example.chaea.entities.Pregunta;

/**
 * Cuestionario tal y como se presenta para responderlo.
 *
 * Deliberadamente NO expone Opcion.valor ni la categoría de cada opción: el
 * endpoint devolvía la entidad completa, así que el estudiante recibía el peso
 * numérico de cada respuesta antes de contestar y podía construir el perfil de
 * aprendizaje que quisiera, lo que invalida el instrumento CHAEA.
 *
 * Si en algún momento el administrador necesita revisar el baremo, debe hacerse
 * en un endpoint aparte restringido a ese rol, no ampliando este DTO.
 */
public record CuestionarioParaResponderDTO(Long id, String nombre, String descripcion, String autor, String version,
        String siglas, List<PreguntaResponderDTO> preguntas) {

    public record PreguntaResponderDTO(Long id, String pregunta, int orden, boolean opcionMultiple,
            List<OpcionResponderDTO> opciones) {
    }

    public record OpcionResponderDTO(Long id, String respuesta, int orden) {
    }

    public static CuestionarioParaResponderDTO from(Cuestionario c) {
        List<PreguntaResponderDTO> preguntas = c.getPreguntas().stream()
                .sorted(Comparator.comparingInt(Pregunta::getOrden)).map(p -> new PreguntaResponderDTO(p.getId(),
                        p.getPregunta(), p.getOrden(), p.isOpcionMultiple(), opcionesDe(p)))
                .toList();

        return new CuestionarioParaResponderDTO(c.getId(), c.getNombre(), c.getDescripcion(), c.getAutor(),
                c.getVersion(), c.getSiglas(), preguntas);
    }

    private static List<OpcionResponderDTO> opcionesDe(Pregunta p) {
        return p.getOpciones().stream().sorted(Comparator.comparingInt(Opcion::getOrden))
                .map(o -> new OpcionResponderDTO(o.getId(), o.getRespuesta(), o.getOrden())).toList();
    }
}
