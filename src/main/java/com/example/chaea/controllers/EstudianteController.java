package com.example.chaea.controllers;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;
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
    
    /**
     * Devuelve como mucho una página de estudiantes. Antes esto era findAll() sin
     * paginación y el cliente lo pedía entero al abrir /grupos, para filtrar en
     * memoria un autocompletado: con la matrícula de una facultad eso es
     * descargar toda la tabla en cada visita.
     *
     * `q` filtra por correo o nombre. Sin `q` se devuelve la primera página, que
     * mantiene el contrato anterior para quien no pagine.
     */
    @GetMapping
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<EstudianteDTO>> listarEstudiantes(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "20") int size) {
        Pageable pagina = PageRequest.of(0, Math.min(Math.max(size, 1), TAM_PAGINA_MAX), Sort.by("email"));
        List<EstudianteDTO> resultado = estudianteRepository
                .findByEmailContainingIgnoreCaseOrNombreContainingIgnoreCase(q, q, pagina).getContent().stream()
                .map(EstudianteDTO::from).toList();
        return ResponseEntity.ok(resultado);
    }
    
    @GetMapping("/{email}")
    @PreAuthorize("hasRole('PROFESOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> consultarPorCorreo(@PathVariable String email) {
        
        Optional<Estudiante> estudianteOptional = estudianteRepository.findById(email);
        if (!estudianteOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Estudiante no encontrado con el correo: " + email);
        }
        
        return ResponseEntity.ok(EstudianteDTO.from(estudianteOptional.get()));
    }
    
    
    /** Tope duro: el cliente no puede pedir la tabla entera subiendo `size`. */
    private static final int TAM_PAGINA_MAX = 100;

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
        
        return ResponseEntity.ok(EstudianteDTO.from(estudianteRepository.save(estudianteExistente)));
    }
    
}