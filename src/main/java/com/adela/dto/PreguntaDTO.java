package com.adela.dto;

import java.util.List;

import com.adela.calificacion.FormatoItem;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PreguntaDTO {
    private String pregunta;
    private int orden;
    private List<OpcionDTO> opciones;
    private FormatoItem formato;
    /** Solo MULTIPLE. Si no llega, 0. */
    private Integer minSelecciones;
    /** Solo MULTIPLE. null = todas las opciones. */
    private Integer maxSelecciones;
    /** Solo REPARTO, obligatorio ahí. */
    private Integer puntosRepartir;
    /** Si no llega, se conserva la regla anterior: múltiple opcional, el resto obligatoria. */
    private Boolean obligatoria;
}
