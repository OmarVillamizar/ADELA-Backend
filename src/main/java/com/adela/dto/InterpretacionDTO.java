package com.adela.dto;

import java.util.List;

import com.adela.calificacion.EscalaBanda;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.TipoEstilo;

/**
 * Cómo se interpreta un cuestionario: regla de dominancia, bandas de nivel por
 * estilo (baremo del manual), tabla de distancia de paso (VARK) y plano de
 * cuadrantes. Es el baremo completo, así que solo lo lee y escribe el
 * administrador.
 *
 * Las bandas y los ejes del plano nombran el estilo por su nombre: el
 * administrador no ve los ids. estilos es de solo lectura: el GET lo llena para
 * que el editor sepa cuáles son compuestos y el PUT lo ignora.
 */
public record InterpretacionDTO(EsquemaInterpretacion esquema, Double delta, Boolean esIpsativo,
        List<BandaDTO> bandas, List<EscalonDTO> escalones, PlanoDTO plano, List<EstiloLecturaDTO> estilos) {

    public InterpretacionDTO(EsquemaInterpretacion esquema, Double delta, Boolean esIpsativo,
            List<BandaDTO> bandas, List<EscalonDTO> escalones) {
        this(esquema, delta, esIpsativo, bandas, escalones, null, null);
    }

    public record BandaDTO(String estilo, EscalaBanda escala, Double limiteInferior, Double limiteSuperior,
            String etiqueta, Integer orden) {
    }

    public record EscalonDTO(Double totalMin, Double totalMax, Double distancia) {
    }

    public record PlanoDTO(String ejeX, String ejeY, Double corteX, Double corteY, String xAltoYAlto,
            String xBajoYAlto, String xBajoYBajo, String xAltoYBajo) {
    }

    public record EstiloLecturaDTO(String nombre, TipoEstilo tipo) {
    }
}
