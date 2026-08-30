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

import jakarta.persistence.EntityNotFoundException;

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
        try {
            Cuestionario cuestionario = cuestionarioService.crearCuestionario(cuestionarioDTO);
            return ResponseEntity.ok(cuestionario);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error creando cuestionario: " + e.getMessage());
        }
    }
    
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PROFESOR')")
    public ResponseEntity<?> listarCuestionarios() {
        try {
            return ResponseEntity.ok(cuestionarioService.getCuestionarios());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error creando repositorio: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PROFESOR') or hasRole('ESTUDIANTE')")
    public ResponseEntity<?> obtenerCuestionario(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(cuestionarioService.getCuestionarioPorId(id));
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> eliminarCuestionario(@PathVariable Long id) {
        try {
            cuestionarioService.eliminarCuestionario(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
    
    @PostMapping("/{idCuestionario}/asignargrupo/{idGrupo}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PROFESOR')")
    public ResponseEntity<?> asignarCuestionarioAGrupo(@PathVariable Long idCuestionario, @PathVariable int idGrupo) {
        try {
            resultadoCuestionarioService.asignarCuestionarioAGrupo(idCuestionario, idGrupo);
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }
    
    @PostMapping("/{idCuestionario}/asignarestudiante")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PROFESOR')")
    public ResponseEntity<?> asignarCuestionarioAEstudiante(@PathVariable Long idCuestionario,
            @RequestBody RequestEstudianteEmail estudianteEmail) {
        try {
            resultadoCuestionarioService.asignarCuestionarioAEstudiante(idCuestionario, estudianteEmail.getEmail());
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }
    
    @PostMapping("/responder")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<?> responderCuestionario(@RequestBody RespuestaCuestionarioDTO respuesta) {
        try {
            Estudiante estudiante = (Estudiante) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            ;
            resultadoCuestionarioService.responderCuestionario(respuesta, estudiante);
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
    
    @GetMapping("/mis-cuestionarios")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<?> obtenerMisCuestionarios() {
        try {
            Estudiante estudiante = (Estudiante) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            ;
            return new ResponseEntity<>(resultadoCuestionarioService.obtenerCuestionarios(estudiante), HttpStatus.OK);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
    
    @GetMapping("/mis-cuestionarios/resuelto/{idResultado}")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<?> obtenerResultadoCuestionario(@PathVariable Long idResultado) {
        try {
            Estudiante estudiante = (Estudiante) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return new ResponseEntity<>(
                    resultadoCuestionarioService.obtenerResultadoCuestionario(idResultado, estudiante), HttpStatus.OK);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
    
    @GetMapping("/reporte/{idCuestionario}/grupo/{idGrupo}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> obtenerReporteGrupo(@PathVariable Long idCuestionario, @PathVariable Integer idGrupo) {
        try {
            Profesor profesor = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return new ResponseEntity<>(
                    resultadoCuestionarioService.obtenerResultadosGrupoCuestionario(idCuestionario, idGrupo, profesor),
                    HttpStatus.OK);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
    
    @GetMapping("/reporte-estudiante/{idCuestionarioResuelto}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> obtenerReporteEstudiante(@PathVariable Long idCuestionarioResuelto) {
        try {
            Profesor profesor = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return new ResponseEntity<>(
                    resultadoCuestionarioService.obtenerResultadoCuestionario(idCuestionarioResuelto, profesor),
                    HttpStatus.OK);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
    
    @GetMapping("/reporte/grupo/{idGrupo}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> obtenerCuestionariosGrupo(@PathVariable Integer idGrupo) {
        try {
            Profesor profesor = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return new ResponseEntity<>(resultadoCuestionarioService.obtenerPorGrupo(idGrupo, profesor),
                    HttpStatus.OK);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
    
    @PatchMapping("/reporte/{idCuestionario}/grupo/{idGrupo}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> toggleBloqueo(@PathVariable Long idCuestionario, @PathVariable Integer idGrupo) {
        try {
            Profesor profesor = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            resultadoCuestionarioService.toggleBloqueoCuestionario(idCuestionario, idGrupo, profesor);
            return new ResponseEntity<>("ok", HttpStatus.OK);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
