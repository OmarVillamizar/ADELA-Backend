package com.adela.dto;

import com.adela.entities.Cuestionario;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CuestionarioResumidoDTO {
    private Long id;
    private String nombre;
    private String siglas;
    private String descripcion;
    private String autor;
    private String version;
    private int numPreguntas;
    /** General de ADELA (sin dueño) o personal de quien consulta. */
    private boolean general;
    /** Quien consulta puede archivarlo y editar su interpretación. Lo decide el servidor. */
    private boolean editable;

    /** Constructor para la proyección JPQL de CuestionarioRepository.resumirVisibles. */
    public CuestionarioResumidoDTO(Long id, String nombre, String siglas, String version, String autor,
            String descripcion, long numPreguntas, boolean general) {
        this.id = id;
        this.nombre = nombre;
        this.siglas = siglas;
        this.version = version;
        this.autor = autor;
        this.descripcion = descripcion;
        this.numPreguntas = (int) numPreguntas;
        this.general = general;
    }

    public static CuestionarioResumidoDTO from(Cuestionario cuestionario) {
        CuestionarioResumidoDTO res = new CuestionarioResumidoDTO();
        res.setAutor(cuestionario.getAutor());
        res.setDescripcion(cuestionario.getDescripcion());
        res.setId(cuestionario.getId());
        res.setNombre(cuestionario.getNombre());
        res.setNumPreguntas(cuestionario.getPreguntas().size());
        res.setSiglas(cuestionario.getSiglas());
        res.setVersion(cuestionario.getVersion());
        res.setGeneral(cuestionario.getProfesor() == null);
        return res;
    }
}
