package com.adela.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Código estable de error y el status HTTP que le corresponde.
 *
 * El cliente decide sobre el código, no sobre el mensaje: así el texto se puede
 * reescribir o traducir sin romper el frontend.
 */
public enum ErrorCode {

    // Genéricos
    VALIDACION(HttpStatus.BAD_REQUEST),
    RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND),
    ACCESO_DENEGADO(HttpStatus.FORBIDDEN),
    CONFLICTO_DATOS(HttpStatus.CONFLICT),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR),

    // Autenticación
    TOKEN_MALFORMADO(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRADO(HttpStatus.UNAUTHORIZED),
    TOKEN_FIRMA_INVALIDA(HttpStatus.UNAUTHORIZED),
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED),

    // Usuarios
    CODIGO_DUPLICADO(HttpStatus.CONFLICT),
    CUENTA_YA_ACTIVA(HttpStatus.CONFLICT),
    CUENTA_NO_ACTIVA(HttpStatus.CONFLICT),
    CUENTA_NO_RECHAZABLE(HttpStatus.CONFLICT),
    AUTO_DEGRADACION(HttpStatus.CONFLICT),
    ROL_NO_CONFIGURADO(HttpStatus.INTERNAL_SERVER_ERROR),

    // Grupos
    ESTUDIANTES_NO_ENCONTRADOS(HttpStatus.BAD_REQUEST),
    ESTUDIANTE_NO_EN_GRUPO(HttpStatus.CONFLICT),

    // Cuestionarios
    CUESTIONARIO_BLOQUEADO(HttpStatus.CONFLICT),
    PREGUNTAS_SIN_RESPONDER(HttpStatus.UNPROCESSABLE_ENTITY),
    OPCION_DUPLICADA(HttpStatus.UNPROCESSABLE_ENTITY),
    OPCION_INCONSISTENTE(HttpStatus.UNPROCESSABLE_ENTITY),
    CATEGORIA_INEXISTENTE(HttpStatus.UNPROCESSABLE_ENTITY),
    ASIGNACION_AMBIGUA(HttpStatus.CONFLICT),
    ASIGNACION_YA_RESPONDIDA(HttpStatus.CONFLICT),
    ASIGNACION_NO_CORRESPONDE(HttpStatus.CONFLICT),
    CUESTIONARIO_SIN_RESOLVER(HttpStatus.CONFLICT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
