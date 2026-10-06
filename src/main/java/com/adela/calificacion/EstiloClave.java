package com.adela.calificacion;

import java.util.Map;

/** coeficientes: solo para COMPUESTO, primarioId -> c(k,e). Un compuesto solo referencia primarios. */
public record EstiloClave(long estiloId, String nombre, TipoEstilo tipo, int orden, Map<Long, Double> coeficientes) {
    public EstiloClave {
        coeficientes = coeficientes == null ? Map.of() : Map.copyOf(coeficientes);
    }
}
