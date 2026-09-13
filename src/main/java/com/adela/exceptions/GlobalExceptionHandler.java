package com.adela.exceptions;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.adela.dto.ApiError;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Único sitio donde una excepción se convierte en respuesta.
 *
 * Antes cada controlador hacía catch (Exception) y devolvía 400 con
 * e.getMessage(): los fallos de infraestructura se disfrazaban de errores de
 * cliente y el detalle interno se filtraba. Aquí el detalle va al log junto al
 * traceId y al cliente solo llega el cuerpo de ApiError.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleApp(AppException ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        logger.warn("[{}] {} en {}: {}", traceId, ex.getCode(), request.getRequestURI(), ex.getMessage());
        return construir(ex.getCode(), ex.getMessage(), ex.getFields(), traceId, request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        logger.warn("[{}] No encontrado en {}: {}", traceId, request.getRequestURI(), ex.getMessage());
        return construir(ErrorCode.RECURSO_NO_ENCONTRADO, ex.getMessage(), null, traceId, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidacion(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        String traceId = nuevoTraceId();
        logger.warn("[{}] Validación fallida en {}: {}", traceId, request.getRequestURI(), fields);
        return construir(ErrorCode.VALIDACION, "Revisa los campos marcados.", fields, traceId, request);
    }

    /**
     * Traduce la violación de una restricción de base de datos a un mensaje útil.
     * Cierra el caso del código duplicado incluso en los endpoints que todavía no
     * lo validan antes de escribir.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegridad(DataIntegrityViolationException ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        String detalle = ex.getMostSpecificCause().getMessage();
        logger.warn("[{}] Violación de integridad en {}: {}", traceId, request.getRequestURI(), detalle);

        // Un valor que no cabe en la columna no es un conflicto con datos ya
        // existentes: es un dato invalido. Postgres lo reporta como "value too long
        // for type character varying(n)" sin nombrar la columna, asi que aqui solo
        // se corrige el status y el mensaje; el campo concreto lo senala la
        // validacion del controlador, que si sabe cual es.
        if (detalle != null && detalle.toLowerCase().contains("value too long")) {
            return construir(ErrorCode.VALIDACION, "Alguno de los datos enviados supera la longitud permitida.", null,
                    traceId, request);
        }

        if (detalle != null && detalle.toLowerCase().contains("codigo")) {
            return construir(ErrorCode.CODIGO_DUPLICADO, "El código ya está registrado por otro usuario.",
                    Map.of("codigo", "Ya está en uso"), traceId, request);
        }
        return construir(ErrorCode.CONFLICTO_DATOS, "La operación entra en conflicto con datos ya existentes.", null,
                traceId, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAcceso(AccessDeniedException ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        logger.warn("[{}] Acceso denegado en {}", traceId, request.getRequestURI());
        return construir(ErrorCode.ACCESO_DENEGADO, "No tienes permisos para realizar esta acción.", null, traceId,
                request);
    }

    /**
     * Una ruta inexistente es un error del cliente, no un fallo del servidor. Sin
     * este handler cae en handleInesperado y devuelve 500 con un traceId, que
     * sugiere una avería donde solo hay una URL mal escrita.
     *
     * Hacen falta las dos excepciones: con el manejador de recursos estáticos
     * activo (el que restaura WebMvcAutoConfiguration al quitar @EnableWebMvc) la
     * petición llega hasta él y lanza NoResourceFoundException, no la otra.
     */
    @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
    public ResponseEntity<ApiError> handleRutaInexistente(Exception ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        logger.warn("[{}] Ruta inexistente: {} {}", traceId, request.getMethod(), request.getRequestURI());
        return construir(ErrorCode.RECURSO_NO_ENCONTRADO, "La ruta solicitada no existe.", null, traceId, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleInesperado(Exception ex, HttpServletRequest request) {
        String traceId = nuevoTraceId();
        // El detalle se queda aquí: al cliente solo le llega el traceId.
        logger.error("[{}] Error inesperado en {}", traceId, request.getRequestURI(), ex);
        return construir(ErrorCode.ERROR_INTERNO,
                "Ocurrió un error inesperado. Comparte el código " + traceId + " con soporte.", null, traceId, request);
    }

    private ResponseEntity<ApiError> construir(ErrorCode code, String message, Map<String, String> fields,
            String traceId, HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), code.getStatus().value(), code.name(), message, fields, traceId,
                request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    private String nuevoTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
