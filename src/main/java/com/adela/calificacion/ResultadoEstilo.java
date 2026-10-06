package com.adela.calificacion;

/**
 * Puntaje de un estilo. bruto y pomp son null si el estado es NO_CALCULABLE;
 * pomp también si el rango es degenerado. banda es la etiqueta del nivel.
 */
public record ResultadoEstilo(long estiloId, String nombre, TipoEstilo tipo, int orden, Double bruto,
        double rangoMin, double rangoMax, Double pomp, EstadoCalculo estado, String banda, boolean dominante) {

    ResultadoEstilo conInterpretacion(String banda, boolean dominante) {
        return new ResultadoEstilo(estiloId, nombre, tipo, orden, bruto, rangoMin, rangoMax, pomp, estado, banda,
                dominante);
    }
}
