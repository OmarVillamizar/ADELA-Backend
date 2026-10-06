package com.adela.calificacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariantes del motor sobre 1000 respuestas válidas al azar por instrumento.
 * La semilla es fija para que un fallo se pueda reproducir.
 */
class PropiedadesMotorTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("Puntaje dentro del rango, POMP en [0, 100] y compuesto = combinación de sus primarios")
    void invariantes() {
        List<ClaveInstrumento> claves = List.of(Instrumentos.acra(), Instrumentos.herrmann(),
                Instrumentos.vark(Instrumentos.SIN_INTERPRETACION), Instrumentos.kolb(),
                Instrumentos.ils(List.of()), Instrumentos.reparto());
        Random rnd = new Random(20261006);

        for (ClaveInstrumento clave : claves) {
            for (int caso = 0; caso < 1000; caso++) {
                Map<Long, RespuestaItem> resp = Instrumentos.aleatorias(clave, rnd);
                for (ItemClave it : clave.items())
                    assertTrue(ValidadorRespuesta.errores(it, resp.get(it.itemId())).isEmpty());

                ResultadoInstrumento r = MotorCalificacion.calificar(clave, resp);
                for (ResultadoEstilo e : r.estilos()) {
                    assertEquals(EstadoCalculo.CALCULADO, e.estado());
                    assertTrue(e.bruto() >= e.rangoMin() - EPS && e.bruto() <= e.rangoMax() + EPS);
                    if (e.pomp() != null)
                        assertTrue(e.pomp() >= 0 && e.pomp() <= 100);
                }
                for (EstiloClave k : clave.estilos()) {
                    if (k.tipo() != TipoEstilo.COMPUESTO)
                        continue;
                    double esperado = k.coeficientes().entrySet().stream()
                            .mapToDouble(c -> c.getValue() * Instrumentos.estilo(r, c.getKey()).bruto()).sum();
                    assertEquals(esperado, Instrumentos.estilo(r, k.estiloId()).bruto(), EPS);
                }
            }
        }
    }
}
