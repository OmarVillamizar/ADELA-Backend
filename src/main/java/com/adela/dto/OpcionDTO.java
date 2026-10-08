package com.adela.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OpcionDTO {
    private int orden;
    private String respuesta;
    /** Puede sumar a varios estilos primarios. Vacía = la opción no puntúa (el "−" del CHAEA). */
    private List<PesoDTO> pesos;

    /** estiloId es el id local de 'estilos' en el JSON, no el de la BD. */
    public record PesoDTO(int estiloId, Double peso) {
    }
}
