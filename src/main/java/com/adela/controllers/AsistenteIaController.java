package com.adela.controllers;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.adela.entities.Usuario;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.ia.EstadoTrabajo;
import com.adela.ia.GeneradorCuestionarioService;
import com.adela.ia.SolicitudRefinamiento;
import com.adela.ia.Trabajo;
import com.adela.ia.TrabajosIa;

/**
 * Nelse: crear un cuestionario a partir de un PDF y/o instrucciones. La generación es un
 * trabajo en memoria que el frontend consulta cada 2 s; aquí solo se reciben las
 * peticiones, y las reglas (dueño, cuota, cola) viven en {@link TrabajosIa}. Solo el
 * administrador, el mismo rol que puede crear cuestionarios.
 */
@RestController
@RequestMapping("/api/ia/cuestionarios")
public class AsistenteIaController {

    static final int MAX_INSTRUCCIONES = 4000;

    private final GeneradorCuestionarioService generador;
    private final TrabajosIa trabajos;
    private final boolean habilitada;
    private final int maxMb;
    private final int maxPaginas;

    /** habilitada = el interruptor está en true Y hay llave; sin una de las dos Nelse no puede llamar al proveedor. */
    @Autowired
    public AsistenteIaController(GeneradorCuestionarioService generador, TrabajosIa trabajos,
            @Value("${adela.ia.habilitada}") boolean habilitada, @Value("${adela.ia.api-key}") String apiKey,
            @Value("${adela.ia.max-mb}") int maxMb, @Value("${adela.ia.max-paginas}") int maxPaginas) {
        this.generador = generador;
        this.trabajos = trabajos;
        this.habilitada = habilitada && apiKey != null && !apiKey.isBlank();
        this.maxMb = maxMb;
        this.maxPaginas = maxPaginas;
    }

    /** Lo que el frontend necesita para mostrar o esconder la tarjeta de Nelse. Responde aunque esté apagada. */
    public record EstadoIa(boolean habilitada, int maxMb, int maxPaginas, int cuotaRestante) {
    }

    @GetMapping("/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstadoIa> estado(Authentication authentication) {
        return ResponseEntity.ok(
                new EstadoIa(habilitada, maxMb, maxPaginas, trabajos.cuotaRestante(email(authentication))));
    }

    /**
     * PDF e instrucciones son opcionales, pero hace falta uno. Apagada, responde 503 antes de
     * mirar el archivo. Los bytes se leen aquí, en el hilo de la petición: el MultipartFile no
     * sirve cuando esta termina. El PDF se interpreta en el trabajo.
     */
    @PostMapping(value = "/trabajos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Map<String, UUID>> crearTrabajo(
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @RequestParam(value = "instrucciones", required = false) String instrucciones,
            Authentication authentication) {
        if (!habilitada) {
            throw new AppException(ErrorCode.IA_DESHABILITADA, "El asistente de IA no está habilitado.");
        }
        String texto = instrucciones == null ? "" : instrucciones.strip();
        boolean hayArchivo = archivo != null && !archivo.isEmpty();
        if (!hayArchivo && texto.isEmpty()) {
            throw new AppException(ErrorCode.VALIDACION, "Sube un PDF o escribe instrucciones.",
                    Map.of("instrucciones", "Hace falta un PDF o instrucciones"));
        }
        if (texto.length() > MAX_INSTRUCCIONES) {
            throw new AppException(ErrorCode.VALIDACION,
                    "Las instrucciones superan los " + MAX_INSTRUCCIONES + " caracteres permitidos.",
                    Map.of("instrucciones", "Máximo " + MAX_INSTRUCCIONES + " caracteres"));
        }
        byte[] pdf = null;
        if (hayArchivo) {
            try {
                pdf = archivo.getBytes();
            } catch (IOException e) {
                throw new AppException(ErrorCode.DOCUMENTO_INVALIDO, "No se pudo leer el archivo enviado.");
            }
        }
        Trabajo trabajo = generador.crear(email(authentication), pdf, texto.isEmpty() ? null : texto);
        return ResponseEntity.accepted().body(Map.of("id", trabajo.id()));
    }

    @GetMapping("/trabajos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstadoTrabajo> consultar(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(trabajos.obtener(email(authentication), uuid(id)).estado());
    }

    @PostMapping("/trabajos/{id}/refinar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> refinar(@PathVariable String id, @RequestBody SolicitudRefinamiento solicitud,
            Authentication authentication) {
        solicitud.validar();
        generador.refinar(email(authentication), uuid(id), solicitud);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/trabajos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> cancelar(@PathVariable String id, Authentication authentication) {
        trabajos.cancelar(email(authentication), uuid(id));
        return ResponseEntity.noContent().build();
    }

    /** El principal es la entidad Usuario (lo pone JwtFilter); getName() daría su toString. */
    private static String email(Authentication authentication) {
        return authentication.getPrincipal() instanceof Usuario usuario ? usuario.getEmail()
                : authentication.getName();
    }

    /** Un id que no es UUID es un trabajo que no existe, no un error de formato. */
    private static UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.RECURSO_NO_ENCONTRADO, "No se encontró el trabajo.");
        }
    }
}
