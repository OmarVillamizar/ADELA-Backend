package com.adela.ia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Única fuente del prompt: lee una vez los archivos de {@code ia/prompt} y,
 * si existen, los de {@code ia/catalogo}, y arma con ellos el prefijo de Nelse
 * y las instrucciones que la persona copia a su propia IA. Ninguno de los dos
 * se edita a mano.
 *
 * El catálogo es opcional: sus JSON tienen licencia de terceros y no van en el
 * repositorio. Se buscan en el classpath y, si faltan, en el directorio
 * {@code adela.ia.catalogo-dir}; sin ellos el prefijo no lleva ejemplos. Las
 * instrucciones externas nunca usan el catálogo; solo la versión que citan
 * refleja qué instrumentos se cargaron.
 *
 * Todo se calcula en el constructor y no depende de la hora ni de ningún dato
 * dinámico: el prefijo es idéntico byte a byte entre llamadas e instancias, que
 * es lo que permite reutilizarlo desde la caché del proveedor.
 */
@Component
public class PromptIa {

    private static final Logger logger = LoggerFactory.getLogger(PromptIa.class);

    private static final String RUTA_PROMPT = "ia/prompt/";
    private static final String RUTA_CATALOGO = "ia/catalogo/";
    private static final String MARCADOR_VERSION = "{{versionPrompt}}";

    /** Un bloque exclusivo de Nelse, con el salto de línea que lo antecede para no dejar huecos. */
    private static final Pattern SOLO_NELSE = Pattern.compile("\n?<!-- solo-nelse -->.*?<!-- /solo-nelse -->",
            Pattern.DOTALL);
    private static final Pattern SALTOS = Pattern.compile("\n{3,}");

    /** Instrumentos del catálogo en el orden fijo del prefijo: id y archivo. */
    private record Instrumento(String id, String archivo) {
    }

    private static final List<Instrumento> CATALOGO = List.of(new Instrumento("CHAEA", "chaea"),
            new Instrumento("ILS", "ils"), new Instrumento("VARK", "vark"),
            new Instrumento("KOLB_3_1", "kolb_3_1"), new Instrumento("HERRMANN_JIMENEZ", "herrmann"),
            new Instrumento("RSPQ2F", "rspq2f"));

    private final String versionPrompt;
    private final String prefijoNelse;
    private final String instruccionesExternas;
    private final Set<String> catalogoCargado;

    /** catalogoDir puede ser null o vacío: entonces solo se mira el classpath. */
    @Autowired
    public PromptIa(ObjectMapper om, @Value("${adela.ia.catalogo-dir:}") String catalogoDir) {
        this(om, catalogoDir, true);
    }

