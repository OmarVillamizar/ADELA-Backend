package com.adela.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adela.dto.ImportacionRequest;
import com.adela.ia.LectorJsonTolerante;
import com.adela.ia.PromptIa;
import com.adela.ia.ResultadoImportacion;
import com.adela.ia.ValidadorGeneracion;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Importar un cuestionario hecho con una IA externa: las instrucciones que se
 * le copian y la validación del JSON que devuelve. Nada se guarda aquí; para
 * crear, el frontend usa después el POST de siempre. Solo el administrador, el
 * mismo rol que puede crear.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/cuestionarios/importacion")
public class ImportacionController {

    private final PromptIa promptIa;

    private final ValidadorGeneracion validadorGeneracion;

    private final ObjectMapper objectMapper;

    @GetMapping(value = "/instrucciones", produces = "text/markdown;charset=UTF-8")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<String> instrucciones() {
        return ResponseEntity.ok(promptIa.instruccionesExternas());
    }

    @PostMapping("/validar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ResultadoImportacion> validar(@RequestBody ImportacionRequest request) {
        return ResponseEntity.ok(
                validadorGeneracion.validar(LectorJsonTolerante.leer(request.texto(), objectMapper)));
    }
}
