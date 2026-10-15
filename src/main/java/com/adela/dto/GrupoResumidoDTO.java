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
    /** Solo lo llena la proyección de la lista de grupos; en el resto va null. */
    private Integer numEstudiantes;
    
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

    /**
     * Sin conteo de estudiantes (PERF-02): se usa dentro de resultados y reportes,
     * donde nadie lo lee, y getEstudiantes().size() cargaba la colección LAZY
     * entera una vez por resultado. La lista de grupos cuenta en la consulta.
     */
    public static GrupoResumidoDTO from(Grupo g) {

        GrupoResumidoDTO gt = new GrupoResumidoDTO();
        if (g == null) {
            return gt;
        }
        gt.setId(g.getId());
        gt.setNombre(g.getNombre());
        gt.setProfesorEmail(g.getProfesor().getEmail());
        gt.setProfesorNombre(g.getProfesor().getNombre());
        return gt;
    }
}