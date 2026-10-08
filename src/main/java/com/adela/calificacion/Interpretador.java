package com.adela.calificacion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Traduce los puntajes a nivel (banda) y perfil (estilos dominantes), como lo
 * indica el manual de cada cuestionario.
 */
public final class Interpretador {
    private Interpretador() {
    }

    public static ResultadoInstrumento interpretar(ClaveInstrumento clave, List<ResultadoEstilo> res) {
        ConfigInterpretacion cfg = clave.config();
        Map<Long, List<Banda>> bandas = cfg.bandas().stream().collect(Collectors.groupingBy(Banda::estiloId));

        Set<Long> dominantes = switch (cfg.esquema()) {
            case RELATIVO -> relativo(res, cfg.delta());
            case RELATIVO_ESCALONADO -> escalonado(res, cfg.escalones());
            default -> Set.of();
        };

        List<ResultadoEstilo> salida = new ArrayList<>();
        for (ResultadoEstilo r : res) {
            String banda = bandaPara(r, bandas.getOrDefault(r.estiloId(), List.of()));
            salida.add(r.conInterpretacion(banda, dominantes.contains(r.estiloId())));
        }

        String etiqueta = null, tipo = null;
        if (cfg.esquema() == EsquemaInterpretacion.CUADRANTES && cfg.plano() != null) {
            etiqueta = esquina(cfg.plano(), res);
            if (etiqueta != null)
                tipo = "CUADRANTE";
        } else if (!dominantes.isEmpty()) {
            List<ResultadoEstilo> dom = salida.stream().filter(ResultadoEstilo::dominante)
                    .sorted(Comparator.comparing(ResultadoEstilo::bruto, Comparator.reverseOrder())
                            .thenComparingInt(ResultadoEstilo::orden))
                    .toList();
            etiqueta = dom.stream().map(ResultadoEstilo::nombre).collect(Collectors.joining(" + "));
            tipo = dom.size() == 1 ? "UNIMODAL" : "MULTIMODAL";
        }
        return new ResultadoInstrumento(clave.cuestionarioId(), MotorCalificacion.VERSION, salida, etiqueta, tipo);
    }

    /** Esquina del plano; null si algún eje falta o no se pudo calcular. */
    static String esquina(Plano plano, List<ResultadoEstilo> res) {
        Double x = brutoCalculado(res, plano.ejeX()), y = brutoCalculado(res, plano.ejeY());
        return x == null || y == null ? null : plano.esquina(x, y);
    }

    /** Bruto del estilo; null si falta o no se pudo calcular. */
    public static Double brutoCalculado(List<ResultadoEstilo> res, long estiloId) {
        return res.stream().filter(r -> r.estiloId() == estiloId && r.estado() != EstadoCalculo.NO_CALCULABLE)
                .map(ResultadoEstilo::bruto).filter(b -> b != null).findFirst().orElse(null);
    }

    /** Primera banda, en orden, cuyo intervalo cerrado contiene el valor. */
    static String bandaPara(ResultadoEstilo r, List<Banda> bandas) {
        for (Banda b : bandas.stream().sorted(Comparator.comparingInt(Banda::orden)).toList()) {
            Double x = switch (b.escala()) {
                case BRUTO -> r.bruto();
                case POMP -> r.pomp();
            };
            if (x != null && x >= b.limiteInferior() - Calculos.EPS && x <= b.limiteSuperior() + Calculos.EPS)
                return b.etiqueta();
        }
        return null;
    }

    /** Dominante si POMP >= max(POMP) - delta. */
    static Set<Long> relativo(List<ResultadoEstilo> res, double delta) {
        List<ResultadoEstilo> prim = primariosCalculados(res).stream().filter(r -> r.pomp() != null).toList();
        if (prim.isEmpty())
            return Set.of();
        double max = prim.stream().mapToDouble(ResultadoEstilo::pomp).max().getAsDouble();
        return prim.stream().filter(r -> r.pomp() >= max - delta - Calculos.EPS).map(ResultadoEstilo::estiloId)
                .collect(Collectors.toSet());
    }

    /**
     * Distancia de paso (Fleming, 2001): ordenar por puntaje directo e incluir el
     * siguiente mientras la diferencia con el anterior no supere d. Sin escalón
     * para el total no hay perfil.
     */
    static Set<Long> escalonado(List<ResultadoEstilo> res, List<Escalon> tabla) {
        List<ResultadoEstilo> ord = primariosCalculados(res).stream()
                .sorted(Comparator.comparing(ResultadoEstilo::bruto, Comparator.reverseOrder())
                        .thenComparingInt(ResultadoEstilo::orden))
                .toList();
        if (ord.isEmpty())
            return Set.of();
        double total = ord.stream().mapToDouble(ResultadoEstilo::bruto).sum();
        Double paso = tabla.stream()
                .filter(s -> total >= s.totalMin() - Calculos.EPS && total <= s.totalMax() + Calculos.EPS)
                .findFirst().map(Escalon::distancia).orElse(null);
        if (paso == null)
            return Set.of();
        Set<Long> d = new LinkedHashSet<>();
        d.add(ord.get(0).estiloId());
        for (int j = 1; j < ord.size(); j++) {
            if (ord.get(j - 1).bruto() - ord.get(j).bruto() <= paso + Calculos.EPS)
                d.add(ord.get(j).estiloId());
            else
                break;
        }
        return d;
    }

    private static List<ResultadoEstilo> primariosCalculados(List<ResultadoEstilo> res) {
        return res.stream().filter(r -> r.tipo() == TipoEstilo.PRIMARIO && r.estado() != EstadoCalculo.NO_CALCULABLE
                && r.bruto() != null).toList();
    }
}
