package com.adela.dto;

import java.util.List;

import com.adela.calificacion.EscalaBanda;
import com.adela.calificacion.EsquemaInterpretacion;

/**
 * Cómo se interpreta un cuestionario: regla de dominancia, bandas de nivel por
 * estilo (baremo del manual) y tabla de distancia de paso (VARK). Es el baremo
 * completo, así que solo lo lee y escribe el administrador.
 *
 * Las bandas nombran el estilo por su nombre: el administrador no ve los ids.
 */
public record InterpretacionDTO(EsquemaInterpretacion esquema, Double delta, Boolean esIpsativo,
        List<BandaDTO> bandas, List<EscalonDTO> escalones) {

    public record BandaDTO(String estilo, EscalaBanda escala, Double limiteInferior, Double limiteSuperior,
            String etiqueta, Integer orden) {
    }

    public record EscalonDTO(Double totalMin, Double totalMax, Double distancia) {
    }
}
