package com.adela.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PreguntaDTO {
    private String pregunta;
    private int orden;
    private List<OpcionDTO> opciones;
    private boolean opcionMultiple;
}
