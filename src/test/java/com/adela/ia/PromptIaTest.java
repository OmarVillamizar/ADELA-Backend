package com.adela.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * El prefijo de Nelse se cachea en el proveedor: tiene que ser idéntico byte a
 * byte. Las instrucciones externas salen de los mismos archivos, sin el
 * catálogo ni los nombres de sus instrumentos.
 *
 * El catálogo real tiene licencia de terceros y no va en el repositorio: las
 * pruebas que lo necesitan se omiten cuando falta, y el resto usa catálogos
 * falsos en un directorio temporal.
 */
class PromptIaTest {

    private static final String FICHAS = "# CATALOG\nIntro.\n\n## ALFA\n- Ficha de alfa.\n\n## BETA\n- Ficha de beta.\n";

    private static PromptIa nuevo() {
        return new PromptIa(new ObjectMapper(), null);
    }

    /** Sin tocar el classpath: solo lo que haya en el directorio. */
    private static PromptIa conDirectorio(Path dir) {
        return new PromptIa(new ObjectMapper(), dir.toString(), false);
    }

    private static void archivo(Path dir, String nombre, String contenido) throws IOException {
        Files.writeString(dir.resolve(nombre), contenido, StandardCharsets.UTF_8);
    }

    private static Pattern palabraCompleta(String termino) {
        return Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(termino) + "(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    // ---------------------------------------------------------------- Prefijo

    @Test
    @DisplayName("El prefijo de Nelse es idéntico entre llamadas e instancias")
    void prefijoEstable() {
        PromptIa a = nuevo();
        assertEquals(a.prefijoNelse(), a.prefijoNelse());
        assertEquals(a.prefijoNelse(), nuevo().prefijoNelse());
    }

    @Test
    @DisplayName("Con el catálogo del classpath, cada instrumento cargado es un ejemplo, en orden alfabético")
    void prefijoConCatalogoDelClasspath() {
        PromptIa prompt = nuevo();
        assumeFalse(prompt.catalogoCargado().isEmpty(), "El catálogo no está en el classpath");

        List<String> ids = List.copyOf(prompt.catalogoCargado());
        assertEquals(ids.stream().sorted().toList(), ids);
        int anterior = -1;
        for (String id : ids) {
            int posicion = prompt.prefijoNelse().indexOf("EXAMPLE " + id + ":\n{");
            assertTrue(posicion > anterior, "Falta o está fuera de orden el ejemplo " + id);
            anterior = posicion;
        }
        assertTrue(prompt.prefijoNelse().contains("<!-- solo-nelse -->"));
        assertFalse(prompt.prefijoNelse().contains("{{versionPrompt}}"));
    }

    @Test
    @DisplayName("Un catálogo en un directorio se descubre solo, por orden alfabético de id")
    void descubreElDirectorio(@TempDir Path dir) throws IOException {
        archivo(dir, "beta_cuestionario.json", "{\n \"nombre\": \"B\"\n}");
        archivo(dir, "alfa_cuestionario.json", "{\"nombre\": \"A\"}");
        archivo(dir, "notas.json", "{\"nombre\": \"ignorado\"}");
        archivo(dir, "fichas.md", FICHAS);

        PromptIa prompt = conDirectorio(dir);

        assertEquals(List.of("ALFA", "BETA"), List.copyOf(prompt.catalogoCargado()));
        String prefijo = prompt.prefijoNelse();
        assertTrue(prefijo.endsWith("\n\nEXAMPLE ALFA:\n{\"nombre\":\"A\"}\n\nEXAMPLE BETA:\n{\"nombre\":\"B\"}"),
                prefijo.substring(prefijo.length() - 80));
        assertTrue(prefijo.contains("## ALFA") && prefijo.contains("## BETA"));
        assertTrue(prefijo.contains("<!-- solo-nelse -->"));
        assertEquals(prefijo, conDirectorio(dir).prefijoNelse());
    }

    @Test
    @DisplayName("El id es el nombre del archivo en mayúsculas, con sus guiones bajos")
    void idConGuionBajo(@TempDir Path dir) throws IOException {
        archivo(dir, "gama_3_1_cuestionario.json", "{\"nombre\": \"G\"}");
        archivo(dir, "fichas.md", "## GAMA_3_1\n");

        PromptIa prompt = conDirectorio(dir);

        assertEquals(List.of("GAMA_3_1"), List.copyOf(prompt.catalogoCargado()));
        assertTrue(prompt.prefijoNelse().contains("EXAMPLE GAMA_3_1:\n"));
    }

    @Test
    @DisplayName("Sin catálogo el prefijo no lleva ejemplos ni bloques de catálogo y no falla")
    void sinCatalogo(@TempDir Path vacio) {
        PromptIa prompt = conDirectorio(vacio);

        assertTrue(prompt.catalogoCargado().isEmpty());
        assertTrue(prompt.terminosCatalogo().isEmpty());
        assertFalse(Pattern.compile("EXAMPLE [A-Z0-9_]+:\\n\\{").matcher(prompt.prefijoNelse()).find());
        assertFalse(prompt.prefijoNelse().contains("solo-nelse"));
        assertTrue(prompt.prefijoNelse().startsWith("# ROLE"));
        // Sin catálogo queda el ejemplo genérico como referencia de formato.
        assertTrue(prompt.prefijoNelse().contains("# EXAMPLE (generic instrument, format reference only)\n{"));
        assertTrue(prompt.versionPrompt().matches("[0-9a-f]{12}"));
        assertEquals(prompt.prefijoNelse(), conDirectorio(vacio).prefijoNelse());
    }

    @Test
    @DisplayName("Sin directorio configurado ni catálogo en el classpath tampoco falla")
    void sinDirectorio() {
        PromptIa prompt = new PromptIa(new ObjectMapper(), "", false);

        assertTrue(prompt.catalogoCargado().isEmpty());
        assertTrue(prompt.terminosCatalogo().isEmpty());
    }

    @Test
    @DisplayName("Los JSON sin fichas no son un catálogo")
    void sinFichas(@TempDir Path dir) throws IOException {
        archivo(dir, "alfa_cuestionario.json", "{\"nombre\": \"A\"}");

        PromptIa prompt = conDirectorio(dir);

        assertTrue(prompt.catalogoCargado().isEmpty());
        assertTrue(prompt.terminosCatalogo().isEmpty());
        assertFalse(prompt.prefijoNelse().contains("EXAMPLE ALFA:"));
    }

    @Test
    @DisplayName("Un directorio con parte de los instrumentos carga solo esos y cambia la versión")
    void catalogoParcial(@TempDir Path dir) throws IOException {
        archivo(dir, "alfa_cuestionario.json", "{\"nombre\": \"A\"}");
        archivo(dir, "fichas.md", FICHAS);
        String soloAlfa = conDirectorio(dir).versionPrompt();

        archivo(dir, "beta_cuestionario.json", "{\"nombre\": \"B\"}");
        PromptIa conBeta = conDirectorio(dir);

        assertEquals(List.of("ALFA", "BETA"), List.copyOf(conBeta.catalogoCargado()));
        assertNotEquals(soloAlfa, conBeta.versionPrompt());
        assertNotEquals(soloAlfa, conDirectorio(dir.resolve("no-existe")).versionPrompt());
    }

    @Test
    @DisplayName("El catálogo se descubre también dentro de un jar del classpath")
    void catalogoDentroDeUnJar() throws IOException {
        // Fuera de @TempDir: en Windows el JDK mantiene abierto el jar leído y el borrado automático falla.
        Path jar = Files.createTempFile("catalogo", ".jar");
        jar.toFile().deleteOnExit();
        try (JarOutputStream salida = new JarOutputStream(Files.newOutputStream(jar))) {
            // Los jar empaquetados llevan una entrada por directorio, y el escaneo depende de ellas.
            for (String directorio : List.of("ia/", "ia/catalogo/")) {
                salida.putNextEntry(new JarEntry(directorio));
                salida.closeEntry();
            }
            entrada(salida, "ia/catalogo/zeta_cuestionario.json", "{\"nombre\": \"Z\"}");
            entrada(salida, "ia/catalogo/fichas.md", "## ZETA\n");
        }
        ClassLoader anterior = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader cargador = new URLClassLoader(new URL[] { jar.toUri().toURL() }, anterior)) {
            Thread.currentThread().setContextClassLoader(cargador);

            PromptIa prompt = nuevo();

            assertTrue(prompt.catalogoCargado().contains("ZETA"), prompt.catalogoCargado().toString());
            assertTrue(prompt.prefijoNelse().contains("EXAMPLE ZETA:\n{\"nombre\":\"Z\"}"));
        } finally {
            Thread.currentThread().setContextClassLoader(anterior);
            jar.toFile().delete();
        }
    }

