package com.adela.calificacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
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

    @Test
    @DisplayName("Las bandas se aplican aunque el esquema no marque dominantes")
    void bandaSinEsquema() {
        ResultadoEstilo r = calculado(1, "A", 12, 75.0);
        assertEquals("Alta", Interpretador.bandaPara(r, List.of(new Banda(1, EscalaBanda.POMP, 0, 50, "Baja", 1),
                new Banda(1, EscalaBanda.POMP, 50, 100, "Alta", 2))));
    }
}
