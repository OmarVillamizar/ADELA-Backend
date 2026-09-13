package com.adela.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adela.dto.GrupoDTO;
import com.adela.dto.GrupoDetalleDTO;
import com.adela.dto.GrupoResumidoDTO;
import com.adela.entities.Profesor;
import com.adela.services.GrupoService;

import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

/**
 * Solo resuelve el profesor autenticado y delega. La lógica y las fronteras
 * transaccionales viven en GrupoService.
 */
@RequiredArgsConstructor
@Tag(name = "Grupos", description = "Gestión de grupos y de sus estudiantes")
@RestController
@RequestMapping("/api/grupos")
public class GrupoController {

    private final GrupoService grupoService;

    private Profesor autenticado() {
        return (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @PostMapping
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<GrupoDetalleDTO> crearGrupo(@RequestBody GrupoDTO grupoDTO) {
        return ResponseEntity.ok(grupoService.crear(grupoDTO, autenticado()));
    }

    @GetMapping
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<GrupoResumidoDTO>> listarGrupos() {
        return ResponseEntity.ok(grupoService.listarDelProfesor(autenticado()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<GrupoDetalleDTO> consultarGrupoPorId(@PathVariable int id) {
        return ResponseEntity.ok(grupoService.consultarPorId(id, autenticado()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<GrupoDetalleDTO> actualizarGrupo(@PathVariable int id, @RequestBody GrupoDTO grupoDTO) {
        return ResponseEntity.ok(grupoService.actualizar(id, grupoDTO, autenticado()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<String> eliminarGrupo(@PathVariable int id) {
        grupoService.eliminar(id, autenticado());
        return ResponseEntity.ok("Grupo eliminado exitosamente.");
    }

    @PostMapping("/{id}/estudiantes")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<GrupoDetalleDTO> agregarEstudiantesAlGrupo(@PathVariable int id,
            @RequestBody List<String> emails) {
        return ResponseEntity.ok(grupoService.agregarEstudiantes(id, emails, autenticado()));
    }

    @DeleteMapping("/{id}/estudiantes/{email}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<GrupoDetalleDTO> eliminarEstudianteDelGrupo(@PathVariable int id,
            @PathVariable String email) {
        return ResponseEntity.ok(grupoService.eliminarEstudiante(id, email, autenticado()));
    }

    @DeleteMapping("/{id}/estudiantes")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<GrupoDetalleDTO> eliminarEstudiantesDelGrupo(@PathVariable int id,
            @RequestBody List<String> emails) {
        return ResponseEntity.ok(grupoService.eliminarEstudiantes(id, emails, autenticado()));
    }
}
