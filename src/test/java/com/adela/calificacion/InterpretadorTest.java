package com.adela.calificacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Casos T08-T11: perfil escalonado de VARK y dominancia relativa. */
class InterpretadorTest {

    /** Distancia de paso de VARK (Fleming, 2001). */
    private static final List<Escalon> ESCALONES_VARK = List.of(new Escalon(0, 16, 1), new Escalon(17, 22, 2),
            new Escalon(23, 26, 3), new Escalon(27, 1e9, 4));

    private static ResultadoEstilo calculado(long id, String nombre, double bruto, Double pomp) {
        return new ResultadoEstilo(id, nombre, TipoEstilo.PRIMARIO, (int) id, bruto, 0, 16, pomp,
                EstadoCalculo.CALCULADO, null, false);
    }

    private static ResultadoInstrumento vark(double v, double a, double r, double k) {
        ClaveInstrumento clave = Instrumentos.vark(new ConfigInterpretacion(
                EsquemaInterpretacion.RELATIVO_ESCALONADO, 10, List.of(), ESCALONES_VARK));
        return Interpretador.interpretar(clave, List.of(calculado(1, "V", v, null), calculado(2, "A", a, null),
                calculado(3, "R", r, null), calculado(4, "K", k, null)));
    }

    @Test
    @DisplayName("T08: V10 A2 R4 K5 (T = 21, d = 2) es V unimodal")
    void varkUnimodal() {
        ResultadoInstrumento r = vark(10, 2, 4, 5);
        assertEquals("V", r.perfilEtiqueta());
        assertEquals("UNIMODAL", r.perfilTipo());
    }

    @Test
    @DisplayName("T09: V7 A8 R6 K5 (T = 26, d = 3) es A + V + R + K multimodal")
    void varkCuatro() {
        ResultadoInstrumento r = vark(7, 8, 6, 5);
        assertEquals("A + V + R + K", r.perfilEtiqueta());
        assertEquals("MULTIMODAL", r.perfilTipo());
    }

    @Test
    @DisplayName("T10: V9 A8 R3 K2 (T = 22, d = 2) es V + A multimodal")
    void varkDos() {
        ResultadoInstrumento r = vark(9, 8, 3, 2);
        assertEquals("V + A", r.perfilEtiqueta());
        assertEquals("MULTIMODAL", r.perfilTipo());
    }

    @Test
    @DisplayName("Sin escalón para el total no hay perfil, en lugar de fallar")
    void sinEscalon() {
        ClaveInstrumento clave = Instrumentos.vark(new ConfigInterpretacion(
                EsquemaInterpretacion.RELATIVO_ESCALONADO, 10, List.of(), List.of(new Escalon(0, 5, 1))));
        ResultadoInstrumento r = Interpretador.interpretar(clave,
                List.of(calculado(1, "V", 9, null), calculado(2, "A", 8, null)));
        assertNull(r.perfilEtiqueta());
    }

    @Test
    @DisplayName("T11: POMP 80, 75, 60, 40 con delta 10 marca dominantes los dos primeros")
    void relativo() {
        Set<Long> d = Interpretador.relativo(List.of(calculado(1, "A", 0, 80.0), calculado(2, "B", 0, 75.0),
                calculado(3, "C", 0, 60.0), calculado(4, "D", 0, 40.0)), 10);
        assertEquals(Set.of(1L, 2L), d);
    }

    private static ResultadoEstilo eje(long id, Double bruto, EstadoCalculo estado) {
        return new ResultadoEstilo(id, "eje" + id, TipoEstilo.COMPUESTO, (int) id, bruto, -36, 36, null, estado,
                null, false);
    }

    /** Eje X = 6 y eje Y = 5, como en Instrumentos.planoKolb. */
    private static ResultadoInstrumento cuadrante(double corteX, double corteY, double x, double y) {
        ClaveInstrumento clave = Instrumentos.kolbConPlano(corteX, corteY);
        return Interpretador.interpretar(clave, List.of(eje(6, x, EstadoCalculo.CALCULADO),
                eje(5, y, EstadoCalculo.CALCULADO)));
    }

