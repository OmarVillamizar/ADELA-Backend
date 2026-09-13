package com.adela.dto;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class ResultadoGrupoDTO extends ResultadoGrupoResumidoDTO {
    private List<CategoriaResultadoDTO> categorias;
    private List<ResultadoCuestionarioDTO> estudiantesResuelto;
    private List<ResultadoCuestionarioDTO> estudiantesNoResuelto;
}
