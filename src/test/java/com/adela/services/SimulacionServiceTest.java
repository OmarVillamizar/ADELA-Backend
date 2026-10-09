package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.calificacion.FormatoItem;
import com.adela.calificacion.ItemClave;
import com.adela.calificacion.OpcionClave;
import com.adela.calificacion.RespuestaItem;
import com.adela.calificacion.ValidadorRespuesta;
import com.adela.dto.RespuestaCuestionarioDTO;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estudiante;
import com.adela.entities.Grupo;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.entities.Profesor;
import com.adela.entities.ResultadoCuestionario;
import com.adela.exceptions.AppException;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.EstudianteRepository;
import com.adela.repositories.GrupoRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;

/**
 * Las respuestas simuladas tienen que pasar la misma validación que las de un
 * estudiante: si no, responderCuestionario las rechaza y la simulación entera
 * se revierte. Se prueba un ítem de cada formato con sus restricciones.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimulacionServiceTest {

    @Mock
    private GrupoRepository grupoRepository;

    @Mock
    private CuestionarioRepository cuestionarioRepository;

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private ResultadoCuestionarioRepository resultadoCuestionarioRepository;

    @Mock
    private ResultadoCuestionarioService resultadoCuestionarioService;

    @Mock
    private EvaluacionRespuestas evaluacionRespuestas;

    private SimulacionService simulacion;

    private Cuestionario cuestionario;

    private final Profesor profesor = new Profesor();

    @BeforeEach
    void preparar() {
        simulacion = new SimulacionService(grupoRepository, cuestionarioRepository, estudianteRepository,
                resultadoCuestionarioRepository, resultadoCuestionarioService, evaluacionRespuestas);

        cuestionario = new Cuestionario();
        cuestionario.setId(1L);
        pregunta(1, FormatoItem.UNICA, 3, p -> {
        });
        pregunta(2, FormatoItem.MULTIPLE, 4, p -> {
            p.setMinSelecciones(1);
            p.setMaxSelecciones(2);
        });
        pregunta(3, FormatoItem.JERARQUIA, 4, p -> {
        });
        pregunta(4, FormatoItem.REPARTO, 3, p -> p.setPuntosRepartir(10));

        when(grupoRepository.findByProfesorAndId(any(), anyInt())).thenReturn(Optional.of(new Grupo()));
        when(cuestionarioRepository.findById(1L)).thenReturn(Optional.of(cuestionario));
        when(resultadoCuestionarioRepository.findByGrupoAndCuestionario(any(), any())).thenReturn(List.of());
        when(estudianteRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        AtomicLong ids = new AtomicLong();
        when(resultadoCuestionarioRepository.save(any())).thenAnswer(i -> {
            ResultadoCuestionario rc = i.getArgument(0);
            rc.setId(ids.incrementAndGet());
            return rc;
        });
    }

    private void pregunta(int orden, FormatoItem formato, int k, java.util.function.Consumer<Pregunta> ajuste) {
        Pregunta p = new Pregunta();
        p.setId((long) orden);
        p.setOrden(orden);
        p.setFormato(formato);
        p.setCuestionario(cuestionario);
        ajuste.accept(p);
        for (int i = 1; i <= k; i++) {
            Opcion o = new Opcion();
            o.setId(orden * 10L + i);
            o.setOrden(i);
            o.setPregunta(p);
            o.setPesos(new HashMap<>(Map.of((long) (i % 2 + 1), 1.0)));
            p.getOpciones().add(o);
        }
        cuestionario.getPreguntas().add(p);
    }

    @Test
    void cadaEstudianteSimuladoEnviaUnaResolucionValida() {
        int creados = simulacion.simular(7, 1L, 50, profesor);

        assertEquals(50, creados);
        ArgumentCaptor<RespuestaCuestionarioDTO> enviadas = ArgumentCaptor.forClass(RespuestaCuestionarioDTO.class);
        verify(resultadoCuestionarioService, times(50)).responderCuestionario(enviadas.capture(),
                any(Estudiante.class));
        for (RespuestaCuestionarioDTO r : enviadas.getAllValues()) {
            Map<Long, Double> todas = new HashMap<>(r.getCantidades());
            r.getOpcionesSeleccionadasId().forEach(id -> todas.put(id, 1.0));
            for (Pregunta p : cuestionario.getPreguntas()) {
                Map<Long, Double> propias = new HashMap<>();
                p.getOpciones().forEach(o -> {
                    if (todas.containsKey(o.getId()))
                        propias.put(o.getId(), todas.get(o.getId()));
                });
                List<String> errores = ValidadorRespuesta.errores(item(p), new RespuestaItem(p.getId(), propias));
                assertTrue(errores.isEmpty(), "Pregunta " + p.getOrden() + ": " + errores);
            }
        }
    }

    @Test
    void rechazaCantidadFueraDeRango() {
        assertThrows(AppException.class, () -> simulacion.simular(7, 1L, 0, profesor));
        assertThrows(AppException.class,
                () -> simulacion.simular(7, 1L, SimulacionService.MAX_ESTUDIANTES + 1, profesor));
    }

    private static ItemClave item(Pregunta p) {
        List<OpcionClave> opciones = p.getOpciones().stream().sorted(Comparator.comparingInt(Opcion::getOrden))
                .map(o -> new OpcionClave(o.getId(), o.getPesos())).toList();
        return new ItemClave(p.getId(), p.getFormato(), p.isObligatoria(), p.getMinSelecciones(),
                p.getMaxSelecciones(), p.getPuntosRepartir(), opciones);
    }
}
