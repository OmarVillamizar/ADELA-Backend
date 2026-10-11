package com.adela.ia;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Informe que la IA entrega junto al cuestionario: qué entendió, de dónde sacó
 * cada sección y qué debe revisar la persona. Todo es opcional y de tipo texto:
 * una IA externa se desvía del formato, y eso no debe tumbar la importación.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalisisIa(String modo, Reconocido reconocido, String modeloAsistente, Lectura lectura,
        Map<String, String> origen, List<String> faltantes, List<NoSoportado> noSoportado,
        List<Revision> revision, List<String> preguntasAlProfesor) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reconocido(String catalogo, Double confianza, Boolean estructuraIgual, List<String> senales,
            List<String> diferencias) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lectura(String esquema, String lecturaAsistente, String corte, String motivo,
            List<Descartado> descartados, String sugerencia) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Descartado(String esquema, String porque) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NoSoportado(String que, String resolucion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Revision(Integer prioridad, String riesgo, String seccion, String ubicacion, String porque,
            String comoVerificar) {
    }
}