    @Test
    @DisplayName("Cuadrantes: las cuatro esquinas, con perfilTipo CUADRANTE y sin dominantes")
    void cuatroEsquinas() {
        assertEquals("Convergente", cuadrante(0, 0, 5, 5).perfilEtiqueta());
        assertEquals("Asimilador", cuadrante(0, 0, -5, 5).perfilEtiqueta());
        assertEquals("Divergente", cuadrante(0, 0, -5, -5).perfilEtiqueta());
        assertEquals("Acomodador", cuadrante(0, 0, 5, -5).perfilEtiqueta());
        ResultadoInstrumento r = cuadrante(0, 0, 5, 5);
        assertEquals("CUADRANTE", r.perfilTipo());
        assertTrue(r.estilos().stream().noneMatch(ResultadoEstilo::dominante));
    }

    @Test
    @DisplayName("Cuadrantes: igual al corte va al lado bajo (6/7 Divergente, 7/8 Convergente)")
    void igualAlCorteEsBajo() {
        assertEquals("Divergente", cuadrante(6, 7, 6, 7).perfilEtiqueta());
        assertEquals("Convergente", cuadrante(6, 7, 7, 8).perfilEtiqueta());
        assertEquals("Divergente", cuadrante(0, 0, 0, 0).perfilEtiqueta());
    }

    @Test
    @DisplayName("Cuadrantes: el ejemplo del manual (5, 4) depende del corte")
    void ejemploDelManual() {
        assertEquals("Convergente", cuadrante(0, 0, 5, 4).perfilEtiqueta());
        assertEquals("Divergente", cuadrante(6, 7, 5, 4).perfilEtiqueta());
    }

    @Test
    @DisplayName("Cuadrantes: un eje no calculable o ausente no da perfil")
    void ejeNoCalculable() {
        ClaveInstrumento clave = Instrumentos.kolbConPlano(0, 0);
        ResultadoInstrumento sinY = Interpretador.interpretar(clave,
                List.of(eje(6, 5.0, EstadoCalculo.CALCULADO), eje(5, null, EstadoCalculo.NO_CALCULABLE)));
        assertNull(sinY.perfilEtiqueta());
        assertNull(sinY.perfilTipo());
        ResultadoInstrumento sinX = Interpretador.interpretar(clave, List.of(eje(5, 5.0, EstadoCalculo.CALCULADO)));
        assertNull(sinX.perfilEtiqueta());
    }

    @Test
    @DisplayName("Cuadrantes: Kolb AC=4 AE=3 RO=2 CE=1 en los 12 ítems da X=+12, Y=+36, Convergente con 6/7")
    void motorCompleto() {
        // La opción j pesa el estilo j (CE=1, RO=2, AC=3, AE=4); el rango de cada opción es su valor.
        Map<Long, RespuestaItem> resp = new HashMap<>();
        double[] rango = { 1, 2, 4, 3 };
        for (long i = 1; i <= 12; i++) {
            Map<Long, Double> c = new HashMap<>();
            for (int j = 1; j <= 4; j++)
                c.put(Instrumentos.opcion(i, j), rango[j - 1]);
            resp.put(i, new RespuestaItem(i, c));
        }
        ResultadoInstrumento r = MotorCalificacion.calificar(Instrumentos.kolbConPlano(6, 7), resp);
        assertEquals(12.0, Instrumentos.estilo(r, 6).bruto(), 1e-9);
        assertEquals(36.0, Instrumentos.estilo(r, 5).bruto(), 1e-9);
        assertEquals("Convergente", r.perfilEtiqueta());
        assertEquals("CUADRANTE", r.perfilTipo());
    }

    @Test
    @DisplayName("Las bandas se aplican aunque el esquema no marque dominantes")
    void bandaSinEsquema() {
        ResultadoEstilo r = calculado(1, "A", 12, 75.0);
        assertEquals("Alta", Interpretador.bandaPara(r, List.of(new Banda(1, EscalaBanda.POMP, 0, 50, "Baja", 1),
                new Banda(1, EscalaBanda.POMP, 50, 100, "Alta", 2))));
    }
}
