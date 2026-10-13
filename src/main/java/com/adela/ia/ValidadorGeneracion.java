package com.adela.ia;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.FormatoItem;
import com.adela.calificacion.TipoEstilo;
import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.InterpretacionDTO.BandaDTO;
import com.adela.dto.InterpretacionDTO.EscalonDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.PreguntaDTO;
import com.adela.services.ValidacionCuestionario;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Valida lo que una IA entregó, sin guardar nada, en cuatro capas:
 * L1 lectura del JSON a los DTO; L2 los validadores del servidor (los del POST);
 * L3 las reglas que hoy solo exige el editor; L4 procedencia y cobertura.
 *
 * Los errores de L1 a L3 son duros: el cuestionario no se podría crear. L4 y
 * todo lo que dice la IA se devuelve como marcas, que la persona revisa en el
 * editor pero no bloquean. Corrige en silencio solo lo que el asistente
 * calcula por su cuenta, y siempre deja una marca.
 */
@Component
public class ValidadorGeneracion {

    static final String FORMATO = "adela-cuestionario/1";

    private static final Map<String, EsquemaInterpretacion> ESQUEMA_DE_LECTURA = Map.of(
            "SOLO_PUNTAJES", EsquemaInterpretacion.NINGUNA,
            "NIVELES", EsquemaInterpretacion.BAREMO,
            "BAREMO_MODELO", EsquemaInterpretacion.BAREMO,
            "PREDOMINANTE", EsquemaInterpretacion.RELATIVO,
            "DOMINANCIA", EsquemaInterpretacion.NIVEL_SUPERIOR,
            "ESCALONADO", EsquemaInterpretacion.RELATIVO_ESCALONADO,
            "CUADRANTES", EsquemaInterpretacion.CUADRANTES);

    /** Lecturas cuyas bandas calcula ADELA; las cifras de la IA se descartan. */
    private static final Set<String> LECTURAS_CON_BANDAS = Set.of("NIVELES", "BAREMO_MODELO", "DOMINANCIA");

    private static final Set<String> SECCIONES = Set.of("clave", "items", "estilos", "compuestos", "bandas",
            "escalones", "plano", "esquema", "complementaria", "meta");

    private static final List<String> RIESGOS = List.of(Marca.ALTO, Marca.MEDIO, Marca.BAJO);

