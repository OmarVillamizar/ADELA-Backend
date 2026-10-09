package com.adela.controllers;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adela.entities.Profesor;
import com.adela.services.SimulacionService;

import lombok.RequiredArgsConstructor;

/**
 * Solo desarrollo. Sin DEV_SIMULACION=true el bean no existe y la ruta responde
 * 404, así que no hay forma de llamarla en producción aunque se conozca.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "adela.dev.simulacion", havingValue = "true")
public class SimulacionController {

    private final SimulacionService simulacionService;

    @PostMapping("/grupos/{idGrupo}/simular/{idCuestionario}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> simular(@PathVariable int idGrupo, @PathVariable Long idCuestionario,
            @RequestParam int cantidad) {
        Profesor profesor = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        int creados = simulacionService.simular(idGrupo, idCuestionario, cantidad, profesor);
        return new ResponseEntity<>(Map.of("creados", creados), HttpStatus.CREATED);
    }
}
