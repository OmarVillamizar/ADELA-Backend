package com.example.chaea.controllers;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.chaea.dto.ProfesorDTO;
import com.example.chaea.entities.Profesor;
import com.example.chaea.entities.ProfesorEstado;
import com.example.chaea.entities.Rol;
import com.example.chaea.entities.Usuario;
import com.example.chaea.entities.UsuarioEstado;
import java.util.Map;

import com.example.chaea.exceptions.AppException;
import com.example.chaea.exceptions.ErrorCode;
import com.example.chaea.repositories.ProfesorRepository;
import com.example.chaea.repositories.RolRepository;
import com.example.chaea.repositories.UsuarioRepository;

import jakarta.persistence.EntityNotFoundException;

@RestController
@RequestMapping("/api/profesores")
public class ProfesorController {
    
    @Autowired
    private ProfesorRepository profesorRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private RolRepository rolRepository;
        
    /*
     * No debería de poder crear profesores, ya que estos se registran por el camino
     * de autenticación
     * 
     * @PostMapping public ResponseEntity<?> crearProfesor(@RequestBody ProfesorDTO
     * profesorDTO) { // Validar campos requeridos if (profesorDTO.getEmail() ==
     * null || profesorDTO.getNombre() == null || profesorDTO.getCodigo() == null ||
     * profesorDTO.getCarrera() == null || profesorDTO.getEstado() == null ||
     * profesorDTO.getEstadoProfesor() == null || profesorDTO.getRol() == null) {
     * return ResponseEntity.status(HttpStatus.BAD_REQUEST).
     * body("Faltan campos requeridos."); } // Validar formato de correo electrónico
     * if (!EMAIL_PATTERN.matcher(profesorDTO.getEmail()).matches()) { return
     * ResponseEntity.status(HttpStatus.BAD_REQUEST).
     * body("Formato de correo incorrecto: " + profesorDTO.getEmail()); } //
     * Verificar si el correo ya existe if
     * (profesorRepository.existsById(profesorDTO.getEmail())) { return
     * ResponseEntity.status(HttpStatus.CONFLICT).body("Profesor con el correo " +
     * profesorDTO.getEmail() + " ya existe."); }
     * 
     * Optional<Rol> rolOpt = rolRepository.findByDescripcion(profesorDTO.getRol());
     * 
     * if(rolOpt.isEmpty()) { return ResponseEntity.status(HttpStatus.BAD_REQUEST).
     * body("Rol de profesor inválido."); }
     * 
     * Rol rol = rolOpt.get(); Profesor profesor = new Profesor(
     * profesorDTO.getEmail(), profesorDTO.getNombre(), profesorDTO.getCodigo(),
     * profesorDTO.getCarrera(), profesorDTO.getEstado(),
     * profesorDTO.getEstadoProfesor() ); profesor.setRol(rol); return
     * ResponseEntity.ok(profesorRepository.save(profesor)); }
     */
    
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<Profesor>> listarProfesores() {
        return ResponseEntity.ok(profesorRepository.findAll());
    }
    
    @GetMapping("/{email}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> consultarPorCorreo(@PathVariable String email) {
        Optional<Profesor> profesorOptional = profesorRepository.findById(email);
        if (!profesorOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Profesor no encontrado con el correo: " + email);
        }
        return ResponseEntity.ok(profesorOptional.get());
    }
    
    /**
     * Retira la aprobación del profesor: vuelve a la lista de pendientes, donde el
     * administrador puede reactivarlo o rechazarlo.
     *
     * estadoProfesor es el eje del flujo de administración (aprobado / pendiente);
     * estado refleja si el propio profesor completó su perfil. Antes este método
     * tocaba estado, que no es lo que decide el acceso, y además nunca llamaba a
     * save(), así que respondía éxito sin persistir nada.
     */
    @DeleteMapping("/deactivate/{email}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> eliminarProfesor(@PathVariable String email) {
        Profesor profesor = profesorRepository.findById(email)
                .orElseThrow(() -> new EntityNotFoundException("Profesor no encontrado con el correo: " + email));

        if (profesor.getEstadoProfesor() == ProfesorEstado.INACTIVA) {
            throw new AppException(ErrorCode.CUENTA_NO_ACTIVA, "La cuenta de " + email + " ya está inactiva.");
        }

        profesor.setEstadoProfesor(ProfesorEstado.INACTIVA);
        profesorRepository.save(profesor);
        return ResponseEntity.ok().body("Profesor desactivado exitosamente.");
    }
    
    @PutMapping("/activate/{email}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> activarCuentaProfesor(@PathVariable String email) {
        Optional<Profesor> profesorOptional = profesorRepository.findById(email);
        if (!profesorOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Profesor no encontrado con el correo: " + email);
        }
        Profesor profesor = profesorOptional.get();
        
        if (profesor.getEstadoProfesor() == ProfesorEstado.ACTIVA) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("La cuenta de profesor " + profesor.getEmail() + " ya está activa");
        }
        
