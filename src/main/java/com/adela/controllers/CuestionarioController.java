package com.adela.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adela.dto.CuestionarioDTO;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.RequestEstudianteEmail;
import com.adela.dto.RespuestaCuestionarioDTO;
import com.adela.dto.ResultCuestCompletoDTO;
import com.adela.entities.Estudiante;
import com.adela.entities.Insignia;
import com.adela.entities.Profesor;
import com.adela.services.CuestionarioService;
import com.adela.services.InsigniaService;
import com.adela.services.InterpretacionService;
import com.adela.services.ResultadoCuestionarioService;

import lombok.RequiredArgsConstructor;

/**
 * Los try/catch por método desaparecieron: GlobalExceptionHandler traduce
 * EntityNotFoundException a 404 y AppException al status de su ErrorCode. Antes
 * cada bloque devolvía 400 con e.getMessage(), lo que disfrazaba de error de
 * cliente cualquier fallo de infraestructura.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/cuestionarios")
public class CuestionarioController {

    private final CuestionarioService cuestionarioService;

    private final ResultadoCuestionarioService resultadoCuestionarioService;

    private final InsigniaService insigniaService;
    
    private final InterpretacionService interpretacionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> crearCuestionario(@RequestBody CuestionarioDTO cuestionarioDTO) {
        // Resumen, no la entidad: devolverla entera arrastraba preguntas, opciones y
        // el baremo completo en la respuesta de creacion.
        return ResponseEntity.ok(cuestionarioService.crearCuestionarioDTO(cuestionarioDTO));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PROFESOR')")
    public ResponseEntity<?> listarCuestionarios() {
        return ResponseEntity.ok(cuestionarioService.getCuestionarios());
    }

    /**
     * Devuelve el cuestionario sin el baremo. Antes serializaba la entidad completa,
     * incluido Opcion.valor, así que el estudiante veía cuánto puntúa cada respuesta
     * antes de contestar.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PROFESOR') or hasRole('ESTUDIANTE')")
    public ResponseEntity<?> obtenerCuestionario(@PathVariable Long id) {
        return ResponseEntity.ok(cuestionarioService.obtenerParaResponder(id));
    }

    /** Baremo y reglas de interpretación: solo el administrador, nunca en el DTO de responder. */
    @GetMapping("/{id}/interpretacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> obtenerInterpretacion(@PathVariable Long id) {
        return ResponseEntity.ok(interpretacionService.obtener(id));
    }

    @PutMapping("/{id}/interpretacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> guardarInterpretacion(@PathVariable Long id, @RequestBody InterpretacionDTO dto) {
        return ResponseEntity.ok(interpretacionService.guardar(id, dto));
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
        insigniaService.otorgar(estudiante.getEmail(), Insignia.PRIMER_CUESTIONARIO);
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
        ResultCuestCompletoDTO resultado = resultadoCuestionarioService.obtenerResultadoCuestionario(idResultado,
                estudiante);
        // Despues de la lectura: si el resultado no es suyo o no esta resuelto, la
        // excepcion sale antes y no se otorga nada.
        insigniaService.otorgar(estudiante.getEmail(), Insignia.PRIMER_REPORTE);
        return new ResponseEntity<>(resultado, HttpStatus.OK);
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
