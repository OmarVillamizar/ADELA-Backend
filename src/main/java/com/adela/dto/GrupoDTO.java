package com.adela.dto;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GrupoDTO {
    
    private String nombre;
    
    private Set<EstudianteCrearDTO> estudiantes; // Usaremos los correos de los estudiantes
    
}