package com.example.chaea.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.chaea.dto.CuestionarioDTO;
import com.example.chaea.dto.RequestEstudianteEmail;
import com.example.chaea.dto.RespuestaCuestionarioDTO;
import com.example.chaea.entities.Cuestionario;
import com.example.chaea.entities.Estudiante;
import com.example.chaea.entities.Profesor;
import com.example.chaea.services.CuestionarioService;
import com.example.chaea.services.ResultadoCuestionarioService;

/**
 * Los try/catch por método desaparecieron: GlobalExceptionHandler traduce
 * EntityNotFoundException a 404 y AppException al status de su ErrorCode. Antes
 * cada bloque devolvía 400 con e.getMessage(), lo que disfrazaba de error de
 * cliente cualquier fallo de infraestructura.
 */
@RestController
@RequestMapping("/api/cuestionarios")
public class CuestionarioController {

    @Autowired
    private CuestionarioService cuestionarioService;

    @Autowired
    private ResultadoCuestionarioService resultadoCuestionarioService;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> crearCuestionario(@RequestBody CuestionarioDTO cuestionarioDTO) {
        Cuestionario cuestionario = cuestionarioService.crearCuestionario(cuestionarioDTO);
        return ResponseEntity.ok(cuestionario);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PROFESOR')")
    public ResponseEntity<?> listarCuestionarios() {
        return ResponseEntity.ok(cuestionarioService.getCuestionarios());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PROFESOR') or hasRole('ESTUDIANTE')")
    public ResponseEntity<?> obtenerCuestionario(@PathVariable Long id) {
        return ResponseEntity.ok(cuestionarioService.getCuestionarioPorId(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> eliminarCuestionario(@PathVariable Long id) {
        cuestionarioService.eliminarCuestionario(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/{idCuestionario}/asignargrupo/{idGrupo}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PROFESOR')")
    public ResponseEntity<?> asignarCuestionarioAGrupo(@PathVariable Long idCuestionario, @PathVariable int idGrupo) {
        resultadoCuestionarioService.asignarCuestionarioAGrupo(idCuestionario, idGrupo);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("/{idCuestionario}/asignarestudiante")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PROFESOR')")
    public ResponseEntity<?> asignarCuestionarioAEstudiante(@PathVariable Long idCuestionario,
            @RequestBody RequestEstudianteEmail estudianteEmail) {
        resultadoCuestionarioService.asignarCuestionarioAEstudiante(idCuestionario, estudianteEmail.getEmail());
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("/responder")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<?> responderCuestionario(@RequestBody RespuestaCuestionarioDTO respuesta) {
        Estudiante estudiante = (Estudiante) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        resultadoCuestionarioService.responderCuestionario(respuesta, estudiante);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/mis-cuestionarios")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<?> obtenerMisCuestionarios() {
        Estudiante estudiante = (Estudiante) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new ResponseEntity<>(resultadoCuestionarioService.obtenerCuestionarios(estudiante), HttpStatus.OK);
    }

    @GetMapping("/mis-cuestionarios/resuelto/{idResultado}")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<?> obtenerResultadoCuestionario(@PathVariable Long idResultado) {
        Estudiante estudiante = (Estudiante) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new ResponseEntity<>(resultadoCuestionarioService.obtenerResultadoCuestionario(idResultado, estudiante),
                HttpStatus.OK);
    }

    @GetMapping("/reporte/{idCuestionario}/grupo/{idGrupo}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> obtenerReporteGrupo(@PathVariable Long idCuestionario, @PathVariable Integer idGrupo) {
        Profesor profesor = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new ResponseEntity<>(
                resultadoCuestionarioService.obtenerResultadosGrupoCuestionario(idCuestionario, idGrupo, profesor),
                HttpStatus.OK);
    }

    @GetMapping("/reporte-estudiante/{idCuestionarioResuelto}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> obtenerReporteEstudiante(@PathVariable Long idCuestionarioResuelto) {
        Profesor profesor = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new ResponseEntity<>(
                resultadoCuestionarioService.obtenerResultadoCuestionario(idCuestionarioResuelto, profesor),
                HttpStatus.OK);
    }

    @GetMapping("/reporte/grupo/{idGrupo}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> obtenerCuestionariosGrupo(@PathVariable Integer idGrupo) {
        Profesor profesor = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new ResponseEntity<>(resultadoCuestionarioService.obtenerPorGrupo(idGrupo, profesor), HttpStatus.OK);
    }

    @PatchMapping("/reporte/{idCuestionario}/grupo/{idGrupo}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> toggleBloqueo(@PathVariable Long idCuestionario, @PathVariable Integer idGrupo) {
        Profesor profesor = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        resultadoCuestionarioService.toggleBloqueoCuestionario(idCuestionario, idGrupo, profesor);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
