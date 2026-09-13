package com.adela.dto;

import java.util.Comparator;
import java.util.List;

import com.adela.entities.Categoria;
import com.adela.entities.Cuestionario;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;

/**
 * Cuestionario tal y como se presenta para responderlo.
 *
 * Deliberadamente NO expone Opcion.valor ni la categoría de cada opción: el
 * endpoint devolvía la entidad completa, así que el estudiante recibía el peso
 * numérico de cada respuesta antes de contestar y podía construir el perfil de
 * aprendizaje que quisiera, lo que invalida el instrumento CHAEA.
 *
 * Sí expone el nombre de las categorías, que la vista de cuestionarios usa para
 * listar los estilos de aprendizaje. Eso no es el baremo: no dice qué opción
 * puntúa en qué categoría ni con cuánto peso, que era la fuga. Los valorMinimo y
 * valorMaximo de Categoria se quedan fuera.
 *
 * Si en algún momento el administrador necesita revisar el baremo, debe hacerse
 * en un endpoint aparte restringido a ese rol, no ampliando este DTO.
 */
public record CuestionarioParaResponderDTO(Long id, String nombre, String descripcion, String autor, String version,
        String siglas, List<PreguntaResponderDTO> preguntas, List<CategoriaResponderDTO> categorias) {

    public record PreguntaResponderDTO(Long id, String pregunta, int orden, boolean opcionMultiple,
            List<OpcionResponderDTO> opciones) {
    }

    public record OpcionResponderDTO(Long id, String respuesta, int orden) {
    }

    public record CategoriaResponderDTO(String nombre) {
    }

    public static CuestionarioParaResponderDTO from(Cuestionario c) {
        List<PreguntaResponderDTO> preguntas = c.getPreguntas().stream()
                .sorted(Comparator.comparingInt(Pregunta::getOrden)).map(p -> new PreguntaResponderDTO(p.getId(),
                        p.getPregunta(), p.getOrden(), p.isOpcionMultiple(), opcionesDe(p)))
                .toList();

        List<CategoriaResponderDTO> categorias = c.getCategorias().stream().map(Categoria::getNombre)
                .sorted(Comparator.nullsLast(Comparator.naturalOrder())).map(CategoriaResponderDTO::new).toList();
        return new CuestionarioParaResponderDTO(c.getId(), c.getNombre(), c.getDescripcion(), c.getAutor(),
                c.getVersion(), c.getSiglas(), preguntas, categorias);
    }

    private static List<OpcionResponderDTO> opcionesDe(Pregunta p) {
        return p.getOpciones().stream().sorted(Comparator.comparingInt(Opcion::getOrden))
                .map(o -> new OpcionResponderDTO(o.getId(), o.getRespuesta(), o.getOrden())).toList();
    }
}
