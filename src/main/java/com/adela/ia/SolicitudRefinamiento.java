package com.adela.ia;

import java.util.List;
import java.util.Map;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;

/**
 * Lo que la persona contesta a Nelse para ajustar el resultado: las respuestas a
 * {@code analisis.preguntasAlProfesor} y/o una nota libre. Es el cuerpo de
 * {@code POST /trabajos/{id}/refinar}.
 *
 * Es texto que llega al modelo, así que se acota: sin tope, un refinamiento sería la vía para
 * pegar un documento entero sin pasar por los límites de la subida.
 */
public record SolicitudRefinamiento(List<Respuesta> respuestas, String texto) {

    static final int MAX_CARACTERES = 4000;

    public record Respuesta(String pregunta, String respuesta) {
    }

    /** 400 VALIDACION si no trae nada que aplicar o es demasiado largo. */
    public void validar() {
        if (respuestasUtiles().isEmpty() && notaLibre().isEmpty()) {
            throw new AppException(ErrorCode.VALIDACION, "Escribe al menos una respuesta o una nota.",
                    Map.of("respuestas", "Vacío"));
        }
        int largo = notaLibre().length();
        for (Respuesta r : respuestasUtiles()) {
            largo += r.pregunta().length() + r.respuesta().length();
        }
        if (largo > MAX_CARACTERES) {
            throw new AppException(ErrorCode.VALIDACION,
                    "Las respuestas superan los " + MAX_CARACTERES + " caracteres permitidos.",
                    Map.of("respuestas", "Máximo " + MAX_CARACTERES + " caracteres"));
        }
    }

    /** Lo que se le dice al modelo, debajo del encabezado del turno: pregunta y respuesta, más la nota. */
    String paraElModelo() {
        StringBuilder sb = new StringBuilder();
        for (Respuesta r : respuestasUtiles()) {
            sb.append("- ").append(r.pregunta()).append(": ").append(r.respuesta()).append('\n');
        }
        if (!notaLibre().isEmpty()) {
            sb.append("NOTE: ").append(notaLibre()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Lo que se suma a las instrucciones originales como fuente PROFESOR de la validación.
     * Solo las respuestas y la nota, nunca las preguntas: las escribió el modelo, y una cifra
     * suya en la pregunta no puede convertirse en "dato del profesor".
     */
    String comoFuente() {
        StringBuilder sb = new StringBuilder();
        for (Respuesta r : respuestasUtiles()) {
            sb.append(r.respuesta()).append('\n');
        }
        return sb.append(notaLibre()).toString().strip();
    }

    private List<Respuesta> respuestasUtiles() {
        if (respuestas == null) {
            return List.of();
        }
        return respuestas.stream()
                .filter(r -> r != null && r.respuesta() != null && !r.respuesta().isBlank())
                .map(r -> new Respuesta(r.pregunta() == null ? "" : r.pregunta().strip(), r.respuesta().strip()))
                .toList();
    }

    private String notaLibre() {
        return texto == null ? "" : texto.strip();
    }
}