    /**
     * "ítem 4", "ítems 4 y 11", "items 1, 4, 7", "preguntas 1-6": la palabra y la
     * lista de números que la sigue.
     */
    private static final String RANGO = "\\d+(?:\\s*[-–—]\\s*\\d+)?";
    private static final Pattern REFERENCIA = Pattern.compile("(?<![\\p{L}\\p{N}])(?:ítems?|items?|preguntas?)\\s*"
            + "(?:n[º°]\\s*)?#?\\s*(" + RANGO + "(?:(?:\\s*[,;/&]\\s*|\\s+(?:y|e|o|and)\\s+)" + RANGO + ")*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NUMERO_O_RANGO = Pattern.compile("(\\d+)(?:\\s*[-–—]\\s*(\\d+))?");

    /** Una marca y la prioridad con la que la IA la ordenó, para desempatar. */
    private record Candidata(Marca marca, int prioridad) {
    }

    private final ObjectMapper om;

    /** Quita los nombres de instrumentos del catálogo de lo que ve el usuario. */
    private final SaneadorTerminos saneador;

    /** L4 contra lo que Nelse le dio al modelo y contra el catálogo; solo la usa la vía Nelse. */
    private final ProcedenciaNelse procedencia;

    public ValidadorGeneracion(ObjectMapper om, PromptIa promptIa) {
        // Como el POST: las propiedades desconocidas se ignoran. Un null dentro de una lista es
        // un error de lectura con su ruta, no un NullPointerException más adelante.
        this.om = om.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.om.configOverride(List.class).setSetterInfo(JsonSetter.Value.forContentNulls(Nulls.FAIL));
        this.saneador = new SaneadorTerminos(promptIa.terminosCatalogo());
        this.procedencia = new ProcedenciaNelse(this.om, promptIa);
    }

    /** Importación (JSON de una IA externa): no hay documento, así que no hay chequeos de procedencia contra él. */
    public ResultadoImportacion validar(LectorJsonTolerante.Entrada entrada) {
        return validar(entrada, null);
    }

    /**
     * Vía Nelse: además de L1 a L4 de la importación, comprueba contra la fuente que el
     * modelo recibió que las cifras, el catálogo y los enunciados no sean inventados.
     * Con fuente null es exactamente la importación.
     */
    public ResultadoImportacion validar(LectorJsonTolerante.Entrada entrada, FuenteNelse fuente) {
        CuestionarioDTO dto;
        try {
            dto = om.treeToValue(entrada.cuestionario(), CuestionarioDTO.class);
        } catch (JsonProcessingException e) {
            return new ResultadoImportacion(null, null, List.of(), Map.of("json", describirLectura(e)));
        }

        List<Candidata> marcas = new ArrayList<>();
        AnalisisIa analisis = leerAnalisis(entrada.analisis(), marcas);
        if (entrada.formato() != null && !FORMATO.equals(entrada.formato())) {
            agregar(marcas, Marca.validador(Marca.BAJO, "meta", List.of(),
                    "El formato '" + entrada.formato() + "' no es " + FORMATO
                            + ": puede venir de una versión anterior de las instrucciones.",
                    "Si el editor abre el cuestionario sin errores, sigue; si no, pide a tu IA que use las "
                            + "instrucciones actuales."));
        }

        String lectura = normalizarLectura(dto, analisis, marcas);

        Map<String, String> errores = new LinkedHashMap<>(ValidacionCuestionario.validarSinGuardar(dto, lectura));
        reglasDelEditor(dto, errores, marcas);
        // La cobertura calcula rangos: solo tiene sentido con estilos y preguntas bien formados.
        if (errores.keySet().stream().noneMatch(k -> k.startsWith("estilos") || k.startsWith("preguntas"))) {
            CoberturaInterpretacion.revisar(dto).forEach(m -> agregar(marcas, m));
        }
        // Se avisa, nunca se edita el texto: lo decide la persona.
        ContenidoSospechoso.revisar(dto).forEach(m -> agregar(marcas, m));
        marcasDeOrigen(analisis, lectura, dto, marcas);
        marcasDelModelo(analisis, dto, marcas);
        if (fuente != null) {
            procedencia.revisar(dto, analisis, fuente).forEach(m -> agregar(marcas, m));
        }
        // Solo el informe y las marcas: el cuestionario es del usuario y no se toca. Se sanea antes de
        // ordenar para que dos mensajes que solo diferían en el nombre queden como un duplicado.
        marcas.replaceAll(c -> new Candidata(saneador.sanear(c.marca()), c.prioridad()));
        return new ResultadoImportacion(dto, saneador.sanear(analisis), ordenar(marcas), errores);
    }

    // ---------------------------------------------------------------- L1

    private AnalisisIa leerAnalisis(JsonNode nodo, List<Candidata> marcas) {
        if (nodo == null) {
            return null;
        }
        try {
            return om.treeToValue(nodo, AnalisisIa.class);
        } catch (JsonProcessingException e) {
            agregar(marcas, Marca.validador(Marca.BAJO, "meta", List.of(),
                    "No se pudo leer el análisis de la IA; se ignoró.",
                    "Sin el informe de la IA no hay guía: revisa tú mismo la clave, los baremos y la lectura."));
            return null;
        }
    }

    /** Ruta del campo y motivo, en una sola frase que se puede pegar de vuelta a la IA. */
    private static String describirLectura(JsonProcessingException e) {
        StringBuilder ruta = new StringBuilder();
        if (e instanceof JsonMappingException m) {
            for (JsonMappingException.Reference r : m.getPath()) {
                if (r.getFieldName() != null) {
                    ruta.append(ruta.length() > 0 ? "." : "").append(r.getFieldName());
                } else if (r.getIndex() >= 0) {
                    ruta.append('[').append(r.getIndex()).append(']');
                }
            }
        }
        String motivo;
        if (e instanceof InvalidFormatException f && f.getTargetType() != null && f.getTargetType().isEnum()) {
            motivo = "el valor '" + f.getValue() + "' no es válido; usa uno de: "
                    + Arrays.stream(f.getTargetType().getEnumConstants()).map(Object::toString)
                            .collect(Collectors.joining(", "));
        } else if (e instanceof InvalidFormatException f) {
            motivo = "el valor '" + f.getValue() + "' no tiene el formato esperado";
        } else {
            motivo = e.getOriginalMessage() == null ? "" : e.getOriginalMessage().lines().findFirst().orElse("");
            if (motivo.length() > 160) {
                motivo = motivo.substring(0, 160) + "…";
            }
        }
        return ruta.length() == 0 ? motivo : ruta + ": " + motivo;
    }

    // ---------------------------------------------------------------- Convención

    /**
     * Si la IA eligió una lectura del asistente, el esquema es el de esa
     * lectura y los arreglos que la lectura llena quedan vacíos: ADELA los
     * calcula con su regla y las cifras de la IA serían inventadas. Devuelve la
     * lectura válida, o null.
     */
    private static String normalizarLectura(CuestionarioDTO dto, AnalisisIa analisis, List<Candidata> marcas) {
        String declarada = analisis == null || analisis.lectura() == null ? null
                : analisis.lectura().lecturaAsistente();
        if (declarada == null || declarada.isBlank()) {
            return null;
        }
        String lectura = declarada.strip().toUpperCase(Locale.ROOT);
        EsquemaInterpretacion esperado = ESQUEMA_DE_LECTURA.get(lectura);
        if (esperado == null) {
            agregar(marcas, Marca.validador(Marca.MEDIO, "esquema", List.of(),
                    "La IA pidió una lectura que ADELA no conoce ('" + declarada.strip() + "'); se ignoró.",
                    "Elige el esquema de lectura a mano en el editor."));
            return null;
        }

        InterpretacionDTO actual = dto.getInterpretacion();
        if (actual == null) {
            dto.setInterpretacion(new InterpretacionDTO(esperado, 10d, null, List.of(), List.of()));
            agregar(marcas, Marca.validador(Marca.BAJO, "esquema", List.of(),
                    "La IA no incluyó la interpretación; ADELA la creó con la lectura " + lectura + ".",
                    "Confirma en el editor que esa es la lectura que quieres."));
            return lectura;
        }
        if (actual.esquema() != esperado) {
            agregar(marcas, Marca.validador(Marca.MEDIO, "esquema", List.of(),
                    "La lectura " + lectura + " corresponde al esquema " + esperado + ", pero la IA puso "
                            + (actual.esquema() == null ? "ninguno" : actual.esquema()) + "; se corrigió.",
                    "Confirma en el editor que el esquema es el que quieres."));
            actual = conArreglos(actual, esperado, actual.bandas(), actual.escalones());
        }
        if (LECTURAS_CON_BANDAS.contains(lectura) && actual.bandas() != null && !actual.bandas().isEmpty()) {
            agregar(marcas, Marca.validador(Marca.BAJO, "bandas", List.of(),
                    "ADELA calcula estos niveles con su regla; se ignoraron las cifras de la IA.",
                    "Revisa los niveles que ADELA calculó en el editor."));
            actual = conArreglos(actual, actual.esquema(), List.of(), actual.escalones());
        }
        if (lectura.equals("ESCALONADO") && actual.escalones() != null && !actual.escalones().isEmpty()) {
            agregar(marcas, Marca.validador(Marca.BAJO, "escalones", List.of(),
                    "ADELA calcula estos escalones con su regla; se ignoraron las cifras de la IA.",
                    "Revisa la tabla de escalones que ADELA calculó en el editor."));
            actual = conArreglos(actual, actual.esquema(), actual.bandas(), List.of());
        }
        dto.setInterpretacion(actual);
        return lectura;
    }

    private static InterpretacionDTO conArreglos(InterpretacionDTO i, EsquemaInterpretacion esquema,
            List<BandaDTO> bandas, List<EscalonDTO> escalones) {
        return new InterpretacionDTO(esquema, i.delta(), i.esIpsativo(), bandas, escalones, i.plano(), i.estilos(),
                i.complementaria());
    }

    // ---------------------------------------------------------------- L3

    /** Lo que el editor exige antes de guardar y el servidor, hasta hoy, dejaba pasar. */
    private static void reglasDelEditor(CuestionarioDTO dto, Map<String, String> errores,
            List<Candidata> marcas) {
        exigir(errores, "nombre", dto.getNombre(), "el nombre");
        exigir(errores, "siglas", dto.getSiglas(), "las siglas");
        exigir(errores, "descripcion", dto.getDescripcion(), "la descripción");
        exigir(errores, "autor", dto.getAutor(), "el autor");
        exigir(errores, "version", dto.getVersion(), "la versión");
        if (dto.getDescripcion() != null && dto.getDescripcion().length() > 1000) {
            errores.putIfAbsent("descripcion", "La descripción admite hasta 1000 caracteres.");
        }

        List<PreguntaDTO> preguntas = dto.getPreguntas() == null ? List.of() : dto.getPreguntas();
        Set<Integer> puntuados = new HashSet<>();
        boolean ordenCorrelativo = correlativo(preguntas.stream().map(PreguntaDTO::getOrden).toList());
        for (int i = 0; i < preguntas.size(); i++) {
            PreguntaDTO p = preguntas.get(i);
            List<OpcionDTO> opciones = p.getOpciones() == null ? List.of() : p.getOpciones();
            int minimo = p.getFormato() == FormatoItem.MULTIPLE ? 1 : 2;
            if (opciones.size() < minimo) {
                errores.putIfAbsent("preguntas[" + i + "]",
                        "La pregunta " + p.getOrden() + " necesita al menos " + minimo + " opciones.");
            }
            ordenCorrelativo &= correlativo(opciones.stream().map(OpcionDTO::getOrden).toList());
            for (OpcionDTO o : opciones) {
                if (o.getPesos() != null) {
                    o.getPesos().stream().filter(w -> w.peso() != null && w.peso() != 0)
                            .forEach(w -> puntuados.add(w.estiloId()));
                }
            }
        }
        if (!preguntas.isEmpty() && dto.getEstilos() != null) {
            for (int i = 0; i < dto.getEstilos().size(); i++) {
                EstiloDTO e = dto.getEstilos().get(i);
                boolean primario = e.getTipo() == null || e.getTipo() == TipoEstilo.PRIMARIO;
                if (primario && e.getNombre() != null && !e.getNombre().isBlank() && !puntuados.contains(e.getId())) {
                    errores.putIfAbsent("estilos[" + i + "]", "Ninguna opción suma al estilo '"
                            + e.getNombre().strip() + "': su puntaje sería siempre 0.");
                }
            }
        }
        if (!ordenCorrelativo) {
            agregar(marcas, Marca.validador(Marca.MEDIO, "items", List.of(),
                    "El orden de las preguntas u opciones no es correlativo; ADELA usará el orden de la lista.",
                    "Compara la numeración con la del documento original."));
        }
    }

    private static void exigir(Map<String, String> errores, String campo, String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            errores.putIfAbsent(campo, "Falta " + nombre + ".");
        }
    }

