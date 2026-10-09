package com.adela.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.adela.calificacion.ClaveInstrumento;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.FormatoItem;
import com.adela.calificacion.RespuestaItem;
import com.adela.calificacion.ResultadoInstrumento;
import com.adela.calificacion.TipoEstilo;
import com.adela.calificacion.ValidadorRespuesta;
import com.adela.dto.CalificacionDTO;
import com.adela.dto.ComplementariaDTO;
import com.adela.dto.EstiloResultadoDTO;
import com.adela.dto.PreguntaResueltaDTO;
import com.adela.dto.PreguntaResueltaDTO.RespuestaElegidaDTO;
import com.adela.entities.Cuestionario;
import com.adela.entities.Opcion;
import com.adela.entities.OpcionComplementaria;
import com.adela.entities.PreguntaComplementaria;
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

    private final CalificacionService calificacionService;

    /**
     * Devuelve cada opción elegida con su cantidad si forman una resolución válida
     * del cuestionario. opcionIds marca opciones con cantidad 1 (única y
     * múltiple); cantidades lleva el rango (jerarquía) o los puntos (reparto). Los
     * ids repetidos en la lista se descartan: guardarlos dos veces sumaba la misma
     * opción dos veces al puntaje.
     *
     * Cada pregunta se valida con las reglas del motor (ValidadorRespuesta), las
     * mismas que fijan su rango teórico.
     */
    public Map<Opcion, Double> validarSeleccion(Cuestionario cuestionario, Collection<Long> opcionIds,
            Map<Long, Double> cantidades) {
        Map<Long, Double> pedidas = new LinkedHashMap<>();
        if (opcionIds != null) {
            opcionIds.forEach(id -> pedidas.put(id, 1.0));
        }
        if (cantidades != null) {
            for (Map.Entry<Long, Double> e : cantidades.entrySet()) {
                if (pedidas.putIfAbsent(e.getKey(), e.getValue()) != null) {
                    throw new AppException(ErrorCode.OPCION_DUPLICADA,
                            "La opción " + e.getKey() + " llegó marcada y también con cantidad.");
                }
            }
        }
        Map<Long, Opcion> encontradas = opcionRepository.findAllById(pedidas.keySet()).stream()
                .collect(Collectors.toMap(Opcion::getId, Function.identity()));

        Map<Opcion, Double> elegidas = new LinkedHashMap<>();
        Map<Long, Map<Long, Double>> porPregunta = new HashMap<>();
        for (Map.Entry<Long, Double> e : pedidas.entrySet()) {
            Long id = e.getKey();
            Opcion opcion = encontradas.get(id);
            if (opcion == null) {
                throw new EntityNotFoundException("No existe la opción " + id);
            }
            Pregunta pregunta = opcion.getPregunta();
            if (!pregunta.getCuestionario().getId().equals(cuestionario.getId())) {
                throw new AppException(ErrorCode.OPCION_INCONSISTENTE,
                        "La opción " + id + " no pertenece al cuestionario " + cuestionario.getId() + ".");
            }
            porPregunta.computeIfAbsent(pregunta.getId(), k -> new HashMap<>()).put(id, e.getValue());
            elegidas.put(opcion, e.getValue());
        }

        List<Integer> sinResponder = new ArrayList<>();
        Map<String, String> invalidas = new LinkedHashMap<>();
        List<Pregunta> preguntas = preguntaRepository.findByCuestionario(cuestionario).stream()
                .sorted(Comparator.comparingInt(Pregunta::getOrden)).toList();
        for (Pregunta pregunta : preguntas) {
            Map<Long, Double> respuesta = porPregunta.getOrDefault(pregunta.getId(), Map.of());
            if (respuesta.isEmpty()) {
                if (pregunta.isObligatoria()) {
                    sinResponder.add(pregunta.getOrden());
                }
                continue;
            }
            List<String> errores = ValidadorRespuesta.errores(calificacionService.item(pregunta),
                    new RespuestaItem(pregunta.getId(), respuesta));
            if (!errores.isEmpty()) {
                invalidas.put("pregunta_" + pregunta.getOrden(),
                        "Pregunta " + pregunta.getOrden() + ": " + String.join("; ", errores) + ".");
            }
        }

        if (!sinResponder.isEmpty()) {
            String faltan = sinResponder.stream().map(String::valueOf).collect(Collectors.joining(", "));
            throw new AppException(ErrorCode.PREGUNTAS_SIN_RESPONDER,
                    "Faltan por responder las preguntas " + faltan + ".");
        }
        if (!invalidas.isEmpty()) {
            throw new AppException(ErrorCode.VALIDACION, "Hay respuestas inválidas.", invalidas);
        }
        return elegidas;
    }

    /**
     * Respuestas para mostrar, una entrada por pregunta en su orden (las no
     * respondidas con la lista vacía). En jerarquía y reparto, de mayor a menor
     * cantidad, que es como se leen.
     */
    public static List<PreguntaResueltaDTO> preguntasResueltas(Cuestionario c, Map<Long, Double> cantidadPorOpcion) {
        return c.getPreguntas().stream().sorted(Comparator.comparingInt(Pregunta::getOrden)).map(p -> {
            Comparator<Opcion> orden = Comparator.comparingInt(Opcion::getOrden);
            if (p.getFormato() == FormatoItem.JERARQUIA || p.getFormato() == FormatoItem.REPARTO) {
                orden = Comparator.comparing((Opcion o) -> cantidadPorOpcion.get(o.getId())).reversed()
                        .thenComparing(orden);
            }
            PreguntaResueltaDTO pr = new PreguntaResueltaDTO();
            pr.setPregunta(p.getPregunta());
            pr.setOrden(p.getOrden());
            pr.setFormato(p.getFormato());
            pr.setRespuestas(p.getOpciones().stream().filter(o -> cantidadPorOpcion.containsKey(o.getId()))
                    .sorted(orden)
                    .map(o -> new RespuestaElegidaDTO(o.getRespuesta(), cantidadPorOpcion.get(o.getId())))
                    .toList());
            return pr;
        }).toList();
    }

    public static final String SIN_DECLARAR = "Sin declarar";

    /** Esquemas que destacan varios estilos: los únicos donde "todos destacados" ocurre. */
    public static boolean admiteComplementaria(EsquemaInterpretacion esquema) {
        return esquema == EsquemaInterpretacion.RELATIVO || esquema == EsquemaInterpretacion.RELATIVO_ESCALONADO;
    }

    /**
     * La pregunta complementaria que le toca a un resultado, o null: el
     * cuestionario la define y el perfil destaca todos los estilos primarios.
     */
    public static PreguntaComplementaria complementaria(Cuestionario c, List<EstiloResultadoDTO> estilos) {
        PreguntaComplementaria p = c.getPreguntaComplementaria();
        if (p == null || !admiteComplementaria(c.getEsquemaInterpretacion())) {
            return null;
        }
        List<EstiloResultadoDTO> primarios = estilos.stream().filter(e -> e.getTipo() == TipoEstilo.PRIMARIO)
                .toList();
        boolean todos = primarios.size() >= 2
                && primarios.stream().allMatch(e -> Boolean.TRUE.equals(e.getDominante()));
        return todos ? p : null;
    }

    /**
     * La opción que se va a guardar como respuesta, si procede: la pregunta le
     * toca a este resultado, aún no se respondió (una sola vez) y la opción es
     * de esta pregunta.
     */
    public static OpcionComplementaria elegir(Cuestionario c, List<EstiloResultadoDTO> estilos,
            OpcionComplementaria actual, Long opcionId) {
        if (actual != null) {
            throw new AppException(ErrorCode.COMPLEMENTARIA_YA_RESPONDIDA, "Esta pregunta ya fue respondida.");
        }
        PreguntaComplementaria p = complementaria(c, estilos);
        if (p == null) {
            throw new AppException(ErrorCode.COMPLEMENTARIA_NO_APLICA,
                    "Esta pregunta solo aplica cuando el perfil destaca todos los estilos.");
        }
        return p.getOpciones().stream().filter(o -> o.getId().equals(opcionId)).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.VALIDACION, "Elige una de las opciones de la pregunta."));
    }

    /**
     * Conteo de un reporte agregado entre las resoluciones a las que les toca la
     * pregunta: cada resultado en el orden de las opciones (también los que nadie
     * eligió) y al final "Sin declarar". null si no le toca a nadie.
     * elegidas va en paralelo a resultados.
     */
    public static ComplementariaDTO.Conteo conteo(Cuestionario c, List<ResultadoInstrumento> resultados,
            List<OpcionComplementaria> elegidas) {
        PreguntaComplementaria p = c.getPreguntaComplementaria();
        if (p == null) {
            return null;
        }
        Map<String, Long> respuestas = new LinkedHashMap<>();
        p.getOpciones().forEach(o -> respuestas.put(o.getResultado(), 0L));
        long sinDeclarar = 0;
        boolean alguna = false;
        for (int i = 0; i < resultados.size(); i++) {
            if (complementaria(c, resultados.get(i).estilos().stream().map(EstiloResultadoDTO::de).toList()) == null) {
                continue;
            }
            alguna = true;
            OpcionComplementaria elegida = elegidas.get(i);
            if (elegida == null) {
                sinDeclarar++;
            } else {
                respuestas.merge(elegida.getResultado(), 1L, Long::sum);
            }
        }
        if (!alguna) {
            return null;
        }
        respuestas.put(SIN_DECLARAR, sinDeclarar);
        return new ComplementariaDTO.Conteo(p.getTitulo(), respuestas);
    }

    public record Puntuacion(List<EstiloResultadoDTO> estilos, CalificacionDTO calificacion) {
    }

    /**
     * Califica con el motor: puntaje directo, rango, % del máximo, nivel y
     * perfil. Incluye los estilos sin puntos para que el resultado tenga siempre
     * todas las dimensiones del cuestionario. cantidadPorOpcion: id de la opción
     * elegida -> cantidad.
     */
    public Puntuacion puntuar(Cuestionario cuestionario, Map<Long, Double> cantidadPorOpcion) {
        ClaveInstrumento clave = calificacionService.clave(cuestionario);
        ResultadoInstrumento r = calificacionService.calificar(cuestionario, clave, cantidadPorOpcion);
        return new Puntuacion(r.estilos().stream().map(EstiloResultadoDTO::de).toList(),
                CalificacionDTO.individual(cuestionario, r, clave));
    }
}
