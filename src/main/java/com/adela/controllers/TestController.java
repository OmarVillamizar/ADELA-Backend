package com.adela.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/test")
public class TestController {
    
    private static final Logger logger = LoggerFactory.getLogger(TestController.class);
    
    @GetMapping("/test/est")
    @PreAuthorize("hasRole('ESTUDIANTE') or hasRole('ESTUDIANTE_INCOMPLETO')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> test() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        logger.info("Principal " + auth.getPrincipal().toString());
        logger.info("Details " + auth.getDetails().toString());
        logger.info("Name " + auth.getName().toString());
        logger.info("Authorities " + auth.getAuthorities().toString());
        return ResponseEntity.status(HttpStatus.OK).body("estudiante"); // Return index.html
    }
    
    @GetMapping("/test/prof")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> testProf() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        logger.info("entered test " + auth.getAuthorities().toString());
        return ResponseEntity.status(HttpStatus.OK).body("profesor"); // Return index.html
    }
    
    @GetMapping("/test/admin")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> testAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        logger.info("Principal " + auth.getPrincipal().toString());
        logger.info("Details " + auth.getDetails().toString());
        logger.info("Name " + auth.getName().toString());
        logger.info("Authorities " + auth.getAuthorities().toString());
        return ResponseEntity.status(HttpStatus.OK).body("administrador"); // Return index.html
    }
    
}