    /** Los órdenes, en cualquier posición, son exactamente 1..n. */
    private static boolean correlativo(List<Integer> ordenes) {
        List<Integer> ordenados = ordenes.stream().sorted().toList();
        for (int i = 0; i < ordenados.size(); i++) {
            if (ordenados.get(i) != i + 1) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- Procedencia

    private static void marcasDeOrigen(AnalisisIa analisis, String lectura, CuestionarioDTO dto,
            List<Candidata> marcas) {
        if (analisis == null || analisis.origen() == null) {
            return;
        }
        boolean convencionSinLectura = false;
        for (Map.Entry<String, String> entrada : analisis.origen().entrySet()) {
            String seccion = entrada.getKey() == null ? "" : entrada.getKey().strip().toLowerCase(Locale.ROOT);
            if (!SECCIONES.contains(seccion)) {
                seccion = "meta";
            }
            String origen = entrada.getValue() == null ? "" : entrada.getValue().strip().toUpperCase(Locale.ROOT);
            switch (origen) {
            case "INFERIDO" -> agregar(marcas, Marca.validador(Marca.ALTO, seccion, List.of(),
                    "Deducido por la IA, no tomado del documento: verifícalo.",
                    "Compáralo con el documento original o con quien conozca el instrumento."));
            case "GENERADO" -> {
                if (seccion.equals("items")) {
                    agregar(marcas, Marca.validador(Marca.ALTO, "items", List.of(),
                            "Preguntas creadas por la IA: no están validadas.",
                            "Léelas una a una; un instrumento nuevo necesita validación psicométrica."));
                }
            }
            // El instrumento de referencia no se nombra (licencia y regla de la interfaz): se describe
            // por los estilos del propio cuestionario, que la persona sí reconoce.
            case "CATALOGO" -> agregar(marcas, Marca.validador(Marca.MEDIO, seccion, List.of(),
                    "La IA lo tomó de su conocimiento interno: un cuestionario muy parecido" + estilosDe(dto)
                            + ". Confirma que aplica a tu versión.",
                    "Si tienes el manual o la hoja de corrección de tu versión, confirma que las cifras "
                            + "coinciden. Si no, responde el cuestionario de prueba y mira si el resultado tiene sentido."));
            case "CONVENCION" -> convencionSinLectura |= lectura == null;
            default -> {
            }
            }
        }
        if (convencionSinLectura) {
            agregar(marcas, Marca.validador(Marca.MEDIO, "esquema", List.of(),
                    "La IA dice que aplicó una convención de ADELA pero no indicó cuál: de dónde salen "
                            + "las cifras no está claro.",
                    "Elige la lectura en el editor para que ADELA calcule los números con su regla."));
        }
    }

    /** ", con estilos similares (A, B, C)" o nada si el cuestionario no trae estilos primarios. */
    private static String estilosDe(CuestionarioDTO dto) {
        List<String> nombres = dto.getEstilos() == null ? List.of()
                : dto.getEstilos().stream().filter(e -> e != null && e.getTipo() == TipoEstilo.PRIMARIO)
                        .map(EstiloDTO::getNombre).filter(n -> n != null && !n.isBlank()).toList();
        return nombres.isEmpty() ? "" : ", con estilos similares (" + String.join(", ", nombres) + ")";
    }

    private static void marcasDelModelo(AnalisisIa analisis, CuestionarioDTO dto, List<Candidata> marcas) {
        if (analisis == null || analisis.revision() == null) {
            return;
        }
        int preguntas = dto.getPreguntas() == null ? 0 : dto.getPreguntas().size();
        for (AnalisisIa.Revision r : analisis.revision()) {
            if (r == null) {
                continue;
            }
            String ubicacion = r.ubicacion() == null ? "" : r.ubicacion().strip();
            String porque = r.porque() == null ? "" : r.porque().strip();
            String mensaje = ubicacion.isEmpty() ? porque : porque.isEmpty() ? ubicacion : ubicacion + ": " + porque;
            if (mensaje.isEmpty()) {
                continue;
            }
            String riesgo = r.riesgo() == null ? "" : r.riesgo().strip().toUpperCase(Locale.ROOT);
            String seccion = r.seccion() == null ? "" : r.seccion().strip().toLowerCase(Locale.ROOT);
            Marca marca = new Marca(Marca.MODELO, RIESGOS.contains(riesgo) ? riesgo : Marca.MEDIO,
                    SECCIONES.contains(seccion) ? seccion : "meta", referencias(ubicacion + " " + porque, preguntas),
                    mensaje, r.comoVerificar());
            marcas.add(new Candidata(marca, r.prioridad() == null ? Integer.MAX_VALUE : r.prioridad()));
        }
    }

    /** Ítems nombrados en el texto ("ítems 4 y 11", "1-6"), solo los que existen: "pregunta:N". */
    static List<String> referencias(String texto, int preguntas) {
        Set<String> refs = new LinkedHashSet<>();
        Matcher lista = REFERENCIA.matcher(texto);
        while (lista.find()) {
            Matcher numeros = NUMERO_O_RANGO.matcher(lista.group(1));
            while (numeros.find()) {
                long desde = Long.parseLong(numeros.group(1));
                long hasta = numeros.group(2) == null ? desde : Long.parseLong(numeros.group(2));
                for (long n = Math.max(desde, 1); n <= Math.min(hasta, preguntas); n++) {
                    refs.add("pregunta:" + n);
                }
            }
        }
        return List.copyOf(refs);
    }

    // ---------------------------------------------------------------- Marcas

    private static void agregar(List<Candidata> marcas, Marca marca) {
        marcas.add(new Candidata(marca, Integer.MAX_VALUE));
    }

    /** Por riesgo; a igual riesgo, primero lo que detectó ADELA y luego lo que dijo la IA, por su prioridad. */
    private static List<Marca> ordenar(List<Candidata> marcas) {
        List<Candidata> ordenadas = new ArrayList<>(marcas);
        ordenadas.sort(Comparator.comparingInt((Candidata c) -> RIESGOS.indexOf(c.marca().riesgo()))
                .thenComparingInt(c -> Marca.VALIDADOR.equals(c.marca().origen()) ? 0 : 1)
                .thenComparingInt(Candidata::prioridad));
        Set<String> vistas = new HashSet<>();
        List<Marca> resultado = new ArrayList<>();
        for (Candidata c : ordenadas) {
            if (vistas.add(c.marca().seccion() + "|" + c.marca().mensaje())) {
                resultado.add(c.marca());
            }
        }
        return resultado;
    }
}
