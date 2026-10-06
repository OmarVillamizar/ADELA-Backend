package com.adela.calificacion;

import java.util.Map;

/** Opción con sus pesos por estilo primario: estiloId -> w(o,e). Un estilo ausente pesa 0. */
public record OpcionClave(long opcionId, Map<Long, Double> pesos) {
    public OpcionClave {
        pesos = Map.copyOf(pesos);
    }

    public double peso(long estiloId) {
        return pesos.getOrDefault(estiloId, 0.0);
    }
}
