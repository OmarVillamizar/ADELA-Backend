package com.adela.calificacion;

import java.util.List;

public record ConfigInterpretacion(EsquemaInterpretacion esquema, double delta, List<Banda> bandas,
        List<Escalon> escalones) {
    public ConfigInterpretacion {
        bandas = bandas == null ? List.of() : List.copyOf(bandas);
        escalones = escalones == null ? List.of() : List.copyOf(escalones);
    }
}
