package com.adela.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CuestionarioDTO {
    private String nombre;
    private String siglas;
    private String descripcion;
    private String autor;
    private String version;
    private List<PreguntaDTO> preguntas;
    private List<CategoriaDTO> categorias;
}
