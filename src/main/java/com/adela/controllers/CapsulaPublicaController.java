package com.adela.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adela.dto.CapsulaPublicaDTO;
import com.adela.dto.RespuestaCapsulaDTO;
import com.adela.dto.ResultadoCapsulaDTO;
import com.adela.services.CapsulaService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/**
 * Único controlador sin autenticación (permitAll de /api/publico/** en
 * SecurityConfig). Solo se accede por código, nunca por id, y LimiteTasaFilter
 * acota cuántas peticiones puede hacer cada IP.
 */
@RequiredArgsConstructor
@Tag(name = "Cápsulas públicas", description = "Resolución de una cápsula por enlace, sin cuenta")
@RestController
@RequestMapping("/api/publico")
public class CapsulaPublicaController {

    private final CapsulaService capsulaService;

    @GetMapping("/capsulas/{codigo}")
    public ResponseEntity<CapsulaPublicaDTO> paraResponder(@PathVariable String codigo) {
        return ResponseEntity.ok(capsulaService.paraResponder(codigo));
    }

    @PostMapping("/capsulas/{codigo}/respuestas")
    public ResponseEntity<ResultadoCapsulaDTO> responder(@PathVariable String codigo,
            @Valid @RequestBody RespuestaCapsulaDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(capsulaService.responder(codigo, dto));
    }

    @GetMapping("/resultados/{codigo}")
    public ResponseEntity<ResultadoCapsulaDTO> resultado(@PathVariable String codigo) {
        return ResponseEntity.ok(capsulaService.resultado(codigo));
    }
}
