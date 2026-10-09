package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.dto.CalificacionDTO;
import com.adela.dto.CalificacionDTO.PuntoPlanoDTO;
import com.adela.dto.EstiloResultadoDTO;
import com.adela.dto.InterpretacionDTO.PlanoDTO;
import com.adela.dto.ResultadoGrupoDTO;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estilo;
import com.adela.entities.Estudiante;
import com.adela.entities.Grupo;
import com.adela.entities.Opcion;
import com.adela.entities.PlanoCuadrantes;
import com.adela.entities.Pregunta;
import com.adela.entities.Profesor;
import com.adela.entities.ResultadoCuestionario;
import com.adela.entities.ResultadoPregunta;
import com.adela.exceptions.AppException;
import com.adela.repositories.BandaInterpretacionRepository;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.EscalonRelativoRepository;
import com.adela.repositories.PlanoCuadrantesRepository;
import com.adela.repositories.EstudianteRepository;
import com.adela.repositories.GrupoRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;
import com.adela.repositories.ResultadoPreguntaRepository;

/**
 * Reporte grupal y CSV calificados con el motor. Una pregunta única: opción 11
 * suma 1 a Visual, opción 12 suma 1 a Auditivo. Ana elige 11, Luis 12, Eva no
 * responde. Con RELATIVO, el perfil de cada uno es el estilo que puntuó.
 */
class ReporteGrupoTest {

    private final ResultadoCuestionarioRepository resultados = mock(ResultadoCuestionarioRepository.class);
    private final CuestionarioRepository cuestionarios = mock(CuestionarioRepository.class);
    private final GrupoRepository grupos = mock(GrupoRepository.class);
    private final PlanoCuadrantesRepository planos = mock(PlanoCuadrantesRepository.class);

    private final ResultadoCuestionarioService service = new ResultadoCuestionarioService(resultados,
            mock(ResultadoPreguntaRepository.class), cuestionarios, grupos, mock(EstudianteRepository.class), null,
            new CalificacionService(mock(BandaInterpretacionRepository.class),
                    mock(EscalonRelativoRepository.class), planos));

    private Profesor profesor;

    @BeforeEach
    void preparar() {
        profesor = new Profesor();
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
        when(resultados.findByGrupoAndCuestionario(grupo, c)).thenReturn(List.of(
                resultado(c, grupo, "luis@ufps.edu.co", "=Luis", a),
                resultado(c, grupo, "ana@ufps.edu.co", "Pérez, Ana", v),
                resultado(c, grupo, "eva@ufps.edu.co", "Eva", null)));
    }

    @Test
    @DisplayName("Media del puntaje directo, estadísticos, rango teórico y distribución de perfiles")
    void reporteGrupal() {
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

    @Test
    @DisplayName("Con cuadrantes el reporte trae el plano y un punto anónimo por resultado calculable")
    void puntosPlano() {
        Cuestionario c = cuestionarios.findById(1L).orElseThrow();
        c.setEsquemaInterpretacion(EsquemaInterpretacion.CUADRANTES);
        PlanoCuadrantes p = new PlanoCuadrantes();
        p.setCuestionarioId(1L);
        p.setEjeX(c.getEstilos().stream().filter(e -> e.getId() == 1L).findFirst().orElseThrow());
        p.setEjeY(c.getEstilos().stream().filter(e -> e.getId() == 2L).findFirst().orElseThrow());
        p.setCorteX(0.5);
        p.setCorteY(0.5);
        p.setXAltoYAlto("Alto");
        p.setXBajoYAlto("Asimilador");
        p.setXBajoYBajo("Bajo");
        p.setXAltoYBajo("Acomodador");
        when(planos.findById(1L)).thenReturn(Optional.of(p));

        CalificacionDTO k = service.obtenerResultadosGrupoCuestionario(1L, 7, profesor).getCalificacion();

        assertEquals(new PlanoDTO("Visual", "Auditivo", 0.5, 0.5, "Alto", "Asimilador", "Bajo", "Acomodador", false, false),
                k.plano());
        // Luis (Auditivo) y Ana (Visual); Eva no respondió y no aporta punto.
        assertEquals(List.of(new PuntoPlanoDTO(0, 1), new PuntoPlanoDTO(1, 0)), k.puntosPlano());
        assertEquals(Map.of("Asimilador", 1L, "Acomodador", 1L), k.distribucionPerfiles());
    }

    @Test
    @DisplayName("Sin cuadrantes no hay plano ni puntos")
    void sinPlano() {
        CalificacionDTO k = service.obtenerResultadosGrupoCuestionario(1L, 7, profesor).getCalificacion();
        assertNull(k.plano());
        assertNull(k.puntosPlano());
    }

    @Test
    @DisplayName("CSV RFC 4180: una fila por estudiante resuelto y estilo, comillas y fórmulas neutralizadas")
    void csvRfc() {
        String[] lineas = service.exportarCsv(1L, 7, profesor, "rfc4180").split("\r\n");

        assertEquals(5, lineas.length);
        assertEquals("estudiante_email,estudiante_nombre,estilo,tipo,puntaje,rango_min,rango_max,pomp,nivel,"
                + "dominante,perfil,version_motor", lineas[0]);
        assertEquals("ana@ufps.edu.co,\"Pérez, Ana\",Visual,PRIMARIO,1,0,1,100,,si,Visual,2.0.0", lineas[1]);
        assertEquals("ana@ufps.edu.co,\"Pérez, Ana\",Auditivo,PRIMARIO,0,0,1,0,,no,Visual,2.0.0", lineas[2]);
        assertTrue(lineas[3].startsWith("luis@ufps.edu.co,'=Luis,Visual,PRIMARIO,0"));
    }

    @Test
    @DisplayName("CSV para Excel: BOM, punto y coma y coma decimal; un formato desconocido es VALIDACION")
    void csvExcel() {
        String csv = service.exportarCsv(1L, 7, profesor, "EXCEL");

        assertTrue(csv.startsWith("﻿estudiante_email;estudiante_nombre;"));
        assertTrue(csv.contains("ana@ufps.edu.co;Pérez, Ana;Visual;PRIMARIO;1;0;1;100;;si;Visual;2.0.0"));
        assertEquals("33,3333", new Csv(Csv.Formato.EXCEL).numero(100d / 3));
        assertEquals("33.3333", new Csv(Csv.Formato.RFC4180).numero(100d / 3));
        assertThrows(AppException.class, () -> service.exportarCsv(1L, 7, profesor, "xlsx"));
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
        o.setPesos(Map.of(e.getId(), 1d));
        return o;
    }

    private static ResultadoCuestionario resultado(Cuestionario c, Grupo g, String email, String nombre,
            Opcion elegida) {
        Estudiante e = new Estudiante();
        e.setEmail(email);
        e.setNombre(nombre);
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
