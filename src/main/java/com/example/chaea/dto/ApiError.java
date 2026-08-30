package com.example.chaea.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo único de error de la API.
 *
 * El detalle técnico (traza, SQL, nombres de clase) nunca viaja aquí: se
 * registra en el log junto al traceId, que es lo que permite correlacionar una
 * respuesta con su causa sin exponerla al cliente.
 *
 * @param code    identificador estable. El cliente decide sobre él, no sobre el
 *                texto de message, que puede reescribirse sin romper nada.
 * @param message redactado para el usuario final: qué pasó y qué hacer.
 * @param fields  errores por campo, para marcar el formulario en vez de lanzar
 *                un modal. Ausente cuando el error no es de validación.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String code, String message, Map<String, String> fields,
        String traceId, String path) {
}
