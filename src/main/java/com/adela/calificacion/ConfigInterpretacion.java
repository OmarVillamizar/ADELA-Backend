package com.adela.calificacion;

import java.util.List;

/** plano solo aplica con el esquema CUADRANTES; es null en los demás. */
public record ConfigInterpretacion(EsquemaInterpretacion esquema, double delta, List<Banda> bandas,
        List<Escalon> escalones, Plano plano) {
    public ConfigInterpretacion {
        bandas = bandas == null ? List.of() : List.copyOf(bandas);
        escalones = escalones == null ? List.of() : List.copyOf(escalones);
    }

    public ConfigInterpretacion(EsquemaInterpretacion esquema, double delta, List<Banda> bandas,
            List<Escalon> escalones) {
        this(esquema, delta, bandas, escalones, null);
    }
}
