package com.adela.dto;

import com.adela.entities.Grupo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GrupoResumidoDTO {
    private int id;
    private String nombre;
    private String profesorNombre;
    private String profesorEmail;
    private int numEstudiantes;
    
    /**
     * Constructor para la proyección JPQL de GrupoRepository.resumirPorProfesor.
     * COUNT devuelve long; el DTO expone int porque es lo que ya consumía el cliente.
     */
    public GrupoResumidoDTO(int id, String nombre, String profesorNombre, String profesorEmail,
            long numEstudiantes) {
        this.id = id;
        this.nombre = nombre;
        this.profesorNombre = profesorNombre;
        this.profesorEmail = profesorEmail;
        this.numEstudiantes = (int) numEstudiantes;
    }

    public static GrupoResumidoDTO from(Grupo g) {
        
        GrupoResumidoDTO gt = new GrupoResumidoDTO();
        if (g == null) {
            return gt;
        }
        gt.setId(g.getId());
        gt.setNombre(g.getNombre());
        gt.setNumEstudiantes(g.getEstudiantes().size());
        gt.setProfesorEmail(g.getProfesor().getEmail());
        gt.setProfesorNombre(g.getProfesor().getNombre());
        return gt;
    }
}