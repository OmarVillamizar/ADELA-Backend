package com.adela.calificacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.calificacion.Estadistica.Resumen;

/** Casos T12-T13. Los valores coinciden con percentile_cont y stddev_samp de PostgreSQL. */
class EstadisticaTest {

    private static final double[] X = { 2, 4, 4, 4, 5, 5, 7, 9 };

    @Test
    @DisplayName("T12: media 5, DE raíz(32/7), mediana 4,5, P25 4, P75 5,5")
    void resumen() {
        Resumen r = Estadistica.resumir(X).orElseThrow();
        assertEquals(8, r.n());
        assertEquals(5.0, r.media(), 1e-9);
        assertEquals(Math.sqrt(32.0 / 7), r.desviacion(), 1e-9);
        assertEquals(4.5, r.mediana(), 1e-9);
        assertEquals(4.0, r.p25(), 1e-9);
        assertEquals(5.5, r.p75(), 1e-9);
        assertEquals(2.0, r.minimo(), 1e-9);
        assertEquals(9.0, r.maximo(), 1e-9);
    }

    @Test
    @DisplayName("T13: rango percentil de 4 es 31,25")
    void rangoPercentil() {
        assertEquals(31.25, Estadistica.rangoPercentil(X, 4), 1e-9);
    }

    @Test
    @DisplayName("Sin valores no hay resumen; con uno solo no hay desviación")
    void bordes() {
        assertTrue(Estadistica.resumir(new double[0]).isEmpty());
        assertNull(Estadistica.resumir(new double[] { 3 }).orElseThrow().desviacion());
    }
}
