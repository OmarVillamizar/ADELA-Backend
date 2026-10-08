package com.adela.dto;

import java.util.List;

import com.adela.calificacion.TipoEstilo;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EstiloDTO {
    private String nombre;
    private int id; // No es el id de la bd
    /** Si no llega, PRIMARIO. */
    private TipoEstilo tipo;
    /** Solo COMPUESTO: combinación lineal de primarios (AC-CE = AC +1, CE -1). */
    private List<CoeficienteDTO> coeficientes;

    /** estiloId es el id local del primario, no el de la BD. */
    public record CoeficienteDTO(int estiloId, Double coeficiente) {
    }
}
