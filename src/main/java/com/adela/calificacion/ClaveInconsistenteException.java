package com.adela.calificacion;

/**
 * La clave del cuestionario (pesos, formatos, rangos) es contradictoria. Es un
 * error de configuración, no de las respuestas del estudiante.
 */
public class ClaveInconsistenteException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ClaveInconsistenteException(String mensaje) {
        super(mensaje);
    }
}
