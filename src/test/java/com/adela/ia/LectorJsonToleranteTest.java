package com.adela.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * La IA entrega el JSON envuelto en markdown, con prosa alrededor o con llaves
 * dentro de los textos: ninguna de esas formas debe impedir la importación.
 */
class LectorJsonToleranteTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String PLANO = """
            {"nombre": "Escala", "estilos": [{"id": 1, "nombre": "A"}], "preguntas": []}""";

    private static final String ENVOLTORIO = """
            {"formato": "adela-cuestionario/1", "cuestionario": {"nombre": "Escala"},
             "analisis": {"modo": "TRANSCRIPCION"}}""";

    private static LectorJsonTolerante.Entrada leer(String texto) {
        return LectorJsonTolerante.leer(texto, OM);
    }

    private static String campoJson(AppException e) {
        assertEquals(ErrorCode.VALIDACION, e.getCode());
        assertNotNull(e.getFields());
        return e.getFields().get("json");
    }

    @Test
    @DisplayName("Un bloque ```json con texto alrededor se lee")
    void conCercaJson() {
        String texto = "Aquí tienes el cuestionario {listo}:\n\n```json\n" + ENVOLTORIO
                + "\n```\n\nRevisa los ítems 3 y 4. Si algo falla, avísame.";

        LectorJsonTolerante.Entrada e = leer(texto);

        assertEquals("Escala", e.cuestionario().get("nombre").asText());
        assertEquals("TRANSCRIPCION", e.analisis().get("modo").asText());
        assertEquals("adela-cuestionario/1", e.formato());
    }

    @Test
    @DisplayName("Con varias cercas se toma la primera ```json")
    void primeraCercaJson() {
        String texto = "```\nnada que ver\n```\n```json\n" + ENVOLTORIO + "\n```\n```json\n{\"otro\": 1}\n```";

        assertEquals("Escala", leer(texto).cuestionario().get("nombre").asText());
    }

    @Test
    @DisplayName("Una cerca sin lenguaje también se lee")
    void conCercaSinLenguaje() {
        assertEquals("Escala", leer("```\n" + ENVOLTORIO + "\n```").cuestionario().get("nombre").asText());
    }

    @Test
    @DisplayName("Un objeto suelto con prosa antes y después se lee")
    void objetoSueltoConProsa() {
        LectorJsonTolerante.Entrada e = leer("Claro, este es el resultado:\n" + ENVOLTORIO + "\nQuedo atento.");

        assertEquals("Escala", e.cuestionario().get("nombre").asText());
    }

    @Test
    @DisplayName("Las llaves y comillas escapadas dentro de un texto no cortan el objeto")
    void llavesDentroDeCadenas() {
        String texto = "Resultado: {\"formato\": \"adela-cuestionario/1\", \"cuestionario\": "
                + "{\"nombre\": \"Escala } con {llaves} y \\\"comillas\\\" \\\\\"}} fin {";

        LectorJsonTolerante.Entrada e = leer(texto);

        assertEquals("Escala } con {llaves} y \"comillas\" \\", e.cuestionario().get("nombre").asText());
    }

    @Test
    @DisplayName("El cuestionario plano (preguntas y estilos) se lee sin análisis")
    void formaPlana() {
        LectorJsonTolerante.Entrada e = leer(PLANO);

        assertEquals("Escala", e.cuestionario().get("nombre").asText());
        assertNull(e.analisis());
        assertNull(e.formato());
    }

    @Test
    @DisplayName("El envoltorio sin análisis ni formato también se lee")
    void envoltorioMinimo() {
        LectorJsonTolerante.Entrada e = leer("{\"cuestionario\": {\"nombre\": \"Escala\"}}");

        assertEquals("Escala", e.cuestionario().get("nombre").asText());
        assertNull(e.analisis());
        assertNull(e.formato());
    }

    @Test
    @DisplayName("Texto vacío o nulo pide pegar el JSON")
    void vacio() {
        for (String texto : new String[] { null, "", "   \n" }) {
            AppException e = assertThrows(AppException.class, () -> leer(texto));
            assertEquals("Pega el JSON que te entregó tu IA.", campoJson(e));
        }
    }

    @Test
    @DisplayName("Texto sin ningún objeto JSON se rechaza con el campo json")
    void basura() {
        AppException e = assertThrows(AppException.class, () -> leer("Lo siento, no pude generar el cuestionario."));

        assertTrue(campoJson(e).contains("JSON"));
    }

    @Test
    @DisplayName("Un JSON cortado a la mitad dice que está incompleto")
    void incompleto() {
        AppException e = assertThrows(AppException.class,
                () -> leer("```json\n{\"cuestionario\": {\"nombre\": \"Esc"));

        assertTrue(campoJson(e).contains("incompleto"), campoJson(e));
    }

    @Test
    @DisplayName("Un error de sintaxis indica línea y columna")
    void sintaxis() {
        AppException e = assertThrows(AppException.class,
                () -> leer("{\n  \"cuestionario\": {\"nombre\": \"Escala\",},\n  \"x\" 1\n}"));

        assertTrue(campoJson(e).contains("línea"), campoJson(e));
    }

    @Test
    @DisplayName("Un objeto que no es un cuestionario de ADELA se rechaza")
    void noEsCuestionario() {
        AppException e = assertThrows(AppException.class, () -> leer("{\"nombre\": \"Escala\", \"preguntas\": []}"));

        assertTrue(campoJson(e).startsWith("No parece un cuestionario de ADELA"));
    }

    @Test
    @DisplayName("Un texto de más de dos millones de caracteres se rechaza")
    void demasiadoLargo() {
        AppException e = assertThrows(AppException.class,
                () -> leer("{" + " ".repeat(LectorJsonTolerante.MAX_CARACTERES) + "}"));

        assertTrue(campoJson(e).contains("demasiado largo"));
    }
}
