package com.adela.calificacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.calificacion.AgregadoGrupo.Agregado;
import com.adela.calificacion.AgregadoGrupo.EstiloAgregado;

class AgregadoGrupoTest {

    private static ResultadoInstrumento resultado(String perfil, Double bruto, Double pomp, EstadoCalculo estado,
            String banda) {
        return new ResultadoInstrumento(1, MotorCalificacion.VERSION, List.of(new ResultadoEstilo(1, "Adquisición",
                TipoEstilo.PRIMARIO, 1, bruto, 20, 80, pomp, estado, banda, false)), perfil, null);
    }

    private static ResultadoInstrumento calculado(double bruto, String banda, String perfil) {
        return resultado(perfil, bruto, 100 * (bruto - 20) / 60, EstadoCalculo.CALCULADO, banda);
    }

    private static ClaveInstrumento acraConBandas() {
        ClaveInstrumento base = Instrumentos.acra();
        List<Banda> bandas = List.of(new Banda(1, EscalaBanda.BRUTO, 20, 40, "Baja", 1),
                new Banda(1, EscalaBanda.BRUTO, 41, 60, "Media", 2),
                new Banda(1, EscalaBanda.BRUTO, 61, 80, "Alta", 3));
        return Instrumentos.clave(base.items(), base.estilos(),
                new ConfigInterpretacion(EsquemaInterpretacion.BAREMO, 10, bandas, List.of()));
    }

    @Test
    @DisplayName("Estadísticos, bandas en su orden con ceros, perfiles por frecuencia y NO_CALCULABLE excluido")
    void agrega() {
        Agregado a = AgregadoGrupo.de(acraConBandas(), List.of(calculado(30, "Baja", "V"),
                calculado(50, "Media", "V + A"), calculado(50, "Media", "V"),
                resultado(null, null, null, EstadoCalculo.NO_CALCULABLE, null)), AgregadoGrupo.N_MINIMO_LOCAL);

        assertEquals(4, a.n());
        EstiloAgregado e = a.estilos().get(0);
        assertEquals(20.0, e.rangoMin(), 1e-9);
        assertEquals(80.0, e.rangoMax(), 1e-9);
        assertEquals(3, e.bruto().n());
        assertEquals(130.0 / 3, e.bruto().media(), 1e-9);
        assertEquals(50.0, e.bruto().mediana(), 1e-9);

        Map<String, Long> bandas = new LinkedHashMap<>();
        bandas.put("Baja", 1L);
        bandas.put("Media", 2L);
        bandas.put("Alta", 0L);
        assertEquals(bandas, e.distribucionBandas());
        assertEquals(List.of("Baja", "Media", "Alta"), new ArrayList<>(e.distribucionBandas().keySet()));

        assertEquals(List.of("V", "V + A"), new ArrayList<>(a.distribucionPerfiles().keySet()));
        assertEquals(2L, a.distribucionPerfiles().get("V"));
        assertNull(e.distribucionBaremoLocal());
        assertFalse(a.baremoLocalDisponible());
    }

    @Test
    @DisplayName("Sin resultados no hay resúmenes ni NaN, pero sí el rango")
    void vacio() {
        Agregado a = AgregadoGrupo.de(Instrumentos.acra(), List.of(), AgregadoGrupo.N_MINIMO_LOCAL);
        EstiloAgregado e = a.estilos().get(0);
        assertNull(e.bruto());
        assertNull(e.pomp());
        assertNull(e.distribucionBandas());
        assertEquals(80.0, e.rangoMax(), 1e-9);
        assertTrue(a.distribucionPerfiles().isEmpty());
    }

    @Test
    @DisplayName("Con 30 resultados el baremo local reparte 3/6/12/6/3; con 29 no se calcula")
    void baremoLocal() {
        List<ResultadoInstrumento> treinta = new ArrayList<>();
        for (int i = 0; i < 30; i++)
            treinta.add(resultado(null, 20.0 + i, (double) i, EstadoCalculo.CALCULADO, null));

        Agregado a = AgregadoGrupo.de(Instrumentos.acra(), treinta, AgregadoGrupo.N_MINIMO_LOCAL);
        assertTrue(a.baremoLocalDisponible());
        assertEquals(List.of(3L, 6L, 12L, 6L, 3L),
                new ArrayList<>(a.estilos().get(0).distribucionBaremoLocal().values()));

        Agregado b = AgregadoGrupo.de(Instrumentos.acra(), treinta.subList(0, 29), AgregadoGrupo.N_MINIMO_LOCAL);
        assertFalse(b.baremoLocalDisponible());
    }

    @Test
    @DisplayName("Rangos homogéneos en Herrmann; no en Kolb, donde el compuesto tiene otro rango")
    void homogeneos() {
        assertTrue(AgregadoGrupo.de(Instrumentos.herrmann(), List.of(), 30).rangosHomogeneos());
        assertFalse(AgregadoGrupo.de(Instrumentos.kolb(), List.of(), 30).rangosHomogeneos());
    }
}
