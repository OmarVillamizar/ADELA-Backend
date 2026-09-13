package com.adela.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RespuestaCuestionarioDTO {
    private Long cuestionarioId;
    // Id de la asignación concreta (ResultadoCuestionario) que se está respondiendo.
    // Necesario cuando el mismo cuestionario está asignado al estudiante en varios grupos.
    private Long resultadoCuestionarioId;
    private List<Long> opcionesSeleccionadasId;
}
