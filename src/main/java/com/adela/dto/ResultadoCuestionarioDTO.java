package com.adela.dto;

import java.sql.Date;
import java.util.List;

import com.adela.entities.ResultadoCuestionario;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResultadoCuestionarioDTO {
    private CuestionarioResumidoDTO cuestionario;
    private EstudianteDTO estudiante;
    private Date fechaAplicacion;
    private Date fechaResolucion;
    private GrupoResumidoDTO grupo;
    private Long id;
    // Solo en el reporte de grupo y con el cuestionario resuelto: el resumen
    // por género y edad cruza el perfil de cada estudiante con sus datos.
    private String perfil;
    private List<String> dominantes;

    public static ResultadoCuestionarioDTO from(ResultadoCuestionario rc) {
        ResultadoCuestionarioDTO rcd = new ResultadoCuestionarioDTO();
        rcd.setCuestionario(CuestionarioResumidoDTO.from(rc.getCuestionario()));
        rcd.setEstudiante(EstudianteDTO.from(rc.getEstudiante()));
        rcd.setFechaAplicacion(rc.getFechaAplicacion());
        rcd.setFechaResolucion(rc.getFechaResolucion());
        rcd.setGrupo(GrupoResumidoDTO.from(rc.getGrupo()));
        rcd.setId(rc.getId());
        return rcd;
    }
}
