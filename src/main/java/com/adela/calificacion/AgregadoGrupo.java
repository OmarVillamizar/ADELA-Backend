package com.adela.calificacion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.adela.calificacion.Estadistica.Resumen;
import com.adela.calificacion.RangoTeorico.Rango;

/**
 * Reporte de un conjunto de resultados del mismo cuestionario (un grupo o una
 * cápsula): estadísticos por estilo, conteos por nivel y por perfil.
 */
public final class AgregadoGrupo {
    /** Con menos estudiantes el percentil dentro del grupo es demasiado inestable. */
    public static final int N_MINIMO_LOCAL = 30;

    /** Proporciones 10/20/40/20/10 de Honey-Mumford y Alonso et al. (1994). */
    private static final List<Banda> BANDAS_LOCALES = List.of(
            new Banda(0, EscalaBanda.POMP, 0, 10, "Muy baja", 1),
            new Banda(0, EscalaBanda.POMP, 10, 30, "Baja", 2),
            new Banda(0, EscalaBanda.POMP, 30, 70, "Moderada", 3),
            new Banda(0, EscalaBanda.POMP, 70, 90, "Alta", 4),
            new Banda(0, EscalaBanda.POMP, 90, 100, "Muy alta", 5));

    private AgregadoGrupo() {
    }

    /**
     * bruto y pomp son null si ningún resultado calculó el estilo.
     * distribucionBandas es null si el estilo no tiene bandas;
     * distribucionBaremoLocal, si hay menos de nMinimoLocal resultados.
     */
    public record EstiloAgregado(long estiloId, String nombre, TipoEstilo tipo, int orden, double rangoMin,
            double rangoMax, Resumen bruto, Resumen pomp, Map<String, Long> distribucionBandas,
            Map<String, Long> distribucionBaremoLocal) {
    }

    public record Agregado(int n, List<EstiloAgregado> estilos, Map<String, Long> distribucionPerfiles,
            boolean rangosHomogeneos, boolean baremoLocalDisponible) {
    }

    public static Agregado de(ClaveInstrumento clave, List<ResultadoInstrumento> resultados, int nMinimoLocal) {
        Map<Long, List<Banda>> bandas = clave.config().bandas().stream()
                .sorted(Comparator.comparingInt(Banda::orden)).collect(Collectors.groupingBy(Banda::estiloId));

        List<EstiloAgregado> estilos = new ArrayList<>();
        List<Rango> rangos = new ArrayList<>();
        for (EstiloClave e : clave.estilos()) {
            Rango r = RangoTeorico.deInstrumento(clave.items(), Pesos.de(e));
            if (e.tipo() == TipoEstilo.PRIMARIO)
                rangos.add(r);
            List<ResultadoEstilo> calc = resultados.stream().flatMap(ri -> ri.estilos().stream())
                    .filter(re -> re.estiloId() == e.estiloId() && re.estado() != EstadoCalculo.NO_CALCULABLE)
                    .toList();
            double[] brutos = calc.stream().mapToDouble(ResultadoEstilo::bruto).toArray();
            double[] pomps = calc.stream().map(ResultadoEstilo::pomp).filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue).toArray();

            estilos.add(new EstiloAgregado(e.estiloId(), e.nombre(), e.tipo(), e.orden(), r.min(), r.max(),
                    Estadistica.resumir(brutos).orElse(null), Estadistica.resumir(pomps).orElse(null),
                    distribucionBandas(bandas.get(e.estiloId()), calc),
                    pomps.length >= nMinimoLocal ? baremoLocal(pomps) : null));
        }

        Map<String, Long> perfiles = resultados.stream().map(ResultadoInstrumento::perfilEtiqueta)
                .filter(Objects::nonNull).collect(Collectors.groupingBy(p -> p, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        return new Agregado(resultados.size(), estilos, perfiles, RangoTeorico.homogeneos(rangos),
                estilos.stream().anyMatch(e -> e.distribucionBaremoLocal() != null));
    }

    /** Todas las etiquetas en el orden de las bandas, incluso con conteo 0. */
    private static Map<String, Long> distribucionBandas(List<Banda> bandas, List<ResultadoEstilo> calc) {
        if (bandas == null)
            return null;
        Map<String, Long> dist = new LinkedHashMap<>();
        bandas.forEach(b -> dist.putIfAbsent(b.etiqueta(), 0L));
        calc.stream().map(ResultadoEstilo::banda).filter(Objects::nonNull).forEach(b -> dist.merge(b, 1L, Long::sum));
        return dist;
    }

    /** Banda de cada estudiante según su rango percentil dentro del grupo. La última banda es cerrada. */
    private static Map<String, Long> baremoLocal(double[] pomps) {
        Map<String, Long> dist = new LinkedHashMap<>();
        BANDAS_LOCALES.forEach(b -> dist.put(b.etiqueta(), 0L));
        Banda ultima = BANDAS_LOCALES.get(BANDAS_LOCALES.size() - 1);
        for (double v : pomps) {
            double rp = Estadistica.rangoPercentil(pomps, v);
            for (Banda b : BANDAS_LOCALES) {
                if (rp >= b.limiteInferior() && (rp < b.limiteSuperior() || b == ultima)) {
                    dist.merge(b.etiqueta(), 1L, Long::sum);
                    break;
                }
            }
        }
        return dist;
    }
}
