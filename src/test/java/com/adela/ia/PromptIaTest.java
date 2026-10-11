package com.adela.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * El prefijo de Nelse se cachea en el proveedor: tiene que ser idéntico byte a
 * byte. Las instrucciones externas salen de los mismos archivos, sin el
 * catálogo ni los nombres de instrumentos.
 *
 * El catálogo tiene licencia de terceros y no va en el repositorio: las
 * pruebas que lo necesitan se omiten cuando falta.
 */
class PromptIaTest {

    private static final List<String> IDS = List.of("CHAEA", "ILS", "VARK", "KOLB_3_1", "HERRMANN_JIMENEZ",
            "RSPQ2F");

    private static PromptIa nuevo() {
        return new PromptIa(new ObjectMapper(), null);
    }

    /** Sin tocar el classpath: solo lo que haya en el directorio. */
    private static PromptIa conDirectorio(Path dir) {
        return new PromptIa(new ObjectMapper(), dir.toString(), false);
    }

    @Test
    @DisplayName("El prefijo de Nelse es idéntico entre llamadas e instancias")
    void prefijoEstable() {
        PromptIa a = nuevo();
        assertEquals(a.prefijoNelse(), a.prefijoNelse());
        assertEquals(a.prefijoNelse(), nuevo().prefijoNelse());
    }

    @Test
    @DisplayName("El prefijo lleva los seis instrumentos del catálogo, en orden, y conserva las marcas solo-nelse")
    void prefijoConCatalogo() {
        PromptIa prompt = nuevo();
        assumeTrue(prompt.catalogoCargado().containsAll(IDS), "El catálogo no está en el classpath");
        assertEquals(IDS, List.copyOf(prompt.catalogoCargado()));

        String prefijo = prompt.prefijoNelse();
        int anterior = -1;
        for (String id : IDS) {
            int posicion = prefijo.indexOf("EXAMPLE " + id + ":\n{");
            assertTrue(posicion > anterior, "Falta o está fuera de orden el ejemplo " + id);
            anterior = posicion;
        }
        assertTrue(prefijo.contains("<!-- solo-nelse -->"));
        assertFalse(prefijo.contains("{{versionPrompt}}"));
    }

    @Test
    @DisplayName("Sin catálogo el prefijo no lleva ejemplos y no falla")
    void sinCatalogo(@TempDir Path vacio) {
        PromptIa prompt = conDirectorio(vacio);

        assertTrue(prompt.catalogoCargado().isEmpty());
        for (String id : IDS) {
            assertFalse(prompt.prefijoNelse().contains("EXAMPLE " + id + ":"), id);
        }
        assertTrue(prompt.prefijoNelse().startsWith("# ROLE"));
        // Sin fichas no hay catálogo: las reglas quedan como en las instrucciones externas.
        assertFalse(prompt.prefijoNelse().contains("# CATALOG"));
        assertFalse(prompt.prefijoNelse().contains("solo-nelse"));
        assertTrue(prompt.versionPrompt().matches("[0-9a-f]{12}"));
        assertEquals(prompt.prefijoNelse(), conDirectorio(vacio).prefijoNelse());
    }

    @Test
    @DisplayName("Sin directorio configurado ni catálogo en el classpath tampoco falla")
    void sinDirectorio() {
        PromptIa prompt = new PromptIa(new ObjectMapper(), "", false);

        assertTrue(prompt.catalogoCargado().isEmpty());
        assertFalse(prompt.prefijoNelse().contains("EXAMPLE VARK:"));
    }

    @Test
    @DisplayName("Un directorio con parte del catálogo carga solo esos ejemplos y cambia la versión")
    void catalogoParcial(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("fichas.md"), "# CATALOG\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("vark_cuestionario.json"), "{\n \"nombre\": \"X\"\n}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("ils_cuestionario.json"), "{\"nombre\": \"Y\"}", StandardCharsets.UTF_8);

        PromptIa prompt = conDirectorio(dir);

        // En el orden fijo (ILS antes que VARK), sin importar cuál se escribió primero.
        assertEquals(List.of("ILS", "VARK"), List.copyOf(prompt.catalogoCargado()));
        String prefijo = prompt.prefijoNelse();
        assertTrue(prefijo.endsWith("\n\nEXAMPLE ILS:\n{\"nombre\":\"Y\"}\n\nEXAMPLE VARK:\n{\"nombre\":\"X\"}"),
                prefijo.substring(prefijo.length() - 80));
        assertFalse(prefijo.contains("EXAMPLE CHAEA:"));
        assertNotEquals(conDirectorio(dir.resolve("no-existe")).versionPrompt(), prompt.versionPrompt());
        assertEquals(Set.of(), conDirectorio(dir.resolve("no-existe")).catalogoCargado());
    }

    @Test
    @DisplayName("Las instrucciones externas no dependen del catálogo, salvo la versión que citan")
    void externasIndependientesDelCatalogo(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("vark_cuestionario.json"), "{\"nombre\": \"X\"}", StandardCharsets.UTF_8);
        PromptIa sin = conDirectorio(dir.resolve("no-existe"));
        PromptIa con = conDirectorio(dir);

        assertEquals(sin.instruccionesExternas().replace(sin.versionPrompt(), "V"),
                con.instruccionesExternas().replace(con.versionPrompt(), "V"));
    }

    @Test
    @DisplayName("versionPrompt son 12 caracteres hexadecimales y no cambia entre instancias")
    void versionPrompt() {
        String version = nuevo().versionPrompt();
        assertTrue(version.matches("[0-9a-f]{12}"), version);
        assertEquals(version, nuevo().versionPrompt());
    }

    @Test
    @DisplayName("Las instrucciones externas llevan la cabecera y la versión, y ninguna marca interna")
    void externasConCabecera() {
        PromptIa prompt = nuevo();
        String externas = prompt.instruccionesExternas();

        assertTrue(externas.contains("adela-cuestionario/1"));
        assertTrue(externas.contains(prompt.versionPrompt()));
        assertTrue(externas.contains("```json"));
        assertFalse(externas.contains("solo-nelse"));
        assertFalse(externas.contains("{{versionPrompt}}"));
        assertEquals(externas, nuevo().instruccionesExternas());
    }

    @Test
    @DisplayName("Las instrucciones externas no nombran instrumentos ni llevan el catálogo")
    void externasSinCatalogo() {
        String externas = nuevo().instruccionesExternas();

        for (String nombre : List.of("CHAEA", "VARK", "KOLB", "HERRMANN", "RSPQ")) {
            assertFalse(externas.toUpperCase().contains(nombre), "Nombra " + nombre);
        }
        assertFalse(Pattern.compile("\\bILS\\b", Pattern.CASE_INSENSITIVE).matcher(externas).find(),
                "Nombra ILS");
        assertFalse(externas.contains("CATALOGO"));
        assertFalse(externas.contains("CATALOG MATCHING"));
        assertFalse(externas.contains("\n\n\n"));
        // Sí lleva el motor y el ejemplo genérico.
        assertTrue(externas.contains("# ENGINE FORMAT"));
        assertTrue(externas.contains("EAE-12"));
    }
}
