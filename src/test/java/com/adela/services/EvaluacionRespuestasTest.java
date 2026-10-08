package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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

import com.adela.calificacion.EscalaBanda;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.FormatoItem;
import com.adela.calificacion.TipoEstilo;
import com.adela.dto.EstiloResultadoDTO;
import com.adela.dto.PreguntaResueltaDTO;
import com.adela.dto.PreguntaResueltaDTO.RespuestaElegidaDTO;
import com.adela.entities.BandaInterpretacion;
import com.adela.entities.Estilo;
import com.adela.entities.Cuestionario;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.BandaInterpretacionRepository;
import com.adela.repositories.EscalonRelativoRepository;
import com.adela.repositories.OpcionRepository;
import com.adela.repositories.PlanoCuadrantesRepository;
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

    @Mock
    private PlanoCuadrantesRepository planoRepository;

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
                new CalificacionService(bandaRepository, escalonRepository, planoRepository));

        when(preguntaRepository.findByCuestionario(cuestionario)).thenReturn(List.of(unica, multiple));
        when(opcionRepository.findAllById(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().filter(opciones::containsKey).map(opciones::get).toList();
        });
    }

    @Test
    @DisplayName("Acepta una resolución completa")
    void aceptaResolucionCompleta() {
        assertEquals(3, evaluacion.validarSeleccion(cuestionario, List.of(11L, 21L, 22L), null).size());
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
                () -> evaluacion.validarSeleccion(cuestionario, List.of(11L, 99L), null));
        assertEquals(ErrorCode.OPCION_INCONSISTENTE, e.getCode());
    }

    @Test
    @DisplayName("Rechaza dos opciones en una pregunta de única respuesta, señalando la pregunta")
    void rechazaDosOpcionesEnUnica() {
        AppException e = assertThrows(AppException.class,
                () -> evaluacion.validarSeleccion(cuestionario, List.of(11L, 12L), null));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
        assertTrue(e.getFields().get("pregunta_1").startsWith("Pregunta 1: "));
    }

    @Test
    @DisplayName("Rechaza dejar sin responder una pregunta de única respuesta")
    void rechazaPreguntaSinResponder() {
        AppException e = assertThrows(AppException.class,
                () -> evaluacion.validarSeleccion(cuestionario, List.of(21L), null));
        assertEquals(ErrorCode.PREGUNTAS_SIN_RESPONDER, e.getCode());
    }

    @Test
    @DisplayName("Acepta en blanco una pregunta de única respuesta marcada como no obligatoria")
    void aceptaUnicaNoObligatoriaEnBlanco() {
        opciones.get(11L).getPregunta().setObligatoria(false);

        assertEquals(1, evaluacion.validarSeleccion(cuestionario, List.of(21L), null).size());
    }

    @Test
    @DisplayName("Rechaza en blanco una pregunta de opción múltiple marcada como obligatoria")
    void rechazaMultipleObligatoriaEnBlanco() {
        opciones.get(21L).getPregunta().setObligatoria(true);

        AppException e = assertThrows(AppException.class,
                () -> evaluacion.validarSeleccion(cuestionario, List.of(11L), null));
        assertEquals(ErrorCode.PREGUNTAS_SIN_RESPONDER, e.getCode());
    }

    @Test
    @DisplayName("Rechaza una opción que no existe")
    void rechazaOpcionInexistente() {
        assertThrows(EntityNotFoundException.class,
                () -> evaluacion.validarSeleccion(cuestionario, List.of(11L, 777L), null));
    }

    @Test
    @DisplayName("Un id repetido cuenta una sola vez")
    void idRepetidoCuentaUnaVez() {
        Map<Opcion, Double> elegidas = evaluacion.validarSeleccion(cuestionario, List.of(11L, 21L, 21L), null);

        assertEquals(2, elegidas.size());
        assertEquals(3d, estilo(evaluacion.puntuar(cuestionario, porId(elegidas)).estilos(), "Visual").getValor());
    }

    /**
     * Visual: la única aporta 0 o 1 (la otra opción es de Auditivo) y la múltiple
     * opcional 0 o 2, así que su rango es [0, 3] y 1 punto es el 33,3 %.
     */
    @Test
    @DisplayName("Puntúa por estilo con su rango teórico e incluye los que quedan en cero")
    void puntuaPorEstilo() {
        EvaluacionRespuestas.Puntuacion p = evaluacion.puntuar(cuestionario, Map.of(11L, 1d));

        assertEquals(2, p.estilos().size());
        EstiloResultadoDTO visual = estilo(p.estilos(), "Visual");
        assertEquals(1d, visual.getValor());
        assertEquals(0d, visual.getRangoMin());
        assertEquals(3d, visual.getRangoMax());
        assertEquals(100d / 3, visual.getPomp(), 1e-9);
        assertEquals(0d, estilo(p.estilos(), "Auditivo").getValor());
        assertEquals("2.0.0", p.calificacion().versionMotor());
    }

    @Test
    @DisplayName("Una opción marcada en la lista y también con cantidad es OPCION_DUPLICADA")
    void opcionEnListaYConCantidad() {
        AppException e = assertThrows(AppException.class,
                () -> evaluacion.validarSeleccion(cuestionario, List.of(11L, 21L), Map.of(11L, 1d)));
        assertEquals(ErrorCode.OPCION_DUPLICADA, e.getCode());
    }

    /**
     * Kolb (T05): 12 ítems de jerarquía; la opción j de cada ítem suma 1 al modo
     * j (CE, RO, AC, AE). Compuestos AC-CE y AE-RO.
     */
    private Cuestionario kolb() {
        Cuestionario k = new Cuestionario();
        k.setId(3L);
        List<Estilo> modos = List.of(estilo(31L, "CE"), estilo(32L, "RO"), estilo(33L, "AC"), estilo(34L, "AE"));
        Estilo acce = estilo(35L, "AC-CE");
        acce.setTipo(TipoEstilo.COMPUESTO);
        acce.setCoeficientes(Map.of(33L, 1d, 31L, -1d));
        Estilo aero = estilo(36L, "AE-RO");
        aero.setTipo(TipoEstilo.COMPUESTO);
        aero.setCoeficientes(Map.of(34L, 1d, 32L, -1d));
        k.getEstilos().addAll(modos);
        k.getEstilos().addAll(List.of(acce, aero));

        opciones = new HashMap<>(opciones);
        List<Pregunta> preguntas = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            Pregunta p = pregunta(3000L + i, i, false, k);
            p.setFormato(FormatoItem.JERARQUIA);
            for (int j = 0; j < 4; j++) {
                Opcion o = opcion(opcionKolb(i, j), p, modos.get(j), 1d);
                o.setOrden(j + 1);
                o.setRespuesta(modos.get(j).getNombre());
                p.getOpciones().add(o);
                opciones.put(o.getId(), o);
            }
            k.getPreguntas().add(p);
            preguntas.add(p);
        }
        when(preguntaRepository.findByCuestionario(k)).thenReturn(preguntas);
        return k;
    }

    private static long opcionKolb(int item, int modo) {
        return 30000L + item * 10 + modo;
    }

    /** El mismo rango a cada modo en los 12 ítems. */
    private static Map<Long, Double> rangosKolb(int ce, int ro, int ac, int ae) {
        Map<Long, Double> m = new HashMap<>();
        for (int i = 1; i <= 12; i++) {
            m.put(opcionKolb(i, 0), (double) ce);
            m.put(opcionKolb(i, 1), (double) ro);
            m.put(opcionKolb(i, 2), (double) ac);
            m.put(opcionKolb(i, 3), (double) ae);
        }
        return m;
    }

    @Test
    @DisplayName("T05: Kolb con AC=4 y CE=1 en todo da AC 48, CE 12 y AC-CE 36 en [-36, 36]")
    void kolbT05() {
        Cuestionario k = kolb();
        Map<Long, Double> rangos = rangosKolb(1, 3, 4, 2);

        Map<Opcion, Double> elegidas = evaluacion.validarSeleccion(k, List.of(), rangos);
        assertEquals(48, elegidas.size());

        EvaluacionRespuestas.Puntuacion p = evaluacion.puntuar(k, porId(elegidas));
        assertEquals(48d, estilo(p.estilos(), "AC").getValor());
        assertEquals(12d, estilo(p.estilos(), "CE").getValor());
        assertEquals(12d, estilo(p.estilos(), "CE").getRangoMin());
        assertEquals(48d, estilo(p.estilos(), "CE").getRangoMax());
        EstiloResultadoDTO acce = estilo(p.estilos(), "AC-CE");
        assertEquals(36d, acce.getValor());
        assertEquals(-36d, acce.getRangoMin());
        assertEquals(36d, acce.getRangoMax());
        assertEquals(-12d, estilo(p.estilos(), "AE-RO").getValor());
        assertTrue(p.calificacion().rangosHomogeneos());
    }

    @Test
    @DisplayName("T17: rangos repetidos en una jerarquía son VALIDACION en esa pregunta")
    void kolbRangosRepetidos() {
        Cuestionario k = kolb();
        Map<Long, Double> rangos = rangosKolb(1, 3, 4, 2);
        rangos.put(opcionKolb(1, 1), 1d);

        AppException e = assertThrows(AppException.class, () -> evaluacion.validarSeleccion(k, List.of(), rangos));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
        assertEquals(1, e.getFields().size());
        assertTrue(e.getFields().get("pregunta_1").contains("permutación"));
    }

    @Test
    @DisplayName("Una jerarquía obligatoria sin responder falta; una opcional en blanco se acepta")
    void kolbObligatoriedad() {
        Cuestionario k = kolb();
        Map<Long, Double> sinLaPrimera = rangosKolb(1, 3, 4, 2);
        for (int j = 0; j < 4; j++) {
            sinLaPrimera.remove(opcionKolb(1, j));
        }

        AppException e = assertThrows(AppException.class,
                () -> evaluacion.validarSeleccion(k, List.of(), sinLaPrimera));
        assertEquals(ErrorCode.PREGUNTAS_SIN_RESPONDER, e.getCode());

        k.getPreguntas().stream().filter(p -> p.getOrden() == 1).forEach(p -> p.setObligatoria(false));
        assertEquals(44, evaluacion.validarSeleccion(k, List.of(), sinLaPrimera).size());
    }

    @Test
    @DisplayName("Reparto: los puntos deben sumar P; la suma exacta se acepta")
    void reparto() {
        Cuestionario k = kolb();
        k.getPreguntas().forEach(p -> {
            p.setFormato(FormatoItem.REPARTO);
            p.setPuntosRepartir(5);
            p.setObligatoria(p.getOrden() == 1);
        });

        AppException e = assertThrows(AppException.class, () -> evaluacion.validarSeleccion(k, List.of(),
                Map.of(opcionKolb(1, 0), 2d, opcionKolb(1, 1), 2d)));
        assertTrue(e.getFields().get("pregunta_1").contains("sumar 5"));

        Map<Opcion, Double> ok = evaluacion.validarSeleccion(k, List.of(),
                Map.of(opcionKolb(1, 0), 3d, opcionKolb(1, 1), 2d));
        assertEquals(3d, estilo(evaluacion.puntuar(k, porId(ok)).estilos(), "CE").getValor());
    }

    @Test
    @DisplayName("Las respuestas de una jerarquía se muestran de mayor a menor rango")
    void preguntasResueltasPorRango() {
        Cuestionario k = kolb();
        List<PreguntaResueltaDTO> preguntas = EvaluacionRespuestas.preguntasResueltas(k, rangosKolb(1, 3, 4, 2));

        assertEquals(12, preguntas.size());
        PreguntaResueltaDTO primera = preguntas.get(0);
        assertEquals(FormatoItem.JERARQUIA, primera.getFormato());
        assertEquals(List.of("AC", "RO", "AE", "CE"),
                primera.getRespuestas().stream().map(RespuestaElegidaDTO::texto).toList());
        assertEquals(4d, primera.getRespuestas().get(0).cantidad());
    }

    @Test
    @DisplayName("T06: ILS con 8 a y 3 b: A-B 5, nivel Moderado A, POMP 72,73")
    void ilsT06() {
        Cuestionario ils = new Cuestionario();
        ils.setId(4L);
        ils.setEsquemaInterpretacion(EsquemaInterpretacion.BAREMO);
        Estilo a = estilo(41L, "A");
        Estilo b = estilo(42L, "B");
        Estilo ab = estilo(43L, "A-B");
        ab.setTipo(TipoEstilo.COMPUESTO);
        ab.setCoeficientes(Map.of(41L, 1d, 42L, -1d));
        ils.getEstilos().addAll(List.of(a, b, ab));

        opciones = new HashMap<>(opciones);
        List<Pregunta> preguntas = new ArrayList<>();
        List<Long> elegidas = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            Pregunta p = pregunta(4000L + i, i, false, ils);
            Opcion oa = opcion(40000L + i * 10, p, a, 1d);
            Opcion ob = opcion(40000L + i * 10 + 1, p, b, 1d);
            p.getOpciones().addAll(List.of(oa, ob));
            opciones.put(oa.getId(), oa);
            opciones.put(ob.getId(), ob);
            ils.getPreguntas().add(p);
            preguntas.add(p);
            elegidas.add(i <= 8 ? oa.getId() : ob.getId());
        }
        when(preguntaRepository.findByCuestionario(ils)).thenReturn(preguntas);
        String[] etiquetas = { "Fuerte B", "Moderado B", "Equilibrado", "Moderado A", "Fuerte A" };
        double[][] limites = { { -11, -8 }, { -7, -4 }, { -3, 3 }, { 4, 7 }, { 8, 11 } };
        List<BandaInterpretacion> bandas = new ArrayList<>();
        for (int j = 0; j < 5; j++) {
            BandaInterpretacion banda = new BandaInterpretacion();
            banda.setEstilo(ab);
            banda.setEscala(EscalaBanda.BRUTO);
            banda.setLimiteInferior(limites[j][0]);
            banda.setLimiteSuperior(limites[j][1]);
            banda.setEtiqueta(etiquetas[j]);
            banda.setOrden(j + 1);
            bandas.add(banda);
        }
        when(bandaRepository.findByEstiloCuestionario(ils)).thenReturn(bandas);

        EvaluacionRespuestas.Puntuacion p = evaluacion.puntuar(ils,
                porId(evaluacion.validarSeleccion(ils, elegidas, null)));

        assertEquals(8d, estilo(p.estilos(), "A").getValor());
        assertEquals(3d, estilo(p.estilos(), "B").getValor());
        EstiloResultadoDTO comp = estilo(p.estilos(), "A-B");
        assertEquals(5d, comp.getValor());
        assertEquals("Moderado A", comp.getNivel());
        assertEquals(72.73, comp.getPomp(), 0.005);
    }

    private static Map<Long, Double> porId(Map<Opcion, Double> elegidas) {
        Map<Long, Double> m = new HashMap<>();
        elegidas.forEach((o, c) -> m.put(o.getId(), c));
        return m;
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
