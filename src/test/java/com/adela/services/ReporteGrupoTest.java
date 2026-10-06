package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.dto.EstiloResultadoDTO;
import com.adela.dto.ResultadoGrupoDTO;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estilo;
import com.adela.entities.Estudiante;
import com.adela.entities.Grupo;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.entities.Profesor;
import com.adela.entities.ResultadoCuestionario;
import com.adela.entities.ResultadoPregunta;
import com.adela.repositories.BandaInterpretacionRepository;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.EscalonRelativoRepository;
import com.adela.repositories.EstudianteRepository;
import com.adela.repositories.GrupoRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;
import com.adela.repositories.ResultadoPreguntaRepository;

/**
 * Reporte grupal calificado con el motor. Una pregunta única: opción 11 suma 1
 * a Visual, opción 12 suma 1 a Auditivo. Ana elige 11, Luis 12, Eva no responde.
 */
class ReporteGrupoTest {

    private final ResultadoCuestionarioRepository resultados = mock(ResultadoCuestionarioRepository.class);
    private final CuestionarioRepository cuestionarios = mock(CuestionarioRepository.class);
    private final GrupoRepository grupos = mock(GrupoRepository.class);

    private final ResultadoCuestionarioService service = new ResultadoCuestionarioService(resultados,
            mock(ResultadoPreguntaRepository.class), cuestionarios, grupos, mock(EstudianteRepository.class), null,
            new CalificacionService(mock(BandaInterpretacionRepository.class),
                    mock(EscalonRelativoRepository.class)));

    @Test
    @DisplayName("Media del puntaje directo, estadísticos, rango teórico y distribución de perfiles")
    void reporteGrupal() {
        Profesor profesor = new Profesor();
        profesor.setEmail("profe@ufps.edu.co");
        Grupo grupo = new Grupo();
        grupo.setId(7);
        grupo.setProfesor(profesor);
        grupo.setEstudiantes(new HashSet<>());

        Cuestionario c = new Cuestionario();
        c.setId(1L);
        c.setEsquemaInterpretacion(EsquemaInterpretacion.RELATIVO);
        Estilo visual = estilo(1L, "Visual", c);
        Estilo auditivo = estilo(2L, "Auditivo", c);
        Pregunta p = new Pregunta();
        p.setId(100L);
        p.setCuestionario(c);
        Opcion v = opcion(11L, p, visual);
        Opcion a = opcion(12L, p, auditivo);
        p.getOpciones().addAll(List.of(v, a));
        c.getPreguntas().add(p);
        c.getEstilos().addAll(List.of(visual, auditivo));

        when(cuestionarios.findById(1L)).thenReturn(Optional.of(c));
        when(grupos.findById(7)).thenReturn(Optional.of(grupo));
        when(resultados.findByGrupoAndCuestionario(grupo, c)).thenReturn(
                List.of(resultado(c, grupo, "ana@ufps.edu.co", v), resultado(c, grupo, "luis@ufps.edu.co", a),
                        resultado(c, grupo, "eva@ufps.edu.co", null)));

        ResultadoGrupoDTO r = service.obtenerResultadosGrupoCuestionario(1L, 7, profesor);

        assertEquals(2, r.getEstudiantesResuelto().size());
        assertEquals(1, r.getEstudiantesNoResuelto().size());
        EstiloResultadoDTO ev = r.getEstilos().get(0);
        assertEquals("Visual", ev.getNombre());
        assertEquals(0.5, ev.getValor(), 1e-9);
        assertEquals(0d, ev.getRangoMin());
        assertEquals(1d, ev.getRangoMax());
        assertEquals(2, ev.getEstadisticaBruto().n());
        assertEquals(50d, ev.getEstadisticaPomp().media(), 1e-9);
        assertTrue(r.getCalificacion().rangosHomogeneos());
        assertEquals(Map.of("Visual", 1L, "Auditivo", 1L), r.getCalificacion().distribucionPerfiles());
    }

    private static Estilo estilo(Long id, String nombre, Cuestionario c) {
        Estilo e = new Estilo();
        e.setId(id);
        e.setNombre(nombre);
        e.setCuestionario(c);
        return e;
    }

    private static Opcion opcion(Long id, Pregunta p, Estilo e) {
        Opcion o = new Opcion();
        o.setId(id);
        o.setPregunta(p);
        o.setEstilo(e);
        o.setValor(1d);
        return o;
    }

    private static ResultadoCuestionario resultado(Cuestionario c, Grupo g, String email, Opcion elegida) {
        Estudiante e = new Estudiante();
        e.setEmail(email);
        ResultadoCuestionario rc = new ResultadoCuestionario();
        rc.setCuestionario(c);
        rc.setGrupo(g);
        rc.setEstudiante(e);
        if (elegida != null) {
            rc.setFechaResolucion(Date.valueOf("2026-10-06"));
            ResultadoPregunta rp = new ResultadoPregunta();
            rp.setCuestionario(rc);
            rp.setOpcion(elegida);
            rc.getPreguntas().add(rp);
        }
        return rc;
    }
}
