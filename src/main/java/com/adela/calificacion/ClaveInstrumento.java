package com.adela.calificacion;

import java.util.List;

/** Todo lo que el motor necesita de un cuestionario, sin depender de JPA. */
public record ClaveInstrumento(long cuestionarioId, boolean permitirOmisiones, List<ItemClave> items,
        List<EstiloClave> estilos, ConfigInterpretacion config) {
    public ClaveInstrumento {
        items = List.copyOf(items);
        estilos = List.copyOf(estilos);
    }
}