    private static void entrada(JarOutputStream salida, String nombre, String contenido) throws IOException {
        salida.putNextEntry(new JarEntry(nombre));
        salida.write(contenido.getBytes(StandardCharsets.UTF_8));
        salida.closeEntry();
    }

    @Test
    @DisplayName("catalogoJson devuelve el JSON del instrumento por id sin distinguir mayúsculas, y vacío si no está")
    void catalogoJsonPorId(@TempDir Path dir) throws IOException {
        archivo(dir, "alfa_cuestionario.json", "{\"nombre\": \"A\"}");
        archivo(dir, "fichas.md", FICHAS);
        PromptIa prompt = conDirectorio(dir);

        assertEquals("{\"nombre\": \"A\"}", prompt.catalogoJson("ALFA").orElseThrow());
        assertEquals("{\"nombre\": \"A\"}", prompt.catalogoJson(" alfa ").orElseThrow());
        assertTrue(prompt.catalogoJson("BETA").isEmpty());
        assertTrue(prompt.catalogoJson("").isEmpty());
        assertTrue(prompt.catalogoJson(null).isEmpty());
        assertTrue(conDirectorio(dir.resolve("no-existe")).catalogoJson("ALFA").isEmpty());
    }

    // ---------------------------------------------------------------- Corrección