    /** desdeClasspath = false ignora ia/catalogo/ del classpath: así se prueba la ausencia del catálogo. */
    PromptIa(ObjectMapper om, String catalogoDir, boolean desdeClasspath) {
        String externo = leer(RUTA_PROMPT + "00_externo.md");
        String reglas = leer(RUTA_PROMPT + "01_reglas.md");
        String motor = leer(RUTA_PROMPT + "02_motor.md");
        String ejemplo = leer(RUTA_PROMPT + "ejemplo_externo.json");
        // Las fichas nombran los instrumentos y resumen sus baremos: viajan con el catálogo, no con el repositorio.
        String fichas = leerCatalogo("fichas.md", catalogoDir, desdeClasspath);

        // id -> JSON, solo de los instrumentos encontrados y en el orden fijo. Sin fichas no hay catálogo.
        Map<String, String> jsonCatalogo = new LinkedHashMap<>();
        List<String> faltantes = new ArrayList<>();
        for (Instrumento i : CATALOGO) {
            String json = fichas == null ? null
                    : leerCatalogo(i.archivo() + "_cuestionario.json", catalogoDir, desdeClasspath);
            if (json == null) {
                faltantes.add(i.id());
            } else {
                jsonCatalogo.put(i.id(), json);
            }
        }
        if (fichas == null) {
            logger.warn("Sin catálogo de instrumentos (falta fichas.md): Nelse trabajará sin reconocer instrumentos. "
                    + "Ponlo en ia/catalogo/ del classpath o define IA_CATALOGO_DIR.");
        } else if (!faltantes.isEmpty()) {
            logger.warn("Catálogo de instrumentos incompleto, faltan {}. Nelse trabajará sin esos ejemplos. "
                    + "Ponlos en ia/catalogo/ del classpath o define IA_CATALOGO_DIR.", faltantes);
        }
        this.catalogoCargado = Collections.unmodifiableSet(new LinkedHashSet<>(jsonCatalogo.keySet()));

        // La versión cambia con el prompt y con los instrumentos que lo acompañan.
        StringBuilder crudo = new StringBuilder().append(externo).append(reglas).append(motor).append(ejemplo);
        if (fichas != null) {
            crudo.append(fichas);
        }
        jsonCatalogo.forEach((id, json) -> crudo.append(id).append('\n').append(json));
        this.versionPrompt = sha256(crudo.toString()).substring(0, 12);

        // Sin catálogo, las reglas pierden sus bloques de catálogo, como en las instrucciones externas.
        String reglasExternas = SOLO_NELSE.matcher(reglas).replaceAll("");
        StringBuilder prefijo = new StringBuilder(fichas == null ? reglasExternas : reglas).append("\n\n")
                .append(motor);
        if (fichas != null) {
            prefijo.append("\n\n").append(fichas);
        }
        jsonCatalogo.forEach((id, json) -> prefijo.append("\n\nEXAMPLE ").append(id).append(":\n")
                .append(compacto(om, json, id)));
        this.prefijoNelse = SALTOS.matcher(prefijo.toString()).replaceAll("\n\n");

        // Los archivos terminan en salto de línea: al unirlos con "\n\n" quedarían tres seguidos.
        // El ejemplo va compacto: legible para la IA y la mitad de largo para quien lo copia.
        String externas = externo.replace(MARCADOR_VERSION, versionPrompt) + "\n\n" + reglasExternas + "\n\n"
                + motor + "\n\n# EXAMPLE (generic instrument, format reference only)\n```json\n"
                + compacto(om, ejemplo, "ejemplo externo") + "\n```\n";
        this.instruccionesExternas = SALTOS.matcher(externas).replaceAll("\n\n");
    }

    /** Primeros 12 caracteres hexadecimales del SHA-256 del prompt y de los instrumentos cargados. */
    public String versionPrompt() {
        return versionPrompt;
    }

    /** Prefijo fijo de Nelse: reglas, motor, catálogo y el JSON de cada instrumento cargado. */
    public String prefijoNelse() {
        return prefijoNelse;
    }

    /** Instrucciones para la IA externa: sin catálogo ni nombres de instrumentos, con un ejemplo genérico. */
    public String instruccionesExternas() {
        return instruccionesExternas;
    }

    /** Ids de los instrumentos del catálogo que se encontraron, en el orden del prefijo. */
    public Set<String> catalogoCargado() {
        return catalogoCargado;
    }

    private static String leer(String ruta) {
        try {
            return normalizar(StreamUtils.copyToString(new ClassPathResource(ruta).getInputStream(),
                    StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el recurso " + ruta, e);
        }
    }

    /** Del classpath si está; si no, del directorio configurado; si no, null. */
    private static String leerCatalogo(String archivo, String dir, boolean desdeClasspath) {
        if (desdeClasspath && new ClassPathResource(RUTA_CATALOGO + archivo).exists()) {
            return leer(RUTA_CATALOGO + archivo);
        }
        if (dir != null && !dir.isBlank()) {
            Path ruta = Path.of(dir.strip(), archivo);
            if (Files.isRegularFile(ruta) && Files.isReadable(ruta)) {
                try {
                    return normalizar(Files.readString(ruta, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException("No se pudo leer " + ruta, e);
                }
            }
        }
        return null;
    }

    /** Fin de línea unificado: así la versión no cambia según cómo se descargó el repositorio. */
    private static String normalizar(String texto) {
        return texto.replace("\r\n", "\n");
    }

    private static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String compacto(ObjectMapper om, String json, String id) {
        try {
            return om.writeValueAsString(om.readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("El JSON de " + id + " no es válido", e);
        }
    }
}
