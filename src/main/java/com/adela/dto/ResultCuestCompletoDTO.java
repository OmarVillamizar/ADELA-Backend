package com.adela.dto;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ResultCuestCompletoDTO extends ResultadoCuestionarioDTO {
    List<PreguntaResueltaDTO> preguntas;
    List<CategoriaResultadoDTO> categorias;
}
