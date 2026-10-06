package com.adela.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adela.dto.InsigniaDTO;
import com.adela.entities.Usuario;
import com.adela.services.InsigniaService;

import lombok.RequiredArgsConstructor;

/**
 * Siempre sobre el usuario autenticado: el correo sale del token, nunca de la
 * petición, así que nadie puede leer ni marcar insignias ajenas.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/insignias")
@PreAuthorize("hasRole('ESTUDIANTE') or hasRole('ESTUDIANTE_INCOMPLETO')")
public class InsigniaController {

    private final InsigniaService insigniaService;

    @GetMapping
    public ResponseEntity<List<InsigniaDTO>> misInsignias() {
        return ResponseEntity.ok(insigniaService.obtenidas(emailActual()));
    }

    @PatchMapping("/{codigo}/celebrada")
    public ResponseEntity<Void> marcarCelebrada(@PathVariable String codigo) {
        insigniaService.marcarCelebrada(emailActual(), codigo);
        return ResponseEntity.noContent().build();
    }

    private static String emailActual() {
        return ((Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getEmail();
    }
}
