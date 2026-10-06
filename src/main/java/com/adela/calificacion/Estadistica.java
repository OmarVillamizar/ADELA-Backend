package com.adela.calificacion;

import java.util.Arrays;
import java.util.Optional;

public final class Estadistica {
    private Estadistica() {
    }

    /** desviacion es null con n < 2. */
    public record Resumen(int n, double media, Double desviacion, double mediana, double p25, double p75,
            double minimo, double maximo) {
    }

    /** Media, desviación estándar muestral y cuantiles. Vacío sin valores. */
    public static Optional<Resumen> resumir(double[] valores) {
        if (valores == null || valores.length == 0)
            return Optional.empty();
        double[] x = valores.clone();
        Arrays.sort(x);
        int n = x.length;
        double suma = 0;
        for (double v : x)
            suma += v;
        double media = suma / n;
        Double de = null;
        if (n >= 2) {
            double ss = 0;
            for (double v : x)
                ss += (v - media) * (v - media);
            de = Math.sqrt(ss / (n - 1));
        }
        return Optional.of(new Resumen(n, media, de, cuantil(x, 0.5), cuantil(x, 0.25), cuantil(x, 0.75), x[0],
                x[n - 1]));
    }

    /**
     * Cuantil tipo 7 (Hyndman y Fan, 1996), el mismo de percentile_cont de
     * PostgreSQL y PERCENTIL.INC de Excel. x ordenado ascendentemente, 0 <= p <= 1.
     */
    public static double cuantil(double[] x, double p) {
        double h = (x.length - 1) * p;
        int f = (int) Math.floor(h);
        int c = Math.min(f + 1, x.length - 1);
        return x[f] + (h - f) * (x[c] - x[f]);
    }

    /** Rango percentil con medio rango para empates: 100 (#{x < v} + 0,5 #{x = v}) / n. */
    public static double rangoPercentil(double[] x, double v) {
        int menores = 0, iguales = 0;
        for (double xi : x) {
            if (xi < v - Calculos.EPS)
                menores++;
            else if (Math.abs(xi - v) <= Calculos.EPS)
                iguales++;
        }
        return 100.0 * (menores + 0.5 * iguales) / x.length;
    }
}
