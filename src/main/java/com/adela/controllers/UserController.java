package com.adela.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adela.dto.EstudianteDTO;
import com.adela.dto.ProfesorDTO;
import com.adela.dto.UserDTO;
import com.adela.entities.Estudiante;
import com.adela.entities.Profesor;
import com.adela.entities.Usuario;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @GetMapping("/info")
    @PreAuthorize("hasRole('ESTUDIANTE') or hasRole('PROFESOR') or hasRole('ESTUDIANTE_INCOMPLETO') or hasRole('PROFESOR_INCOMPLETO') or hasRole('PROFESOR_INACTIVO') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<UserDTO> getUserInfo() {
        Usuario user = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserDTO userD;
        if (user instanceof Estudiante) {
            Estudiante est = (Estudiante) user;
            userD = new EstudianteDTO(est.getEmail(), est.getNombre(), est.getCodigo(), est.getEstado(),
                    est.getGenero(), est.getFecha_nacimiento());
        } else {
            Profesor prof = (Profesor) user;
            userD = new ProfesorDTO(prof.getEmail(), prof.getNombre(), prof.getCodigo(), prof.getEstado(),
                    prof.getCarrera(), prof.getRol(), prof.getEstadoProfesor());
        }
        return ResponseEntity.ok(userD);
    }
}
