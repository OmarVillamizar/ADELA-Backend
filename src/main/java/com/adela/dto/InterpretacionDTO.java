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
 *
 * complementaria es la pregunta extra que se hace cuando el perfil destaca
 * todos los estilos; null = sin pregunta. Solo con RELATIVO o
 * RELATIVO_ESCALONADO, los esquemas que destacan varios estilos.
 */
public record InterpretacionDTO(EsquemaInterpretacion esquema, Double delta, Boolean esIpsativo,
        List<BandaDTO> bandas, List<EscalonDTO> escalones, PlanoDTO plano, List<EstiloLecturaDTO> estilos,
        ComplementariaConfigDTO complementaria) {

    public InterpretacionDTO(EsquemaInterpretacion esquema, Double delta, Boolean esIpsativo,
            List<BandaDTO> bandas, List<EscalonDTO> escalones) {
        this(esquema, delta, esIpsativo, bandas, escalones, null, null, null);
    }

    public InterpretacionDTO(EsquemaInterpretacion esquema, Double delta, Boolean esIpsativo,
            List<BandaDTO> bandas, List<EscalonDTO> escalones, PlanoDTO plano, List<EstiloLecturaDTO> estilos) {
        this(esquema, delta, esIpsativo, bandas, escalones, plano, estilos, null);
    }

    public record BandaDTO(String estilo, EscalaBanda escala, Double limiteInferior, Double limiteSuperior,
            String etiqueta, Integer orden) {
    }

    public record EscalonDTO(Double totalMin, Double totalMax, Double distancia) {
    }

    /**
     * Las esquinas se nombran por puntaje (alto = por encima del corte), no por
     * posición. invertirX / invertirY solo cambian el dibujo: el lado alto va a
     * la izquierda o abajo, como en la rejilla del inventario de Kolb 3.1.
     */
    public record PlanoDTO(String ejeX, String ejeY, Double corteX, Double corteY, String xAltoYAlto,
            String xBajoYAlto, String xBajoYBajo, String xAltoYBajo, Boolean invertirX, Boolean invertirY) {
    }

    public record EstiloLecturaDTO(String nombre, TipoEstilo tipo) {
    }

    public record ComplementariaConfigDTO(String titulo, String introduccion, String enunciado, String nota,
            List<OpcionComplementariaDTO> opciones) {
    }

    /** texto y descripcion: al responder. resultado y resultadoDescripcion: en el reporte. */
    public record OpcionComplementariaDTO(String texto, String descripcion, String resultado,
            String resultadoDescripcion) {
    }
}
