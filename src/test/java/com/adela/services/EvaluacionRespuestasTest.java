package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.calificacion.FormatoItem;
import com.adela.dto.EstiloResultadoDTO;
import com.adela.entities.Estilo;
import com.adela.entities.Cuestionario;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.BandaInterpretacionRepository;
import com.adela.repositories.EscalonRelativoRepository;
import com.adela.repositories.OpcionRepository;
import com.adela.repositories.PreguntaRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Cubre las reglas que deciden si una resolución se acepta y cuánto puntúa.
 * Las usan grupos y cápsulas: si se relajan, cualquiera de los dos caminos
 * guarda respuestas incompletas o puntajes inflados.
 *
 * Cuestionario de prueba: pregunta 1 de única opción (ids 11 y 12), pregunta 2
 * de opción múltiple (ids 21 y 22). Estilos Visual (1) y Auditivo (2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluacionRespuestasTest {

    @Mock
    private OpcionRepository opcionRepository;

    @Mock
    private PreguntaRepository preguntaRepository;

    @Mock
    private BandaInterpretacionRepository bandaRepository;

    @Mock
    private EscalonRelativoRepository escalonRepository;

    private EvaluacionRespuestas evaluacion;

    private Cuestionario cuestionario;
    private Map<Long, Opcion> opciones;

    @BeforeEach
    void preparar() {
        cuestionario = new Cuestionario();
        cuestionario.setId(1L);
        Estilo visual = estilo(1L, "Visual");
        Estilo auditivo = estilo(2L, "Auditivo");
        cuestionario.getEstilos().add(visual);
        cuestionario.getEstilos().add(auditivo);

        Pregunta unica = pregunta(100L, 1, false, cuestionario);
        Pregunta multiple = pregunta(200L, 2, true, cuestionario);

        opciones = List.of(
                opcion(11L, unica, visual, 1d),
                opcion(12L, unica, auditivo, 1d),
                opcion(21L, multiple, visual, 2d),
                opcion(22L, multiple, auditivo, 3d))
                .stream().collect(Collectors.toMap(Opcion::getId, o -> o));
        opciones.values().forEach(o -> o.getPregunta().getOpciones().add(o));
        cuestionario.getPreguntas().add(unica);
        cuestionario.getPreguntas().add(multiple);
        evaluacion = new EvaluacionRespuestas(opcionRepository, preguntaRepository,
                new CalificacionService(bandaRepository, escalonRepository));

        when(preguntaRepository.findByCuestionario(cuestionario)).thenReturn(List.of(unica, multiple));
        when(opcionRepository.findAllById(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().filter(opciones::containsKey).map(opciones::get).toList();
        });
    }

    @Test
    @DisplayName("Acepta una resolución completa")
    void aceptaResolucionCompleta() {
        assertEquals(3, evaluacion.validarSeleccion(cuestionario, List.of(11L, 21L, 22L)).size());
    }

    @Test
    @DisplayName("Rechaza una opción de otro cuestionario")
    void rechazaOpcionAjena() {
        Cuestionario otro = new Cuestionario();
        otro.setId(2L);
        Opcion ajena = opcion(99L, pregunta(900L, 1, false, otro), estilo(9L, "X"), 1d);
        opciones = new HashMap<>(opciones);
        opciones.put(99L, ajena);

        AppException e = assertThrows(AppException.class,
                () -> evaluacion.validarSeleccion(cuestionario, List.of(11L, 99L)));
        assertEquals(ErrorCode.OPCION_INCONSISTENTE, e.getCode());
    }

    @Test
    @DisplayName("Rechaza dos opciones en una pregunta de única respuesta")
    void rechazaDosOpcionesEnUnica() {
        AppException e = assertThrows(AppException.class,
                () -> evaluacion.validarSeleccion(cuestionario, List.of(11L, 12L)));
        assertEquals(ErrorCode.OPCION_DUPLICADA, e.getCode());
    }

    @Test
    @DisplayName("Rechaza dejar sin responder una pregunta de única respuesta")
    void rechazaPreguntaSinResponder() {
        AppException e = assertThrows(AppException.class,
                () -> evaluacion.validarSeleccion(cuestionario, List.of(21L)));
        assertEquals(ErrorCode.PREGUNTAS_SIN_RESPONDER, e.getCode());
    }

    @Test
    @DisplayName("Acepta en blanco una pregunta de única respuesta marcada como no obligatoria")
    void aceptaUnicaNoObligatoriaEnBlanco() {
        opciones.get(11L).getPregunta().setObligatoria(false);

        assertEquals(1, evaluacion.validarSeleccion(cuestionario, List.of(21L)).size());
    }

    @Test
    @DisplayName("Rechaza en blanco una pregunta de opción múltiple marcada como obligatoria")
    void rechazaMultipleObligatoriaEnBlanco() {
        opciones.get(21L).getPregunta().setObligatoria(true);

        AppException e = assertThrows(AppException.class,
                () -> evaluacion.validarSeleccion(cuestionario, List.of(11L)));
        assertEquals(ErrorCode.PREGUNTAS_SIN_RESPONDER, e.getCode());
    }

    @Test
    @DisplayName("Rechaza una opción que no existe")
    void rechazaOpcionInexistente() {
        assertThrows(EntityNotFoundException.class,
                () -> evaluacion.validarSeleccion(cuestionario, List.of(11L, 777L)));
    }

    @Test
    @DisplayName("Un id repetido cuenta una sola vez")
    void idRepetidoCuentaUnaVez() {
        List<Opcion> elegidas = evaluacion.validarSeleccion(cuestionario, List.of(11L, 21L, 21L));

        assertEquals(2, elegidas.size());
        assertEquals(3d, estilo(evaluacion.puntuar(cuestionario, elegidas).estilos(), "Visual").getValor());
    }

    /**
     * Visual: la única aporta 0 o 1 (la otra opción es de Auditivo) y la múltiple
     * opcional 0 o 2, así que su rango es [0, 3] y 1 punto es el 33,3 %.
     */
    @Test
    @DisplayName("Puntúa por estilo con su rango teórico e incluye los que quedan en cero")
    void puntuaPorEstilo() {
        EvaluacionRespuestas.Puntuacion p = evaluacion.puntuar(cuestionario, List.of(opciones.get(11L)));

        assertEquals(2, p.estilos().size());
        EstiloResultadoDTO visual = estilo(p.estilos(), "Visual");
        assertEquals(1d, visual.getValor());
        assertEquals(0d, visual.getRangoMin());
        assertEquals(3d, visual.getRangoMax());
        assertEquals(100d / 3, visual.getPomp(), 1e-9);
        assertEquals(0d, estilo(p.estilos(), "Auditivo").getValor());
        assertEquals("2.0.0", p.calificacion().versionMotor());
    }

    private static EstiloResultadoDTO estilo(List<EstiloResultadoDTO> res, String nombre) {
        return res.stream().filter(c -> c.getNombre().equals(nombre)).findFirst().orElseThrow();
    }

    private static Estilo estilo(Long id, String nombre) {
        Estilo c = new Estilo();
        c.setId(id);
        c.setNombre(nombre);
        return c;
    }

    private static Pregunta pregunta(Long id, int orden, boolean multiple, Cuestionario c) {
        Pregunta p = new Pregunta();
        p.setId(id);
        p.setOrden(orden);
        p.setFormato(multiple ? FormatoItem.MULTIPLE : FormatoItem.UNICA);
        p.setObligatoria(!multiple);
        p.setCuestionario(c);
        return p;
    }

    private static Opcion opcion(Long id, Pregunta p, Estilo c, double valor) {
        Opcion o = new Opcion();
        o.setId(id);
        o.setPregunta(p);
        o.setPesos(Map.of(c.getId(), valor));
        return o;
    }
}
