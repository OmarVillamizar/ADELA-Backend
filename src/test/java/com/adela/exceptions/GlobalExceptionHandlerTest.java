package com.adela.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.adela.dto.ApiError;

import jakarta.persistence.EntityNotFoundException;

/**
 * Protege el contrato de error de la ola 2.
 *
 * Dos regresiones se colaron aquí en producción: una ruta inexistente devolvía
 * 500 en lugar de 404, y un valor demasiado largo se anunciaba como "conflicto
 * con datos ya existentes". Ambas eran invisibles al compilador.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static MockHttpServletRequest peticion(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    /** Postgres no nombra la columna en este error, solo el tipo. */
    private static DataIntegrityViolationException desbordamiento() {
        return new DataIntegrityViolationException("no cabe",
                new RuntimeException("ERROR: value too long for type character varying(8)"));
    }

    private static DataIntegrityViolationException duplicado() {
        return new DataIntegrityViolationException("duplicado", new RuntimeException(
                "ERROR: duplicate key value violates unique constraint \"uk_codigo\"  Detail: Key (codigo)=(1151234) already exists."));
    }

    @Test
    @DisplayName("Una ruta inexistente es 404, no 500")
    void rutaInexistenteEs404() {
        ResponseEntity<ApiError> res = handler.handleRutaInexistente(
                new NoHandlerFoundException("GET", "/no/existe", new org.springframework.http.HttpHeaders()),
                peticion("/no/existe"));

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO.name(), res.getBody().code());
    }

    @Test
    @DisplayName("Un recurso estático inexistente también es 404")
    void recursoEstaticoInexistenteEs404() {
        // Con el manejador de recursos activo la peticion llega hasta el y lanza
        // esta excepcion, no NoHandlerFoundException. Quitar @EnableWebMvc lo activo.
        ResponseEntity<ApiError> res = handler.handleRutaInexistente(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/docs/loquesea"),
                peticion("/docs/loquesea"));

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO.name(), res.getBody().code());
    }

    @Test
    @DisplayName("Un valor que no cabe en la columna es validación, no conflicto")
    void desbordamientoDeLongitudEsValidacion() {
        ResponseEntity<ApiError> res = handler.handleIntegridad(desbordamiento(), peticion("/api/estudiantes"));

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals(ErrorCode.VALIDACION.name(), res.getBody().code());
    }

    @Test
    @DisplayName("Un código repetido sí es conflicto, y señala el campo")
    void codigoDuplicadoEsConflicto() {
        ResponseEntity<ApiError> res = handler.handleIntegridad(duplicado(), peticion("/api/estudiantes"));

        assertEquals(HttpStatus.CONFLICT, res.getStatusCode());
        assertEquals(ErrorCode.CODIGO_DUPLICADO.name(), res.getBody().code());
        assertEquals(Map.of("codigo", "Ya está en uso"), res.getBody().fields());
    }

    @Test
    @DisplayName("Un AppException conserva su código y sus campos")
    void appExceptionConservaCodigoYCampos() {
        ResponseEntity<ApiError> res = handler.handleApp(
                new AppException(ErrorCode.VALIDACION, "demasiado largo", Map.of("codigo", "Máximo 8 caracteres")),
                peticion("/api/profesores"));

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("demasiado largo", res.getBody().message());
        assertEquals(Map.of("codigo", "Máximo 8 caracteres"), res.getBody().fields());
    }

    @Test
    @DisplayName("El fallo inesperado no filtra el detalle interno, solo el traceId")
    void falloInesperadoNoFiltraDetalle() {
        ResponseEntity<ApiError> res = handler.handleInesperado(
                new IllegalStateException("connection string: postgres://usuario:clave@host"), peticion("/api/grupos"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
        assertNotNull(res.getBody().traceId());
        assertNull(res.getBody().fields());
        org.junit.jupiter.api.Assertions.assertFalse(res.getBody().message().contains("clave"),
                "el mensaje al cliente no debe incluir el detalle de la excepción");
    }

    @Test
    @DisplayName("Todo error lleva traceId, status y ruta")
    void todoErrorLlevaTraceIdStatusYRuta() {
        ApiError body = handler.handleNotFound(new EntityNotFoundException("no existe"), peticion("/api/grupos/9"))
                .getBody();

        assertNotNull(body.traceId());
        assertNotNull(body.timestamp());
        assertEquals(404, body.status());
        assertEquals("/api/grupos/9", body.path());
    }
}
