package com.adela.services;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.adela.dto.EstiloResultadoDTO;
import com.adela.entities.Estilo;
import com.adela.entities.Cuestionario;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.OpcionRepository;
import com.adela.repositories.PreguntaRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Reglas para aceptar y puntuar una resolución, independientes de quién responde.
 * Las comparten las asignaciones de grupo y las cápsulas: tenerlas en un solo
 * sitio evita que un camino acepte respuestas que el otro rechazaría.
 */
@Component
@RequiredArgsConstructor
public class EvaluacionRespuestas {

    private final OpcionRepository opcionRepository;

    private final PreguntaRepository preguntaRepository;

    /**
     * Devuelve las opciones elegidas si forman una resolución válida del
     * cuestionario. Los ids repetidos se descartan: guardarlos dos veces sumaba la
     * misma opción dos veces al puntaje.
     */
    public List<Opcion> validarSeleccion(Cuestionario cuestionario, Collection<Long> opcionIds) {
        Set<Long> ids = new LinkedHashSet<>(opcionIds);
        Map<Long, Opcion> encontradas = opcionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Opcion::getId, Function.identity()));

        Map<Long, Pregunta> sinResponder = new TreeMap<>();
        for (Pregunta pregunta : preguntaRepository.findByCuestionario(cuestionario)) {
            if (pregunta.isObligatoria()) {
                sinResponder.put(pregunta.getId(), pregunta);
            }
        }

        Set<Long> respondidas = new LinkedHashSet<>();
        List<Opcion> opciones = new LinkedList<>();
        for (Long id : ids) {
            Opcion opcion = encontradas.get(id);
            if (opcion == null) {
                throw new EntityNotFoundException("No existe la opción " + id);
            }
            Pregunta pregunta = opcion.getPregunta();
            if (!pregunta.getCuestionario().getId().equals(cuestionario.getId())) {
                throw new AppException(ErrorCode.OPCION_INCONSISTENTE,
                        "La opción " + id + " no pertenece al cuestionario " + cuestionario.getId() + ".");
            }
            if (!respondidas.add(pregunta.getId()) && !pregunta.isOpcionMultiple()) {
                throw new AppException(ErrorCode.OPCION_DUPLICADA,
                        "La pregunta " + pregunta.getOrden() + " admite una sola respuesta y llegó más de una.");
            }
            sinResponder.remove(pregunta.getId());
            opciones.add(opcion);
        }

        if (!sinResponder.isEmpty()) {
            String faltan = sinResponder.values().stream()
                    .map(p -> String.valueOf(p.getOrden()))
                    .collect(Collectors.joining(", "));
            throw new AppException(ErrorCode.PREGUNTAS_SIN_RESPONDER,
                    "Faltan por responder las preguntas " + faltan + ".");
        }
        return opciones;
    }

    /**
     * Suma el valor de cada opción en su estilo. Incluye los estilos sin
     * puntos para que el resultado tenga siempre todas las dimensiones del
     * cuestionario.
     */
    public List<EstiloResultadoDTO> puntuar(Cuestionario cuestionario, Collection<Opcion> opciones) {
        Map<Long, EstiloResultadoDTO> porEstilo = new TreeMap<>();
        List<EstiloResultadoDTO> estilos = new LinkedList<>();

        for (Estilo estilo : cuestionario.getEstilos()) {
            EstiloResultadoDTO cr = new EstiloResultadoDTO();
            cr.setNombre(estilo.getNombre());
            cr.setValor(0d);
            cr.setValorMaximo(estilo.getValorMaximo());
            cr.setValorMinimo(estilo.getValorMinimo());
            porEstilo.put(estilo.getId(), cr);
            estilos.add(cr);
        }

        for (Opcion o : opciones) {
            EstiloResultadoDTO cr = porEstilo.get(o.getEstilo().getId());
            cr.setValor(cr.getValor() + o.getValor());
        }
        return estilos;
    }
}
