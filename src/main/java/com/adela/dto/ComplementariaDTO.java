package com.adela.dto;

import java.util.List;
import java.util.Map;

import com.adela.entities.OpcionComplementaria;
import com.adela.entities.PreguntaComplementaria;

/**
 * Vistas de la pregunta complementaria fuera del editor del administrador: la
 * pregunta para responderla, la respuesta en un reporte individual y el
 * conteo en un reporte agregado.
 */
public final class ComplementariaDTO {

    private ComplementariaDTO() {
    }

    /** Para responder: sin el resultado de cada opción, que se ve al elegir. */
    public record Pregunta(String titulo, String introduccion, String enunciado, String nota, List<Opcion> opciones) {
        public static Pregunta from(PreguntaComplementaria p) {
            return new Pregunta(p.getTitulo(), p.getIntroduccion(), p.getEnunciado(), p.getNota(),
                    p.getOpciones().stream().map(o -> new Opcion(o.getId(), o.getTexto(), o.getDescripcion()))
                            .toList());
        }
    }

    public record Opcion(Long id, String texto, String descripcion) {
    }

    /** Lo que eligió la persona, como se muestra en su reporte. */
    public record Respuesta(String titulo, Long opcionId, String resultado, String descripcion) {
        public static Respuesta from(OpcionComplementaria o) {
            return o == null ? null
                    : new Respuesta(o.getPregunta().getTitulo(), o.getId(), o.getResultado(),
                            o.getResultadoDescripcion());
        }
    }

    /** Cuerpo de la petición que la responde. */
    public record Eleccion(Long opcionId) {
    }

    /** Personas por resultado, en el orden de las opciones, más "Sin declarar". */
    public record Conteo(String titulo, Map<String, Long> respuestas) {
    }
}