        profesor.setEstadoProfesor(ProfesorEstado.ACTIVA);
        profesor.setRol(rolPorDescripcion("PROFESOR"));
        return ResponseEntity.ok(profesorRepository.save(profesor));
    }
    
    @PutMapping("/elevate/{email}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> elevarCuentaProfesor(@PathVariable String email) {
        Optional<Profesor> profesorOptional = profesorRepository.findById(email);
        if (!profesorOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Profesor no encontrado con el correo: " + email);
        }
        
        Profesor profesor = profesorOptional.get();
        
        if (profesor.getEstado() != UsuarioEstado.ACTIVA) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cuenta de profesor no está activa, no se puede hacer administrador: " + email);
        }
        
        profesor.setRol(rolPorDescripcion("ADMINISTRADOR"));
        return ResponseEntity.ok(profesorRepository.save(profesor));
    }
    
    @PutMapping("/demote/{email}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> bajarCuentaProfesor(@PathVariable String email) {
        Optional<Profesor> profesorOptional = profesorRepository.findById(email);
        if (!profesorOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Profesor no encontrado con el correo: " + email);
        }
        
        Profesor profesor = profesorOptional.get();
        Profesor prof = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (profesor.getEmail().equalsIgnoreCase(prof.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("No puede quitarse derechos de administrador a usted mismo.");
        }
        
        if (profesor.getEstado() != UsuarioEstado.ACTIVA) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cuenta de profesor no está activa, no se puede hacer administrador: " + email);
        }
        
        profesor.setRol(rolPorDescripcion("PROFESOR"));
        return ResponseEntity.ok(profesorRepository.save(profesor));
    }
    
    /**
     * Rechaza una solicitud pendiente. Comprueba estadoProfesor, que es lo que la
     * pantalla de administración usa para listar los pendientes: antes miraba
     * estado, que en un profesor recién registrado vale INCOMPLETA y nunca
     * INACTIVA, así que la guarda rechazaba siempre y el flujo era inalcanzable.
     */
    @DeleteMapping("/reject/{email}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> rechazarSolicitudCuentaProfesor(@PathVariable String email) {
        Profesor profesor = profesorRepository.findById(email)
                .orElseThrow(() -> new EntityNotFoundException("Profesor no encontrado con el correo: " + email));

        if (profesor.getEstadoProfesor() != ProfesorEstado.INACTIVA) {
            throw new AppException(ErrorCode.CUENTA_NO_RECHAZABLE,
                    "La cuenta de " + email + " está aprobada; desactívala antes de rechazarla.");
        }

        profesorRepository.delete(profesor);
        return ResponseEntity.ok("Solicitud de profesor rechazada");
    }
    
    private Rol rolPorDescripcion(String descripcion) {
        return rolRepository.findByDescripcion(descripcion)
                .orElseThrow(() -> new AppException(ErrorCode.ROL_NO_CONFIGURADO,
                        "El rol " + descripcion + " no está configurado en el sistema."));
    }

    /** Debe coincidir con @Column(length = 8) en Usuario.codigo. */
    private static final int LONGITUD_MAX_CODIGO = 8;

    @PutMapping
    @PreAuthorize("hasRole('PROFESOR') or hasRole('PROFESOR_INCOMPLETO') or hasRole('PROFESOR_INACTIVO') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> actualizarProfesor(@RequestBody ProfesorDTO profesorDTO) {
        // Validar formato de correo electrónico
        Profesor prof = (Profesor) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        String email = prof.getEmail();
        
        List<String> errores = new LinkedList<String>();
        if (profesorDTO.getCodigo() == null) {
            errores.add("codigo");
        }
        if (profesorDTO.getCarrera() == null) {
            errores.add("carrera");
        }
        
        if (errores.size() > 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Profesor presenta errores en los siguientes campos: " + errores.toString());
        }
        Optional<Profesor> profesorOptional = profesorRepository.findById(email);
        if (!profesorOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Profesor : " + email);
        }
        // Usuario.codigo es varchar(8): sin esta comprobacion el desbordamiento
        // llegaba a Postgres y salia como "conflicto con datos ya existentes",
        // que describe un problema distinto al real.
        if (profesorDTO.getCodigo().length() > LONGITUD_MAX_CODIGO) {
            throw new AppException(ErrorCode.VALIDACION,
                    "El código admite un máximo de " + LONGITUD_MAX_CODIGO + " caracteres.",
                    Map.of("codigo", "Máximo " + LONGITUD_MAX_CODIGO + " caracteres"));
        }

        Optional<Usuario> existente = usuarioRepository.findByCodigo(profesorDTO.getCodigo());
        if (existente.isPresent() && !existente.get().getEmail().equals(email)) {
            throw new AppException(ErrorCode.CODIGO_DUPLICADO,
                    "El código " + profesorDTO.getCodigo() + " ya está registrado por otro usuario.",
                    Map.of("codigo", "Ya está en uso"));
        }
        
        Profesor profesorExistente = profesorOptional.get();
        profesorExistente.setCarrera(profesorDTO.getCarrera());
        profesorExistente.setCodigo(profesorDTO.getCodigo());
        profesorExistente.setEstado(UsuarioEstado.ACTIVA);
        
        return ResponseEntity.ok(profesorRepository.save(profesorExistente));
    }
    
}