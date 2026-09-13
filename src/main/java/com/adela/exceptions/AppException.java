package com.adela.exceptions;

import java.util.Map;

/**
 * Error de negocio. Sustituye a los RuntimeException genéricos: lleva un código
 * estable y, opcionalmente, el detalle por campo.
 *
 * El mensaje se muestra al usuario final, así que se redacta para él.
 */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final transient Map<String, String> fields;

    public AppException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public AppException(ErrorCode code, String message, Map<String, String> fields) {
        super(message);
        this.code = code;
        this.fields = fields;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
