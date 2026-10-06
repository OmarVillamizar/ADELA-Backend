package com.adela.calificacion;

import java.util.Map;

/**
 * cantidades: opcionId -> a(o). 1 por opción marcada (UNICA, MULTIPLE), el
 * rango asignado (JERARQUIA) o los puntos (REPARTO). Mapa vacío = sin responder.
 */
public record RespuestaItem(long itemId, Map<Long, Double> cantidades) {
    public boolean respondido() {
        return cantidades != null && !cantidades.isEmpty();
    }
}
