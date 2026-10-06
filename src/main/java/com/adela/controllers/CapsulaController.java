package com.adela.controllers;

import java.util.List;

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

import com.adela.dto.CapsulaActualizarDTO;
import com.adela.dto.CapsulaCrearDTO;
import com.adela.dto.CapsulaDTO;
import com.adela.entities.Profesor;
import com.adela.services.CapsulaService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/**
 * Gestión de cápsulas por su profesor. La resolución por enlace, sin cuenta,
 * vive aparte en CapsulaPublicaController.
 */
@RequiredArgsConstructor
@Tag(name = "Cápsulas", description = "Cuestionarios compartidos por enlace o QR")
@RestController
@RequestMapping("/api/capsulas")
public class CapsulaController {

    private final CapsulaService capsulaService;

    private Profesor autenticado() {
        return (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @PostMapping
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<CapsulaDTO> crear(@Valid @RequestBody CapsulaCrearDTO dto) {
        return ResponseEntity.ok(capsulaService.crear(dto, autenticado()));
    }

    @GetMapping
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<CapsulaDTO>> listar() {
        return ResponseEntity.ok(capsulaService.listar(autenticado()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<CapsulaDTO> consultar(@PathVariable Long id) {
        return ResponseEntity.ok(capsulaService.consultar(id, autenticado()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<CapsulaDTO> actualizar(@PathVariable Long id, @Valid @RequestBody CapsulaActualizarDTO dto) {
        return ResponseEntity.ok(capsulaService.actualizar(id, dto, autenticado()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        capsulaService.eliminar(id, autenticado());
        return ResponseEntity.noContent().build();
    }
}
