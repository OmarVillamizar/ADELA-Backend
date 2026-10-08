package com.adela.calificacion;

import static com.adela.calificacion.Instrumentos.estilo;
import static com.adela.calificacion.Instrumentos.opcion;
import static com.adela.calificacion.Instrumentos.todos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.ToDoubleFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.calificacion.RangoTeorico.Rango;

/** Casos de referencia T01-T07, T14-T16 y T19 de la especificación del motor. */
class MotorCalificacionTest {

    private static final double EPS = 1e-9;

    private static Rango rango(ClaveInstrumento c, long estiloId) {
        EstiloClave e = c.estilos().stream().filter(x -> x.estiloId() == estiloId).findFirst().orElseThrow();
        return RangoTeorico.deInstrumento(c.items(), Pesos.de(e));
    }

    @Test
    @DisplayName("T01: POMP de 18 en [0, 20] es 90")
    void pomp() {
        assertEquals(90.0, MotorCalificacion.pomp(18.0, new Rango(0, 20)), EPS);
    }

    @Test
    @DisplayName("T02: ACRA Likert 1-4 da rango [20, 80]; todo en 3 da 60 y POMP 66,67")
    void acra() {
        ClaveInstrumento c = Instrumentos.acra();
        assertEquals(new Rango(20, 80), rango(c, 1));

        ResultadoEstilo r = estilo(MotorCalificacion.calificar(c, todos(20, 3)), 1);
        assertEquals(60.0, r.bruto(), EPS);
        assertEquals(66.67, r.pomp(), 0.005);
        assertEquals(EstadoCalculo.CALCULADO, r.estado());
    }

    @Test
    @DisplayName("T03: Herrmann con opciones a cuadrantes distintos tiene rango [0, 10], no [10, 10]")
    void herrmann() {
        ClaveInstrumento c = Instrumentos.herrmann();
        for (long e = 1; e <= 4; e++)
            assertEquals(new Rango(0, 10), rango(c, e));
    }

    @Test
    @DisplayName("T04: VARK de selección múltiple opcional tiene rango [0, 16] por modalidad")
    void vark() {
        ClaveInstrumento c = Instrumentos.vark(Instrumentos.SIN_INTERPRETACION);
        for (long e = 1; e <= 4; e++)
            assertEquals(new Rango(0, 16), rango(c, e));
    }

    @Test
    @DisplayName("T05: Kolb jerarquizado da [12, 48] por modo y [-36, 36] para AC-CE")
    void kolb() {
        ClaveInstrumento c = Instrumentos.kolb();
        assertEquals(new Rango(12, 48), rango(c, 1));
        assertEquals(new Rango(-36, 36), rango(c, 5));
        // El compuesto no cuenta para la homogeneidad: los cuatro modos comparten [12, 48].
        assertTrue(MotorCalificacion.calificar(c, Instrumentos.aleatorias(c, new Random(1))).rangosHomogeneos());
    }

    @Test
    @DisplayName("T06: ILS con 8 respuestas a y 3 b: compuesto 5, banda Moderado A, POMP 72,73")
    void ils() {
        List<Banda> bandas = List.of(new Banda(3, EscalaBanda.BRUTO, -11, -8, "Fuerte B", 1),
                new Banda(3, EscalaBanda.BRUTO, -7, -4, "Moderado B", 2),
                new Banda(3, EscalaBanda.BRUTO, -3, 3, "Equilibrado", 3),
                new Banda(3, EscalaBanda.BRUTO, 4, 7, "Moderado A", 4),
                new Banda(3, EscalaBanda.BRUTO, 8, 11, "Fuerte A", 5));
        Map<Long, Integer> elegidas = new HashMap<>();
        for (long i = 1; i <= 11; i++)
            elegidas.put(i, i <= 8 ? 1 : 2);

        ResultadoInstrumento r = MotorCalificacion.calificar(Instrumentos.ils(bandas),
                Instrumentos.marcar(elegidas));

        assertEquals(8.0, estilo(r, 1).bruto(), EPS);
        assertEquals(3.0, estilo(r, 2).bruto(), EPS);
        ResultadoEstilo comp = estilo(r, 3);
        assertEquals(5.0, comp.bruto(), EPS);
        assertEquals(-11.0, comp.rangoMin(), EPS);
        assertEquals("Moderado A", comp.banda());
        assertEquals(72.73, comp.pomp(), 0.005);
    }

