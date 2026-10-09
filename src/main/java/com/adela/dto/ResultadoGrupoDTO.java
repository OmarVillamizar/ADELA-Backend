package com.adela.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class ResultadoGrupoDTO extends ResultadoGrupoResumidoDTO {
    private List<EstiloResultadoDTO> estilos;
    private CalificacionDTO calificacion;
    private List<ResultadoCuestionarioDTO> estudiantesResuelto;
    private List<ResultadoCuestionarioDTO> estudiantesNoResuelto;
    // Entre quienes tienen perfil de todas las modalidades: SELECTIVO,
    // INTEGRATIVO y SIN_DECLARAR. Null si nadie del grupo lo tiene.
    private Map<String, Long> preferenciasMultimodales;
}
