package com.adela.calificacion;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Mínimo y máximo que un estilo puede alcanzar. Los ítems son independientes,
 * así que el rango del instrumento es la suma exacta de los rangos por ítem.
 *
 * Corrige el cálculo anterior, que en selección única tomaba solo los pesos del
 * propio estilo: si las demás opciones apuntan a otro estilo, el mínimo real es
 * 0 y no el menor peso propio (Herrmann, PNL, VARK).
 */
public final class RangoTeorico {
    private RangoTeorico() {
    }

    public record Rango(double min, double max) {
        public static final Rango CERO = new Rango(0, 0);

        public Rango mas(Rango otro) {
            return new Rango(min + otro.min, max + otro.max);
        }

        public boolean degenerado() {
            return max - min < Calculos.EPS;
        }
    }

    public static Rango deInstrumento(List<ItemClave> items, ToDoubleFunction<OpcionClave> w) {
        Rango total = Rango.CERO;
        for (ItemClave it : items)
            total = total.mas(deItem(it, w));
        return total;
    }

    public static Rango deItem(ItemClave it, ToDoubleFunction<OpcionClave> w) {
        double[] pesos = it.opciones().stream().mapToDouble(w).toArray();
        boolean vacio = !it.obligatorio();
        return switch (it.formato()) {
            case UNICA, MULTIPLE -> porSeleccion(pesos, it.minSelEfectivo(), it.maxSelEfectivo(), vacio);
            case JERARQUIA -> porJerarquia(pesos, vacio);
            case REPARTO -> porReparto(pesos, it.puntosRepartir(), vacio);
        };
    }

    /** Todos los rangos tienen el mismo mínimo y máximo: el puntaje directo es comparable entre estilos. */
    public static boolean homogeneos(List<Rango> rangos) {
        if (rangos.isEmpty())
            return true;
        Rango r0 = rangos.get(0);
        return rangos.stream().allMatch(
                r -> Math.abs(r.min() - r0.min()) < Calculos.EPS && Math.abs(r.max() - r0.max()) < Calculos.EPS);
    }

    /** t opciones marcadas, lo <= t <= hi; t = 0 admitido si vacio. */
    static Rango porSeleccion(double[] w, int lo, int hi, boolean vacio) {
        double[] asc = w.clone();
        Arrays.sort(asc);
        int k = asc.length;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        double menores = 0, mayores = 0;
        for (int t = 0; t <= hi; t++) {
            if (t > 0) {
                menores += asc[t - 1];
                mayores += asc[k - t];
            }
            if (t >= lo || (t == 0 && vacio)) {
                min = Math.min(min, menores);
                max = Math.max(max, mayores);
            }
        }
        return new Rango(min, max);
    }

    /** Permutación de rangos 1..k con a(o) = rango. Desigualdad de reordenamiento. */
    static Rango porJerarquia(double[] w, boolean vacio) {
        double[] asc = w.clone();
        Arrays.sort(asc);
        int k = asc.length;
        double max = 0, min = 0;
        for (int j = 0; j < k; j++) {
            max += asc[j] * (j + 1);
            min += asc[j] * (k - j);
        }
        if (vacio) {
            min = Math.min(min, 0);
            max = Math.max(max, 0);
        }
        return new Rango(min, max);
    }

    /** P puntos enteros no negativos que suman P. */
    static Rango porReparto(double[] w, int puntos, boolean vacio) {
        double wMin = Arrays.stream(w).min().orElseThrow();
        double wMax = Arrays.stream(w).max().orElseThrow();
        double min = puntos * wMin, max = puntos * wMax;
        if (vacio) {
            min = Math.min(min, 0);
            max = Math.max(max, 0);
        }
        return new Rango(min, max);
    }
}
