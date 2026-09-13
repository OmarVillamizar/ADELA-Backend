package com.adela.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ListasCuestionariosDTO {
    private List<ResultadoCuestionarioDTO> resueltos;
    private List<ResultadoCuestionarioDTO> pendientes;
}
