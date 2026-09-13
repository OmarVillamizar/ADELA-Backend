package com.example.chaea.controllers;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.chaea.dto.EstudianteDTO;
import com.example.chaea.entities.Estudiante;
import com.example.chaea.entities.Usuario;
import com.example.chaea.entities.UsuarioEstado;
import com.example.chaea.exceptions.AppException;
import com.example.chaea.exceptions.ErrorCode;
import com.example.chaea.repositories.EstudianteRepository;
import com.example.chaea.repositories.UsuarioRepository;

@RestController
@RequestMapping("/api/estudiantes")
public class EstudianteController {
    
    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;
        
    @GetMapping("/omero")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public String ola() {
        return "Hola";
    }
    
    /*
     * No puedo crear estudiantes siendo profesor /
     * 
     * @PostMapping
     * 
     * @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')") public
     * ResponseEntity<?> crearEstudiante(@RequestBody EstudianteDTO estudianteDTO) {
     * // Validar campos requeridos if (estudianteDTO.getEmail() == null ||
     * estudianteDTO.getNombre() == null || estudianteDTO.getCodigo() == null) {
     * return ResponseEntity.status(HttpStatus.BAD_REQUEST).
     * body("Faltan campos requeridos."); } // Validar formato de correo electrónico
     * if (!EMAIL_PATTERN.matcher(estudianteDTO.getEmail()).matches()) { return
     * ResponseEntity.status(HttpStatus.BAD_REQUEST)
     * .body("Formato de correo incorrecto: " + estudianteDTO.getEmail()); } //
     * Verificar si el correo ya existe if
     * (estudianteRepository.existsById(estudianteDTO.getEmail())) { return
     * ResponseEntity.status(HttpStatus.CONFLICT) .body("Estudiante con el correo "
     * + estudianteDTO.getEmail() + " ya existe."); }
     * 
     * Estudiante estudiante = new Estudiante();
     * estudiante.setCodigo(estudianteDTO.getCodigo());
     * estudiante.setEmail(estudianteDTO.getEmail());
     * estudiante.setNombre(estudianteDTO.getNombre());
     * estudiante.setEstado(UsuarioEstado.INCOMPLETA); return
     * ResponseEntity.ok(estudianteRepository.save(estudiante)); }
     */
    @GetMapping
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<Estudiante>> listarEstudiantes() {
        return ResponseEntity.ok(estudianteRepository.findAll());
    }
    
    @GetMapping("/{email}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> consultarPorCorreo(@PathVariable String email) {
        
        Optional<Estudiante> estudianteOptional = estudianteRepository.findById(email);
        if (!estudianteOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Estudiante no encontrado con el correo: " + email);
        }
        
        return ResponseEntity.ok(estudianteOptional.get());
    }
    
    /*
     * @DeleteMapping("/{email}") public ResponseEntity<?>
     * eliminarEstudiante(@PathVariable String email) { // Validar formato de correo
     * electrónico if (!EMAIL_PATTERN.matcher(email).matches()) { return
     * ResponseEntity.status(HttpStatus.BAD_REQUEST).
     * body("Formato de correo incorrecto: " + email); }
     * 
     * Optional<Estudiante> estudianteOptional =
     * estudianteRepository.findById(email); if (!estudianteOptional.isPresent()) {
     * return ResponseEntity.status(HttpStatus.NOT_FOUND).
     * body("Estudiante no encontrado con el correo: " + email); }
     * 
     * estudianteRepository.deleteById(email); return
     * ResponseEntity.ok().body("Estudiante eliminado exitosamente."); }
     */
    
    /** Debe coincidir con @Column(length = 8) en Usuario.codigo. */
    private static final int LONGITUD_MAX_CODIGO = 8;

    @PutMapping
    @PreAuthorize("hasRole('ESTUDIANTE') or hasRole('ESTUDIANTE_INCOMPLETO')")
    public ResponseEntity<?> actualizarEstudiante(@RequestBody EstudianteDTO estudianteDTO) {
        // Validar formato de correo electrónico
        Estudiante estud = (Estudiante) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        String email = estud.getEmail();

        List<String> errores = new LinkedList<String>();
        if (estudianteDTO.getCodigo() == null) {
            errores.add("codigo");
        }
        if (estudianteDTO.getFechaNacimiento() == null) {
            errores.add("fecha de nacimiento");
        }
        if (estudianteDTO.getGenero() == null) {
            errores.add("genero");
        }
        if (errores.size() > 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Estudiante presenta errores en los siguientes campos: " + errores.toString());
        }
        Optional<Estudiante> estudianteOptional = estudianteRepository.findById(email);
        if (!estudianteOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Estudiante no encontrado con el correo: " + email);
        }
        
        // Usuario.codigo es varchar(8): sin esta comprobacion el desbordamiento
        // llegaba a Postgres y salia como "conflicto con datos ya existentes",
        // que describe un problema distinto al real.
        if (estudianteDTO.getCodigo().length() > LONGITUD_MAX_CODIGO) {
            throw new AppException(ErrorCode.VALIDACION,
                    "El código admite un máximo de " + LONGITUD_MAX_CODIGO + " caracteres.",
                    Map.of("codigo", "Máximo " + LONGITUD_MAX_CODIGO + " caracteres"));
        }

        // Usuario.codigo es unique: sin esta comprobacion la violacion de constraint
        // salia como 500. ProfesorController ya la hacia; aqui faltaba.
        Optional<Usuario> conMismoCodigo = usuarioRepository.findByCodigo(estudianteDTO.getCodigo());
        if (conMismoCodigo.isPresent() && !conMismoCodigo.get().getEmail().equalsIgnoreCase(email)) {
            throw new AppException(ErrorCode.CODIGO_DUPLICADO,
                    "El código " + estudianteDTO.getCodigo() + " ya está registrado por otro usuario.",
                    Map.of("codigo", "Ya está en uso"));
        }

        Estudiante estudianteExistente = estudianteOptional.get();
        estudianteExistente.setCodigo(estudianteDTO.getCodigo());
        estudianteExistente.setGenero(estudianteDTO.getGenero());
        estudianteExistente.setFecha_nacimiento(estudianteDTO.getFechaNacimiento());
        estudianteExistente.setEstado(UsuarioEstado.ACTIVA);
        
        return ResponseEntity.ok(estudianteRepository.save(estudianteExistente));
    }
    
}