    @Test
    @DisplayName("T07: CHAEA con el baremo general: Activo 13 Alta, Reflexivo 20 Muy alta, Teórico 9 Baja")
    void chaea() {
        // Ítem i pertenece al estilo (i % 4) + 1; opción 1 = "+" (suma 1), opción 2 = "-" (sin peso).
        List<ItemClave> items = new ArrayList<>();
        for (long i = 0; i < 80; i++) {
            long estiloId = i % 4 + 1;
            items.add(new ItemClave(i, FormatoItem.UNICA, true, 0, null, null,
                    List.of(new OpcionClave(opcion(i, 1), Map.of(estiloId, 1.0)),
                            new OpcionClave(opcion(i, 2), Map.of()))));
        }
        List<Banda> bandas = new ArrayList<>();
        double[][] cortes = { { 0, 6, 7, 8, 9, 12, 13, 14, 15, 20 }, { 0, 10, 11, 13, 14, 17, 18, 19, 20, 20 },
                { 0, 6, 7, 9, 10, 13, 14, 15, 16, 20 }, { 0, 8, 9, 10, 11, 13, 14, 15, 16, 20 } };
        String[] etiquetas = { "Muy baja", "Baja", "Moderada", "Alta", "Muy alta" };
        for (int e = 0; e < 4; e++)
            for (int b = 0; b < 5; b++)
                bandas.add(new Banda(e + 1, EscalaBanda.BRUTO, cortes[e][2 * b], cortes[e][2 * b + 1],
                        etiquetas[b], b + 1));
        ClaveInstrumento c = Instrumentos.clave(items,
                List.of(Instrumentos.primario(1, "Activo"), Instrumentos.primario(2, "Reflexivo"),
                        Instrumentos.primario(3, "Teórico"), Instrumentos.primario(4, "Pragmático")),
                new ConfigInterpretacion(EsquemaInterpretacion.BAREMO, 10, bandas, List.of()));

        int[] positivos = { 13, 20, 9, 12 };
        int[] marcados = new int[4];
        Map<Long, RespuestaItem> resp = new HashMap<>();
        for (long i = 0; i < 80; i++) {
            int e = (int) (i % 4);
            int j = marcados[e]++ < positivos[e] ? 1 : 2;
            resp.put(i, new RespuestaItem(i, Map.of(opcion(i, j), 1.0)));
        }

        ResultadoInstrumento r = MotorCalificacion.calificar(c, resp);
        assertEquals(13.0, estilo(r, 1).bruto(), EPS);
        assertEquals("Alta", estilo(r, 1).banda());
        assertEquals("Muy alta", estilo(r, 2).banda());
        assertEquals("Baja", estilo(r, 3).banda());
        assertEquals("Moderada", estilo(r, 4).banda());
        assertNull(r.perfilEtiqueta());
    }

    private static ClaveInstrumento likertConOmisiones() {
        ClaveInstrumento base = Instrumentos.acra();
        return new ClaveInstrumento(1, true, base.items(), base.estilos(), base.config());
    }

    @Test
    @DisplayName("T14: 2 de 20 obligatorios omitidos (10 %) se prorratea: bruto 80, POMP 100")
    void prorrateo() {
        Map<Long, RespuestaItem> resp = todos(20, 4);
        resp.remove(1L);
        resp.remove(2L);

        ResultadoEstilo r = estilo(MotorCalificacion.calificar(likertConOmisiones(), resp), 1);
        assertEquals(EstadoCalculo.PRORRATEADO, r.estado());
        assertEquals(80.0, r.bruto(), EPS);
        assertEquals(100.0, r.pomp(), EPS);
    }

    @Test
    @DisplayName("T15: 3 de 20 omitidos (15 %) no es calculable")
    void noCalculable() {
        Map<Long, RespuestaItem> resp = todos(20, 4);
        resp.remove(1L);
        resp.remove(2L);
        resp.remove(3L);

        ResultadoEstilo r = estilo(MotorCalificacion.calificar(likertConOmisiones(), resp), 1);
        assertEquals(EstadoCalculo.NO_CALCULABLE, r.estado());
        assertNull(r.bruto());
        assertNull(r.pomp());
    }

    @Test
    @DisplayName("Sin permitir omisiones, un obligatorio en blanco deja el estilo sin calcular")
    void omisionNoPermitida() {
        Map<Long, RespuestaItem> resp = todos(20, 4);
        resp.remove(1L);

        ResultadoEstilo r = estilo(MotorCalificacion.calificar(Instrumentos.acra(), resp), 1);
        assertEquals(EstadoCalculo.NO_CALCULABLE, r.estado());
    }

    @Test
    @DisplayName("T16: reparto de 5 puntos con pesos [1, 0, 0, 0] da rango del ítem [0, 5]")
    void reparto() {
        ItemClave it = new ItemClave(1, FormatoItem.REPARTO, true, 0, null, 5,
                List.of(new OpcionClave(11, Map.of(1L, 1.0)), new OpcionClave(12, Map.of()),
                        new OpcionClave(13, Map.of()), new OpcionClave(14, Map.of())));
        ToDoubleFunction<OpcionClave> w = Pesos.de(Instrumentos.primario(1, "A"));
        assertEquals(new Rango(0, 5), RangoTeorico.deItem(it, w));
    }

    @Test
    @DisplayName("T19: un puntaje fuera del rango es una clave inconsistente")
    void claveInconsistente() {
        assertThrows(ClaveInconsistenteException.class, () -> MotorCalificacion.pomp(25.0, new Rango(0, 20)));
    }

    @Test
    @DisplayName("Selección múltiple suma cada opción marcada, igual que el cálculo anterior")
    void multipleSumaOpciones() {
        ClaveInstrumento c = Instrumentos.vark(Instrumentos.SIN_INTERPRETACION);
        Map<Long, RespuestaItem> resp = Map.of(1L, new RespuestaItem(1, Map.of(11L, 1.0, 13L, 1.0)),
                2L, new RespuestaItem(2, Map.of(21L, 1.0)));

        ResultadoInstrumento r = MotorCalificacion.calificar(c, resp);
        assertEquals(2.0, estilo(r, 1).bruto(), EPS);
        assertEquals(0.0, estilo(r, 2).bruto(), EPS);
        assertEquals(1.0, estilo(r, 3).bruto(), EPS);
        assertEquals(EstadoCalculo.CALCULADO, estilo(r, 4).estado());
    }
}