    @Test
    @DisplayName("La corrección se carga sin su comentario de cabecera, termina en la lista de errores y no va en el prefijo")
    void correccion() {
        PromptIa prompt = nuevo();
        String correccion = prompt.correccion();

        assertTrue(correccion.startsWith("# CORRECTION TURN"), correccion.substring(0, 40));
        assertFalse(correccion.contains("<!--"));
        assertTrue(correccion.contains("{\"patch\": []}"));
        assertTrue(correccion.endsWith("# HARD ERRORS TO FIX\n"));
        assertFalse(prompt.prefijoNelse().contains("CORRECTION TURN"));
        assertFalse(prompt.instruccionesExternas().contains("CORRECTION TURN"));
        assertEquals(correccion, nuevo().correccion());
    }

    @Test
    @DisplayName("La corrección no nombra ningún instrumento del catálogo")
    void correccionSinTerminosDelCatalogo() {
        PromptIa prompt = nuevo();
        assumeFalse(prompt.terminosCatalogo().isEmpty(), "El catálogo no está en el classpath");

        for (String termino : prompt.terminosCatalogo()) {
            assertFalse(palabraCompleta(termino).matcher(prompt.correccion()).find(), termino);
        }
    }

    // ---------------------------------------------------------------- Términos

    @Test
    @DisplayName("Los términos reúnen ids, siglas, nombres, autores y encabezados de las fichas")
    void terminos(@TempDir Path dir) throws IOException {
        archivo(dir, "alfa_cuestionario.json",
                "{\"nombre\": \"Escala Alfa de Prueba\", \"siglas\": \"AFX\", \"autor\": \"Ana Pérez Gómez & col.\"}");
        archivo(dir, "gama_3_1_cuestionario.json", "{\"nombre\": \"Gama\", \"siglas\": \" \", \"autor\": null}");
        archivo(dir, "fichas.md", FICHAS + "\n## GAMA_3_1\n- Ficha de gama.\n");

        var terminos = conDirectorio(dir).terminosCatalogo();

        for (String esperado : List.of("ALFA", "AFX", "Escala Alfa de Prueba", "Ana Pérez Gómez & col.", "Pérez",
                "Gómez", "BETA", "GAMA_3_1", "GAMA", "Gama")) {
            assertTrue(terminos.contains(esperado), "Falta " + esperado + " en " + terminos);
        }
        // Palabras cortas o sin mayúscula, y los vacíos, no cuentan.
        for (String ausente : List.of("Ana", "col", "")) {
            assertFalse(terminos.contains(ausente), "Sobra '" + ausente + "'");
        }
        assertTrue(terminos.stream().noneMatch(String::isBlank));
    }

    // ---------------------------------------------------------------- Versión e instrucciones externas

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
    @DisplayName("Las instrucciones externas llevan el motor y un ejemplo genérico, sin catálogo")
    void externasSinCatalogo() {
        String externas = nuevo().instruccionesExternas();

        assertFalse(externas.contains("CATALOG MATCHING"));
        assertFalse(externas.contains("\n\n\n"));
        assertTrue(externas.contains("# ENGINE FORMAT"));
        assertTrue(externas.contains("# EXAMPLE (generic instrument, format reference only)"));
    }

    @Test
    @DisplayName("Las instrucciones externas no contienen ningún término del catálogo como palabra completa")
    void externasSinTerminosDelCatalogo() {
        PromptIa prompt = nuevo();
        assumeFalse(prompt.terminosCatalogo().isEmpty(), "El catálogo no está en el classpath");

        for (String termino : prompt.terminosCatalogo()) {
            assertFalse(palabraCompleta(termino).matcher(prompt.instruccionesExternas()).find(),
                    "Las instrucciones externas nombran un instrumento: " + termino);
        }
    }

    @Test
    @DisplayName("Las instrucciones externas no cambian con el catálogo, salvo la versión que citan")
    void externasIndependientesDelCatalogo(@TempDir Path dir) throws IOException {
        archivo(dir, "alfa_cuestionario.json", "{\"nombre\": \"Escala Alfa\", \"siglas\": \"AFX\"}");
        archivo(dir, "fichas.md", FICHAS);
        PromptIa sin = conDirectorio(dir.resolve("no-existe"));
        PromptIa con = conDirectorio(dir);

        assertEquals(sin.instruccionesExternas().replace(sin.versionPrompt(), "V"),
                con.instruccionesExternas().replace(con.versionPrompt(), "V"));
        for (String termino : con.terminosCatalogo()) {
            assertFalse(palabraCompleta(termino).matcher(con.instruccionesExternas()).find(), termino);
        }
    }
}
