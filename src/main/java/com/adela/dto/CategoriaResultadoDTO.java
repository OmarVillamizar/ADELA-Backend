package com.adela.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CategoriaResultadoDTO {
    private String nombre;
    private Double valorMinimo;
    private Double valorMaximo;
    private Double valor;
}
