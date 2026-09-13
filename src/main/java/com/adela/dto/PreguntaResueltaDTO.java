package com.adela.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PreguntaResueltaDTO {
    private String pregunta;
    private int orden;
    private List<String> respuestas;
}
