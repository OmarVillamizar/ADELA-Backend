package com.adela.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * El patch lo escribe un modelo: solo add, replace y remove, y cualquier cosa que no
 * encaje se rechaza en vez de adivinarse.
 */
class AplicadorJsonPatchTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private static JsonNode json(String texto) throws IOException {
        return OM.readTree(texto);
    }

    private static JsonNode aplicar(String raiz, String patch) throws IOException {
        return AplicadorJsonPatch.aplicar(json(raiz), json(patch));
    }

    private static PatchInvalidoException rechaza(String raiz, String patch) throws IOException {
        return assertThrows(PatchInvalidoException.class, () -> aplicar(raiz, patch));
    }

    // ---------------------------------------------------------------- add

    @Test
    @DisplayName("add sobre un objeto crea el miembro y, si existe, lo reemplaza")
    void addEnObjeto() throws IOException {
        assertEquals(json("{\"a\":1,\"b\":2}"), aplicar("{\"a\":1}", "[{\"op\":\"add\",\"path\":\"/b\",\"value\":2}]"));
        assertEquals(json("{\"a\":9}"), aplicar("{\"a\":1}", "[{\"op\":\"add\",\"path\":\"/a\",\"value\":9}]"));
    }

    @Test
    @DisplayName("add en un arreglo inserta en el índice y desplaza el resto; el tamaño y '-' añaden al final")
    void addEnArreglo() throws IOException {
        String raiz = "{\"l\":[\"a\",\"b\"]}";
        assertEquals(json("{\"l\":[\"x\",\"a\",\"b\"]}"),
                aplicar(raiz, "[{\"op\":\"add\",\"path\":\"/l/0\",\"value\":\"x\"}]"));
        assertEquals(json("{\"l\":[\"a\",\"x\",\"b\"]}"),
                aplicar(raiz, "[{\"op\":\"add\",\"path\":\"/l/1\",\"value\":\"x\"}]"));
        assertEquals(json("{\"l\":[\"a\",\"b\",\"x\"]}"),
                aplicar(raiz, "[{\"op\":\"add\",\"path\":\"/l/2\",\"value\":\"x\"}]"));
        assertEquals(json("{\"l\":[\"a\",\"b\",\"x\"]}"),
                aplicar(raiz, "[{\"op\":\"add\",\"path\":\"/l/-\",\"value\":\"x\"}]"));
    }

    @Test
    @DisplayName("add acepta objetos, arreglos y null como valor")
    void addValoresCompuestos() throws IOException {
        assertEquals(json("{\"a\":{\"x\":[1,2]},\"n\":null}"),
                aplicar("{}", "[{\"op\":\"add\",\"path\":\"/a\",\"value\":{\"x\":[1,2]}},"
                        + "{\"op\":\"add\",\"path\":\"/n\",\"value\":null}]"));
    }

    @Test
    @DisplayName("add falla si el índice pasa del tamaño, si el padre no existe o si el padre es un valor")
    void addInvalido() throws IOException {
        rechaza("{\"l\":[1]}", "[{\"op\":\"add\",\"path\":\"/l/3\",\"value\":0}]");
        rechaza("{}", "[{\"op\":\"add\",\"path\":\"/a/b\",\"value\":0}]");
        rechaza("{\"a\":5}", "[{\"op\":\"add\",\"path\":\"/a/b\",\"value\":0}]");
        rechaza("{\"a\":\"texto\"}", "[{\"op\":\"add\",\"path\":\"/a/0\",\"value\":0}]");
    }

    // ---------------------------------------------------------------- replace

    @Test
    @DisplayName("replace cambia un miembro u elemento existente, a cualquier tipo")
    void replace() throws IOException {
        assertEquals(json("{\"a\":{\"b\":[1,7,3]}}"),
                aplicar("{\"a\":{\"b\":[1,2,3]}}", "[{\"op\":\"replace\",\"path\":\"/a/b/1\",\"value\":7}]"));
        assertEquals(json("{\"a\":\"ahora texto\"}"),
                aplicar("{\"a\":[1,2]}", "[{\"op\":\"replace\",\"path\":\"/a\",\"value\":\"ahora texto\"}]"));
    }

    @Test
    @DisplayName("replace exige que el destino exista")
    void replaceInexistente() throws IOException {
        rechaza("{\"a\":1}", "[{\"op\":\"replace\",\"path\":\"/b\",\"value\":2}]");
        rechaza("{\"l\":[1,2]}", "[{\"op\":\"replace\",\"path\":\"/l/2\",\"value\":2}]");
        rechaza("{\"l\":[1,2]}", "[{\"op\":\"replace\",\"path\":\"/l/-\",\"value\":2}]");
    }

    // ---------------------------------------------------------------- remove

    @Test
    @DisplayName("remove quita un miembro o un elemento, que debe existir")
    void remove() throws IOException {
        assertEquals(json("{\"b\":2}"), aplicar("{\"a\":1,\"b\":2}", "[{\"op\":\"remove\",\"path\":\"/a\"}]"));
        assertEquals(json("{\"l\":[1,3]}"), aplicar("{\"l\":[1,2,3]}", "[{\"op\":\"remove\",\"path\":\"/l/1\"}]"));
        rechaza("{\"a\":1}", "[{\"op\":\"remove\",\"path\":\"/b\"}]");
        rechaza("{\"l\":[1]}", "[{\"op\":\"remove\",\"path\":\"/l/1\"}]");
        rechaza("{\"l\":[1]}", "[{\"op\":\"remove\",\"path\":\"/l/-\"}]");
    }

    @Test
    @DisplayName("Quitar varios elementos de un arreglo funciona de mayor a menor índice; de menor a mayor quita otros")
    void quitarVariosDeMayorAMenor() throws IOException {
        String raiz = "{\"p\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}";

        JsonNode bien = aplicar(raiz, "[{\"op\":\"remove\",\"path\":\"/p/4\"},{\"op\":\"remove\",\"path\":\"/p/2\"},"
                + "{\"op\":\"remove\",\"path\":\"/p/0\"}]");
        assertEquals(json("{\"p\":[\"b\",\"d\",\"f\"]}"), bien);

        // Al revés, cada remove ve el arreglo ya desplazado: /p/2 ya no es "c" sino "d", y los
        // índices altos acaban fuera de rango.
        JsonNode mal = aplicar(raiz, "[{\"op\":\"remove\",\"path\":\"/p/0\"},{\"op\":\"remove\",\"path\":\"/p/2\"}]");
        assertEquals(json("{\"p\":[\"b\",\"c\",\"e\",\"f\"]}"), mal);
        rechaza(raiz, "[{\"op\":\"remove\",\"path\":\"/p/0\"},{\"op\":\"remove\",\"path\":\"/p/2\"},"
                + "{\"op\":\"remove\",\"path\":\"/p/4\"},{\"op\":\"remove\",\"path\":\"/p/5\"}]");
    }

    // ---------------------------------------------------------------- Punteros

    @Test
    @DisplayName("~0 es '~' y ~1 es '/' dentro de un nombre")
    void escapes() throws IOException {
        JsonNode r = aplicar("{\"a/b\":1,\"m~n\":2,\"~1\":3}",
                "[{\"op\":\"replace\",\"path\":\"/a~1b\",\"value\":10},{\"op\":\"replace\",\"path\":\"/m~0n\",\"value\":20},"
                        + "{\"op\":\"replace\",\"path\":\"/~01\",\"value\":30}]");

        assertEquals(json("{\"a/b\":10,\"m~n\":20,\"~1\":30}"), r);
    }

    @Test
    @DisplayName("Un nombre vacío ('/') es un miembro más y la ruta profunda recorre objetos y arreglos")
    void rutaProfundaYNombreVacio() throws IOException {
        assertEquals(json("{\"\":2}"), aplicar("{\"\":1}", "[{\"op\":\"replace\",\"path\":\"/\",\"value\":2}]"));
        assertEquals(json("{\"q\":[{\"o\":[{\"pesos\":[{\"peso\":5}]}]}]}"),
                aplicar("{\"q\":[{\"o\":[{\"pesos\":[{\"peso\":1}]}]}]}",
                        "[{\"op\":\"replace\",\"path\":\"/q/0/o/0/pesos/0/peso\",\"value\":5}]"));
    }

    @Test
    @DisplayName("Los índices son decimales sin ceros a la izquierda ni signo; un nombre no numérico falla")
    void indicesEstrictos() throws IOException {
        for (String indice : new String[] { "01", "+1", "-1", "1.0", "x", "", "0x1", "1e0" }) {
            rechaza("{\"l\":[1,2,3]}", "[{\"op\":\"replace\",\"path\":\"/l/" + indice + "\",\"value\":0}]");
        }
        rechaza("{\"l\":[1,2,3]}", "[{\"op\":\"replace\",\"path\":\"/l/99999999999\",\"value\":0}]");
        // '-' tampoco sirve para recorrer.
        rechaza("{\"l\":[{\"a\":1}]}", "[{\"op\":\"replace\",\"path\":\"/l/-/a\",\"value\":0}]");
    }

    @Test
    @DisplayName("Una ruta sin '/' inicial o que no existe en el medio se rechaza")
    void rutasMalas() throws IOException {
        rechaza("{\"a\":1}", "[{\"op\":\"replace\",\"path\":\"a\",\"value\":0}]");
        rechaza("{\"a\":{}}", "[{\"op\":\"replace\",\"path\":\"/z/b\",\"value\":0}]");
        rechaza("{\"l\":[{\"a\":1}]}", "[{\"op\":\"replace\",\"path\":\"/l/3/a\",\"value\":0}]");
    }

    // ---------------------------------------------------------------- Raíz

    @Test
    @DisplayName("La raíz no se puede reemplazar, quitar ni añadir")
    void raizRechazada() throws IOException {
        rechaza("{\"a\":1}", "[{\"op\":\"replace\",\"path\":\"\",\"value\":{}}]");
        rechaza("{\"a\":1}", "[{\"op\":\"remove\",\"path\":\"\"}]");
        rechaza("{\"a\":1}", "[{\"op\":\"add\",\"path\":\"\",\"value\":{}}]");
    }

    // ---------------------------------------------------------------- Forma del patch

    @Test
    @DisplayName("Acepta el arreglo y el envoltorio {\"patch\": [...]}; un patch vacío no cambia nada")
    void formasDelPatch() throws IOException {
        String raiz = "{\"a\":1}";
        String op = "{\"op\":\"replace\",\"path\":\"/a\",\"value\":2}";

        assertEquals(json("{\"a\":2}"), aplicar(raiz, "[" + op + "]"));
        assertEquals(json("{\"a\":2}"), aplicar(raiz, "{\"patch\":[" + op + "]}"));
        assertEquals(json(raiz), aplicar(raiz, "[]"));
        assertEquals(json(raiz), aplicar(raiz, "{\"patch\":[]}"));
    }

    @Test
    @DisplayName("Un patch que no es arreglo, o cuyo 'patch' no lo es, se rechaza")
    void patchMalFormado() throws IOException {
        rechaza("{}", "{\"patch\":\"nada\"}");
        rechaza("{}", "{\"ops\":[]}");
        rechaza("{}", "\"texto\"");
        rechaza("{}", "null");
        assertThrows(PatchInvalidoException.class, () -> AplicadorJsonPatch.aplicar(json("{}"), null));
        assertThrows(PatchInvalidoException.class, () -> AplicadorJsonPatch.aplicar(null, json("[]")));
    }

    @Test
    @DisplayName("move, copy, test y cualquier otra operación se rechazan")
    void operacionesNoPermitidas() throws IOException {
        for (String op : new String[] { "move", "copy", "test", "ADD", "borrar" }) {
            rechaza("{\"a\":1,\"b\":2}", "[{\"op\":\"" + op + "\",\"path\":\"/a\",\"from\":\"/b\",\"value\":1}]");
        }
    }

    @Test
    @DisplayName("Operaciones mal formadas: sin op, sin path, sin value, op o path que no son texto, no objeto")
    void operacionesMalFormadas() throws IOException {
        rechaza("{\"a\":1}", "[{\"path\":\"/a\",\"value\":1}]");
        rechaza("{\"a\":1}", "[{\"op\":\"remove\"}]");
        rechaza("{\"a\":1}", "[{\"op\":\"replace\",\"path\":\"/a\"}]");
        rechaza("{\"a\":1}", "[{\"op\":\"add\",\"path\":\"/b\"}]");
        rechaza("{\"a\":1}", "[{\"op\":5,\"path\":\"/a\"}]");
        rechaza("{\"a\":1}", "[{\"op\":\"remove\",\"path\":[\"a\"]}]");
        rechaza("{\"a\":1}", "[\"remove /a\"]");
        rechaza("{\"a\":1}", "[null]");
    }

    // ---------------------------------------------------------------- Inmutabilidad y orden

    @Test
    @DisplayName("Devuelve un árbol nuevo: ni la raíz ni el patch cambian, y el valor insertado no queda compartido")
    void inmutable() throws IOException {
        JsonNode raiz = json("{\"a\":[1,2],\"o\":{\"x\":1}}");
        JsonNode copiaRaiz = raiz.deepCopy();
        JsonNode patch = json("[{\"op\":\"add\",\"path\":\"/a/-\",\"value\":{\"k\":1}},"
                + "{\"op\":\"remove\",\"path\":\"/o/x\"},{\"op\":\"replace\",\"path\":\"/a/0\",\"value\":9}]");
        JsonNode copiaPatch = patch.deepCopy();

        JsonNode resultado = AplicadorJsonPatch.aplicar(raiz, patch);

        assertNotSame(raiz, resultado);
        assertEquals(copiaRaiz, raiz);
        assertEquals(copiaPatch, patch);
        assertEquals(json("{\"a\":[9,2,{\"k\":1}],\"o\":{}}"), resultado);
        // Cambiar el resultado después no toca el valor del patch.
        ((com.fasterxml.jackson.databind.node.ObjectNode) resultado.get("a").get(2)).put("k", 2);
        assertEquals(copiaPatch, patch);
    }

    @Test
    @DisplayName("Si una operación falla, la raíz original queda intacta")
    void falloALaMitad() throws IOException {
        JsonNode raiz = json("{\"a\":1}");
        JsonNode patch = json("[{\"op\":\"replace\",\"path\":\"/a\",\"value\":2},{\"op\":\"remove\",\"path\":\"/z\"}]");

        assertThrows(PatchInvalidoException.class, () -> AplicadorJsonPatch.aplicar(raiz, patch));

        assertEquals(json("{\"a\":1}"), raiz);
    }

    @Test
    @DisplayName("Cada operación ve el resultado de la anterior")
    void secuencial() throws IOException {
        JsonNode r = aplicar("{}", "[{\"op\":\"add\",\"path\":\"/l\",\"value\":[]},"
                + "{\"op\":\"add\",\"path\":\"/l/-\",\"value\":1},{\"op\":\"add\",\"path\":\"/l/0\",\"value\":0},"
                + "{\"op\":\"replace\",\"path\":\"/l/1\",\"value\":5}]");

        assertEquals(json("{\"l\":[0,5]}"), r);
    }

    @Test
    @DisplayName("El mensaje es corto, en español, numera la operación y no copia la ruta entera ni valores")
    void mensaje() throws IOException {
        String ruta = "/" + "x".repeat(500);
        PatchInvalidoException e = rechaza("{}",
                "[{\"op\":\"replace\",\"path\":\"" + ruta + "\",\"value\":\"SECRETO-largo\"}]");

        assertTrue(e.getMessage().startsWith("Operación 1 del patch:"), e.getMessage());
        assertTrue(e.getMessage().length() < 200, e.getMessage());
        assertTrue(!e.getMessage().contains("SECRETO"), e.getMessage());
    }
}
