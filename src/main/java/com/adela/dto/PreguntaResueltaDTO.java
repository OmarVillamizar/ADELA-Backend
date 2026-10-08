package com.adela.dto;

import java.util.List;

import com.adela.calificacion.FormatoItem;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PreguntaResueltaDTO {
    private String pregunta;
    private int orden;
    private FormatoItem formato;
    /** En jerarquía y reparto, de mayor a menor cantidad; en el resto, en el orden de las opciones. */
    private List<RespuestaElegidaDTO> respuestas;

    /** cantidad: 1 si se marcó, el rango (jerarquía) o los puntos (reparto). */
    public record RespuestaElegidaDTO(String texto, double cantidad) {
    }
}
