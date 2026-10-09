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
    List<EstiloResultadoDTO> estilos;
    CalificacionDTO calificacion;
    // Pregunta complementaria, solo si le toca a este resultado.
    ComplementariaDTO.Pregunta complementaria;
    // Lo que eligió; null mientras no la responda.
    ComplementariaDTO.Respuesta respuestaComplementaria;
}
