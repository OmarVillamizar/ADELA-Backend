package com.adela.dto;

import java.util.List;

import com.adela.entities.PreferenciaMultimodal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ResultCuestCompletoDTO extends ResultadoCuestionarioDTO {
    List<PreguntaResueltaDTO> preguntas;
    List<EstiloResultadoDTO> estilos;
    CalificacionDTO calificacion;
    // El perfil incluye todas las modalidades: aplica la pregunta de preferencia.
    boolean pidePreferencia;
    // Respuesta a esa pregunta; null si no aplica o aún no se declaró.
    PreferenciaMultimodal preferenciaMultimodal;
}
