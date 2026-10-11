package com.adela.ia;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Saca el JSON de lo que la persona pegó: quita cercas de markdown y la prosa
 * que la IA suele poner antes y después. Acepta el envoltorio
 * {@code {formato, cuestionario, analisis}} o el cuestionario plano.
 *
 * Solo reconoce la forma; que el contenido sea válido lo decide el validador.
 */
public final class LectorJsonTolerante {

    static final int MAX_CARACTERES = 2_000_000;

    private static final String CAMPO = "json";

    private static final Pattern CERCA_JSON = Pattern.compile("```[ \\t]*json[^\\n`]*\\n?(.*?)```",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CERCA = Pattern.compile("```[^\\n`]*\\n?(.*?)```", Pattern.DOTALL);

    /** analisis y formato son opcionales; en la forma plana ambos son null. */
    public record Entrada(JsonNode cuestionario, JsonNode analisis, String formato) {
    }

    private LectorJsonTolerante() {
    }

    public static Entrada leer(String texto, ObjectMapper om) {
        if (texto == null || texto.isBlank()) {
            throw error("Pega el JSON que te entregó tu IA.");
        }
        if (texto.length() > MAX_CARACTERES) {
            throw error("El texto es demasiado largo (máximo " + MAX_CARACTERES + " caracteres).");
        }

        String json = extraer(texto);
        JsonNode raiz;
        try {
            raiz = om.readTree(json);
        } catch (JsonProcessingException e) {
            throw error(describirSintaxis(e));
        }

        JsonNode cuestionario = raiz.get("cuestionario");
        if (cuestionario != null && cuestionario.isObject()) {
            JsonNode analisis = raiz.get("analisis");
            JsonNode formato = raiz.get("formato");
            return new Entrada(cuestionario, analisis != null && analisis.isObject() ? analisis : null,
                    formato != null && formato.isTextual() ? formato.asText() : null);
        }
        if (raiz.has("preguntas") && raiz.has("estilos")) {
            return new Entrada(raiz, null, null);
        }
        throw error("No parece un cuestionario de ADELA: falta 'cuestionario' o 'preguntas' y 'estilos'.");
    }

    /** El contenido de la primera cerca ```json (o, si no hay, la primera cerca) y, si no, el texto entero. */
    private static String extraer(String texto) {
        List<String> candidatos = new ArrayList<>();
        Matcher m = CERCA_JSON.matcher(texto);
        if (m.find()) {
            candidatos.add(m.group(1));
        } else {
            m = CERCA.matcher(texto);
            if (m.find()) {
                candidatos.add(m.group(1));
            }
        }
        candidatos.add(texto);
        for (String candidato : candidatos) {
            String objeto = primerObjeto(candidato);
            if (objeto != null) {
                return objeto;
            }
        }
        throw error("No encontré ningún objeto JSON en el texto.");
    }

    /**
     * Primer objeto con llaves balanceadas. Las llaves dentro de una cadena JSON
     * no cuentan, y una barra invertida protege la comilla que sigue.
     */
    private static String primerObjeto(String s) {
        int inicio = s.indexOf('{');
        if (inicio < 0) {
            return null;
        }
        int profundidad = 0;
        boolean enCadena = false;
        boolean escapado = false;
        for (int i = inicio; i < s.length(); i++) {
            char c = s.charAt(i);
            if (enCadena) {
                if (escapado) {
                    escapado = false;
                } else if (c == '\\') {
                    escapado = true;
                } else if (c == '"') {
                    enCadena = false;
                }
            } else if (c == '"') {
                enCadena = true;
            } else if (c == '{') {
                profundidad++;
            } else if (c == '}' && --profundidad == 0) {
                return s.substring(inicio, i + 1);
            }
        }
        throw error("El JSON está incompleto: faltan llaves de cierre. ¿Se cortó la respuesta de tu IA?");
    }

    private static String describirSintaxis(JsonProcessingException e) {
        String motivo = e.getOriginalMessage() == null ? "" : e.getOriginalMessage().lines().findFirst().orElse("");
        if (motivo.length() > 140) {
            motivo = motivo.substring(0, 140) + "…";
        }
        JsonLocation donde = e.getLocation();
        String posicion = donde != null && donde.getLineNr() > 0
                ? " (línea " + donde.getLineNr() + ", columna " + donde.getColumnNr() + ")"
                : "";
        return "El JSON tiene un error de sintaxis" + posicion + ": " + motivo;
    }

    private static AppException error(String motivo) {
        return new AppException(ErrorCode.VALIDACION, "No se pudo leer el JSON.", Map.of(CAMPO, motivo));
    }
}
