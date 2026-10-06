package com.adela.calificacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Casos T17-T18 y la obligatoriedad. */
class ValidadorRespuestaTest {

    private static List<OpcionClave> cuatro() {
        return List.of(new OpcionClave(11, Map.of()), new OpcionClave(12, Map.of()), new OpcionClave(13, Map.of()),
                new OpcionClave(14, Map.of()));
    }

    @Test
    @DisplayName("T17: jerarquía con rangos {1, 1, 3, 4} no es una permutación")
    void jerarquiaRepetida() {
        ItemClave it = new ItemClave(1, FormatoItem.JERARQUIA, true, 0, null, null, cuatro());
        List<String> e = ValidadorRespuesta.errores(it,
                new RespuestaItem(1, Map.of(11L, 1.0, 12L, 1.0, 13L, 3.0, 14L, 4.0)));
        assertEquals(1, e.size());
        assertTrue(e.get(0).contains("permutación"));
    }

    @Test
    @DisplayName("T18: múltiple con máximo 2 rechaza 3 marcadas")
    void multipleExcedida() {
        ItemClave it = new ItemClave(1, FormatoItem.MULTIPLE, false, 0, 2, null, cuatro());
        List<String> e = ValidadorRespuesta.errores(it, new RespuestaItem(1, Map.of(11L, 1.0, 12L, 1.0, 13L, 1.0)));
        assertEquals(1, e.size());
        assertTrue(e.get(0).contains("entre 0 y 2"));
    }

    @Test
    @DisplayName("Un ítem obligatorio en blanco es error; uno opcional no")
    void obligatoriedad() {
        ItemClave obligatorio = new ItemClave(1, FormatoItem.UNICA, true, 0, null, null, cuatro());
        ItemClave opcional = new ItemClave(1, FormatoItem.UNICA, false, 0, null, null, cuatro());
        RespuestaItem blanco = new RespuestaItem(1, Map.of());
        assertEquals(1, ValidadorRespuesta.errores(obligatorio, blanco).size());
        assertTrue(ValidadorRespuesta.errores(opcional, blanco).isEmpty());
    }

    @Test
    @DisplayName("El reparto debe sumar exactamente los puntos")
    void reparto() {
        ItemClave it = new ItemClave(1, FormatoItem.REPARTO, true, 0, null, 5, cuatro());
        assertTrue(ValidadorRespuesta.errores(it, new RespuestaItem(1, Map.of(11L, 3.0, 12L, 2.0))).isEmpty());
        assertEquals(1, ValidadorRespuesta.errores(it, new RespuestaItem(1, Map.of(11L, 3.0, 12L, 1.0))).size());
    }
}
