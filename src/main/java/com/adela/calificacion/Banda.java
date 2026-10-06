package com.adela.calificacion;

/** Nivel de un estilo: el valor pertenece a la banda si limiteInferior <= x <= limiteSuperior. */
public record Banda(long estiloId, EscalaBanda escala, double limiteInferior, double limiteSuperior,
        String etiqueta, int orden) {
}
