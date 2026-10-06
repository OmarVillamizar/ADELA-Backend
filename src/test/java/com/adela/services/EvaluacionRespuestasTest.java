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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.dto.CategoriaResultadoDTO;
import com.adela.entities.Categoria;
import com.adela.entities.Cuestionario;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.OpcionRepository;
import com.adela.repositories.PreguntaRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Cubre las reglas que deciden si una resolución se acepta y cuánto puntúa.
 * Las usan grupos y cápsulas: si se relajan, cualquiera de los dos caminos
 * guarda respuestas incompletas o puntajes inflados.
 *
 * Cuestionario de prueba: pregunta 1 de única opción (ids 11 y 12), pregunta 2
 * de opción múltiple (ids 21 y 22). Categorías Visual (1) y Auditivo (2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluacionRespuestasTest {

    @Mock
    private OpcionRepository opcionRepository;

    @Mock
    private PreguntaRepository preguntaRepository;

    @InjectMocks
    private EvaluacionRespuestas evaluacion;

    private Cuestionario cuestionario;
    private Map<Long, Opcion> opciones;

    @BeforeEach
    void preparar() {
        cuestionario = new Cuestionario();
        cuestionario.setId(1L);
        Categoria visual = categoria(1L, "Visual");
        Categoria auditivo = categoria(2L, "Auditivo");
        cuestionario.getCategorias().add(visual);
        cuestionario.getCategorias().add(auditivo);

        Pregunta unica = pregunta(100L, 1, false, cuestionario);
        Pregunta multiple = pregunta(200L, 2, true, cuestionario);

        opciones = List.of(
                opcion(11L, unica, visual, 1d),
                opcion(12L, unica, auditivo, 1d),
                opcion(21L, multiple, visual, 2d),
                opcion(22L, multiple, auditivo, 3d))
                .stream().collect(Collectors.toMap(Opcion::getId, o -> o));

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
        Opcion ajena = opcion(99L, pregunta(900L, 1, false, otro), categoria(9L, "X"), 1d);
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
        assertEquals(3d, valor(evaluacion.puntuar(cuestionario, elegidas), "Visual"));
    }

    @Test
    @DisplayName("Puntúa por categoría e incluye las que quedan en cero")
    void puntuaPorCategoria() {
        List<CategoriaResultadoDTO> res = evaluacion.puntuar(cuestionario, List.of(opciones.get(11L)));

        assertEquals(2, res.size());
        assertEquals(1d, valor(res, "Visual"));
        assertEquals(0d, valor(res, "Auditivo"));
    }

    private static double valor(List<CategoriaResultadoDTO> res, String nombre) {
        return res.stream().filter(c -> c.getNombre().equals(nombre)).findFirst().orElseThrow().getValor();
    }

    private static Categoria categoria(Long id, String nombre) {
        Categoria c = new Categoria();
        c.setId(id);
        c.setNombre(nombre);
        c.setValorMinimo(0d);
        c.setValorMaximo(10d);
        return c;
    }

    private static Pregunta pregunta(Long id, int orden, boolean multiple, Cuestionario c) {
        Pregunta p = new Pregunta();
        p.setId(id);
        p.setOrden(orden);
        p.setOpcionMultiple(multiple);
        p.setCuestionario(c);
        return p;
    }

    private static Opcion opcion(Long id, Pregunta p, Categoria c, double valor) {
        Opcion o = new Opcion();
        o.setId(id);
        o.setPregunta(p);
        o.setCategoria(c);
        o.setValor(valor);
        return o;
    }
}
