package com.adela.ia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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

    /** Comentario HTML al inicio de un archivo: documenta el archivo para quien lo edita, no es para el modelo. */
    private static final Pattern CABECERA_COMENTARIO = Pattern.compile("\\A<!--.*?-->\\s*", Pattern.DOTALL);

    /** Cada instrumento es un archivo {@code <id>_cuestionario.json}; el id va en mayúsculas. */
    private static final String SUFIJO_JSON = "_cuestionario.json";

    /** Encabezado de una ficha: {@code ## <ID>}. */
    private static final Pattern ENCABEZADO_FICHA = Pattern.compile("(?m)^##[ \\t]+(\\S+)");

    private final String versionPrompt;
    private final String prefijoNelse;
    private final String instruccionesExternas;
    private final String correccion;
    private final String refinamiento;
    private final Set<String> catalogoCargado;
    private final Map<String, String> jsonCatalogo;
    private final Set<String> terminosCatalogo;

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
        String correccionCruda = leer(RUTA_PROMPT + "04_correccion.md");
        this.correccion = CABECERA_COMENTARIO.matcher(correccionCruda).replaceFirst("");
        String refinamientoCrudo = leer(RUTA_PROMPT + "05_refinamiento.md");
        this.refinamiento = CABECERA_COMENTARIO.matcher(refinamientoCrudo).replaceFirst("");
        // Las fichas nombran los instrumentos y resumen sus baremos: viajan con el catálogo, no con el repositorio.
        String fichas = leerCatalogo("fichas.md", catalogoDir, desdeClasspath);

        // id -> JSON de los instrumentos encontrados, en orden alfabético. Sin fichas no hay catálogo.
        Map<String, String> jsonCatalogo = fichas == null ? Map.of() : descubrirCatalogo(catalogoDir, desdeClasspath);
        if (fichas == null) {
            logger.warn("Sin catálogo de instrumentos (falta fichas.md): Nelse trabajará sin reconocer instrumentos. "
                    + "Ponlo en ia/catalogo/ del classpath o define IA_CATALOGO_DIR.");
        } else if (jsonCatalogo.isEmpty()) {
            logger.warn("El catálogo tiene fichas pero ningún *{}: Nelse trabajará sin ejemplos. "
                    + "Ponlos en ia/catalogo/ del classpath o define IA_CATALOGO_DIR.", SUFIJO_JSON);
        }
        this.catalogoCargado = Collections.unmodifiableSet(new LinkedHashSet<>(jsonCatalogo.keySet()));
        this.jsonCatalogo = Collections.unmodifiableMap(new TreeMap<>(jsonCatalogo));
        this.terminosCatalogo = fichas == null ? Set.of() : terminos(om, fichas, jsonCatalogo);

        // La versión cambia con el prompt y con los instrumentos que lo acompañan. La corrección y
        // el refinamiento entran aunque no estén en el prefijo: cambiarlos cambia lo que Nelse
        // hace, y las métricas que citan la versión deben poder distinguirlo.
        StringBuilder crudo = new StringBuilder().append(externo).append(reglas).append(motor).append(ejemplo)
                .append(correccionCruda).append(refinamientoCrudo);
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
        if (jsonCatalogo.isEmpty()) {
            // Sin ejemplos del catálogo, el genérico mantiene una referencia de formato y de análisis.
            prefijo.append("\n\n# EXAMPLE (generic instrument, format reference only)\n")
                    .append(compacto(om, ejemplo, "ejemplo externo"));
        }
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

    /**
     * Instrucciones del turno de corrección, para enviar como mensaje de usuario después del
     * documento (nunca dentro del prefijo cacheado). Terminan en el encabezado de la lista:
     * el orquestador agrega debajo un "- ubicación: mensaje" por error duro.
     */
    public String correccion() {
        return correccion;
    }

    /**
     * Instrucciones del turno de refinamiento, como mensaje de usuario tras el JSON vigente
     * (nunca en el prefijo). Terminan en el encabezado de las respuestas: el orquestador
     * agrega debajo lo que la persona contestó.
     */
    public String refinamiento() {
        return refinamiento;
    }

    /** Ids de los instrumentos del catálogo que se encontraron, en el orden del prefijo (alfabético). */
    public Set<String> catalogoCargado() {
        return catalogoCargado;
    }

    /**
     * JSON del instrumento del catálogo con ese id (sin importar mayúsculas), para comparar
     * contra lo que generó el modelo. Vacío si no se cargó. No toca el prefijo ni la versión.
     */
    public Optional<String> catalogoJson(String id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(jsonCatalogo.get(id.strip().toUpperCase(Locale.ROOT)));
    }

    /**
     * Nombres con los que se puede reconocer un instrumento del catálogo: ids,
     * siglas, nombres, autores y los encabezados de las fichas. Sirven para que
     * ningún texto que ve el usuario los nombre. Vacío si no hay catálogo.
     */
    public Set<String> terminosCatalogo() {
        return terminosCatalogo;
    }

    private static String leer(String ruta) {
        try {
            return normalizar(StreamUtils.copyToString(new ClassPathResource(ruta).getInputStream(),
                    StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el recurso " + ruta, e);
        }
    }

    /**
     * Los *_cuestionario.json del classpath (también dentro del jar empaquetado) o,
     * si ahí no hay ninguno, los del directorio configurado. El id es el nombre
     * del archivo sin el sufijo, en mayúsculas; el orden es alfabético por id.
     */
    private static Map<String, String> descubrirCatalogo(String dir, boolean desdeClasspath) {
        Map<String, String> encontrados = new TreeMap<>();
        try {
            if (desdeClasspath) {
                Resource[] recursos = new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:" + RUTA_CATALOGO + "*" + SUFIJO_JSON);
                for (Resource recurso : recursos) {
                    String id = idDe(recurso.getFilename());
                    if (!id.isEmpty()) {
                        encontrados.putIfAbsent(id, normalizar(
                                StreamUtils.copyToString(recurso.getInputStream(), StandardCharsets.UTF_8)));
                    }
                }
            }
            if (encontrados.isEmpty() && dir != null && !dir.isBlank() && Files.isDirectory(Path.of(dir.strip()))) {
                List<Path> archivos;
                try (Stream<Path> lista = Files.list(Path.of(dir.strip()))) {
                    archivos = lista.filter(p -> Files.isRegularFile(p) && Files.isReadable(p)
                            && p.getFileName().toString().endsWith(SUFIJO_JSON)).toList();
                }
                for (Path archivo : archivos) {
                    String id = idDe(archivo.getFileName().toString());
                    if (!id.isEmpty()) {
                        encontrados.putIfAbsent(id, normalizar(Files.readString(archivo, StandardCharsets.UTF_8)));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el catálogo de instrumentos", e);
        }
        return encontrados;
    }

    private static String idDe(String archivo) {
        return archivo == null ? "" : archivo.substring(0, archivo.length() - SUFIJO_JSON.length()).strip()
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Con qué nombres se reconoce cada instrumento cargado: id (y su primera
     * parte si lleva guion bajo), siglas, nombre, autor y cada palabra con
     * mayúscula inicial del autor, más los encabezados de las fichas.
     */
    private static Set<String> terminos(ObjectMapper om, String fichas, Map<String, String> jsonCatalogo) {
        Set<String> terminos = new LinkedHashSet<>();
        Matcher encabezados = ENCABEZADO_FICHA.matcher(fichas);
        while (encabezados.find()) {
            agregarId(terminos, encabezados.group(1));
        }
        jsonCatalogo.forEach((id, json) -> {
            agregarId(terminos, id);
            JsonNode instrumento;
            try {
                instrumento = om.readTree(json);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("El JSON de " + id + " no es válido", e);
            }
            agregar(terminos, instrumento.path("siglas").asText(""));
            agregar(terminos, instrumento.path("nombre").asText(""));
            String autor = instrumento.path("autor").asText("");
            agregar(terminos, autor);
            for (String palabra : autor.split("[^\\p{L}]+")) {
                if (palabra.length() >= 4 && Character.isUpperCase(palabra.codePointAt(0))) {
                    terminos.add(palabra);
                }
            }
        });
        return Collections.unmodifiableSet(terminos);
    }

    private static void agregarId(Set<String> terminos, String id) {
        agregar(terminos, id);
        int guion = id.indexOf('_');
        if (guion > 0) {
            agregar(terminos, id.substring(0, guion));
        }
    }

    private static void agregar(Set<String> terminos, String termino) {
        if (termino != null && !termino.isBlank()) {
            terminos.add(termino.strip());
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
