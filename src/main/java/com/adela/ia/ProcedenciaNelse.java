package com.adela.ia;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.adela.calificacion.TipoEstilo;
import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.InterpretacionDTO.BandaDTO;
import com.adela.dto.InterpretacionDTO.EscalonDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.PreguntaDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * L4 con fuente (solo Nelse): comprueba contra lo que la persona aportó y contra el
 * catálogo que el modelo no inventó nada. Todo sale como marca; ninguna bloquea.
 *
 * El texto del documento y el de las instrucciones no son de fiar: de ellos solo se
 * extraen números y se hacen búsquedas, y ningún fragmento se copia a las marcas.
 *
 * Qué cifras se comprueban (las de {@code interpretacion}, que son las que la regla de
 * procedencia exige con fuente):
 * - bandas: límite inferior y superior;
 * - escalones: totalMin, totalMax y distancia;
 * - plano: corteX y corteY, salvo null o 0 (0 es la convención EQUILIBRIO).
 *
 * Y cuáles no, a propósito: los pesos de la clave y los coeficientes de los compuestos
 * (0, 1, -1 o la escala 1..5 son estructura, no datos de una fuente), el delta (no tiene
 * sección en {@code analisis.origen}) y los extremos de cada grupo de bandas o de
 * escalones (el primer límite inferior y el último superior: un documento que dice "hasta 6" o
 * "9 o más" no imprime el tope, y el modelo lo completa con el rango posible, que la
 * cobertura ya revisa).
 */
final class ProcedenciaNelse {

    /** Cifras o números de pregunta que se nombran en un mensaje antes de resumir con "y N más". */
    private static final int MAX_LISTA = 8;
    private static final int MAX_REFS = 10;
    /** Una numeración más corta no basta para decir cuántos ítems tiene el documento. */
    private static final int MIN_NUMERADOS = 5;
    /** Si no se encuentra más de esta fracción de los enunciados, el problema no es de cada uno: es de todos. */
    private static final double UMBRAL_TRADUCCION = 0.5;

    /** 15 dígitos como tope: un número más largo es un código, no una cifra de baremo. */
    private static final Pattern NUMERO = Pattern.compile("(?<!\\p{N})\\d{1,15}(?:[.,]\\d{1,15})?(?!\\p{N})");
    /** Marcador que agrega {@code LimpiadorTexto.unir}: sus números no son del documento. */
    private static final Pattern MARCADOR_PAGINA = Pattern.compile("(?m)^--- página \\d+ ---$");
    /** "12." "12)" "a)" "(a)" al inicio de un enunciado u opción. */
    private static final Pattern NUMERACION_INICIAL = Pattern
            .compile("^\\s*(?:\\(?\\d{1,3}[.):\\-]|\\(?[A-Za-z][.):\\-]|\\([A-Za-z0-9]{1,3}\\))\\s+");
    /** Un token de solo dígitos dentro de un texto ya normalizado (palabras separadas por un espacio). */
    private static final Pattern SOLO_DIGITOS = Pattern.compile("(?<![^ ])\\d+(?![^ ])");
    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^\\p{L}\\p{N}\\p{M}]+");
    private static final Pattern LINEA_NUMERADA = Pattern.compile("(?m)^\\s*(\\d{1,3})[.)-]\\s+\\p{L}");

    private static final String NINGUNO = "NINGUNO";

    private record Cifra(String valor, String estilo) {
    }

    private final ObjectMapper om;
    private final PromptIa promptIa;

    ProcedenciaNelse(ObjectMapper om, PromptIa promptIa) {
        this.om = om;
        this.promptIa = promptIa;
    }

    List<Marca> revisar(CuestionarioDTO dto, AnalisisIa analisis, FuenteNelse fuente) {
        List<Marca> marcas = new ArrayList<>();
        Map<String, String> origenes = origenes(analisis);
        cifrasContraLaFuente(dto, origenes, fuente, marcas);
        contraElCatalogo(dto, analisis, origenes, marcas);
        paginasOmitidas(fuente, marcas);
        // En CREACION no hay documento que copiar: la fidelidad y la numeración no aplican.
        if (!esCreacion(analisis)) {
            fidelidad(dto, fuente, marcas);
        }
        return marcas;
    }

    private static boolean esCreacion(AnalisisIa analisis) {
        return analisis != null && analisis.modo() != null && analisis.modo().strip().equalsIgnoreCase("CREACION");
    }

    // ---------------------------------------------------------------- Cifras contra la fuente

    private static void cifrasContraLaFuente(CuestionarioDTO dto, Map<String, String> origenes, FuenteNelse fuente,
            List<Marca> marcas) {
        InterpretacionDTO i = dto.getInterpretacion();
        if (i == null) {
            return;
        }
        Set<String> deDocumento = numeros(sinMarcadores(fuente.documento()), fuente.profesor());
        Set<String> deProfesor = numeros(fuente.profesor());
        for (String seccion : List.of("bandas", "escalones", "plano")) {
            String origen = origenes.getOrDefault(seccion, "");
            boolean documento = origen.equals("DOCUMENTO");
            if (!documento && !origen.equals("PROFESOR")) {
                continue;
            }
            Set<String> disponibles = documento ? deDocumento : deProfesor;
            Map<String, Set<String>> faltantes = new LinkedHashMap<>();
            for (Cifra c : cifras(i, seccion)) {
                if (!disponibles.contains(c.valor())) {
                    faltantes.computeIfAbsent(c.valor(), k -> new LinkedHashSet<>());
                    if (c.estilo() != null) {
                        faltantes.get(c.valor()).add("estilo:" + c.estilo());
                    }
                }
            }
            if (faltantes.isEmpty()) {
                continue;
            }
            Set<String> refs = new LinkedHashSet<>();
            faltantes.values().forEach(refs::addAll);
            if (documento && fuente.paginasImagen() > 0) {
                // Las cifras de una página escaneada no están en el texto contra el que se busca: que no
                // aparezcan no prueba que se inventaron. Se pide verificarlas, no se acusa (MEDIO, no ALTO).
                marcas.add(Marca.validador(Marca.MEDIO, seccion, limitar(refs, MAX_REFS),
                        "Cifras de '" + seccion + "' que la IA atribuye al documento (" + lista(faltantes.keySet())
                                + ") y no aparecen en el texto; pueden estar en las páginas escaneadas: "
                                + "verifícalas contra el original.",
                        "Búscalas en las páginas escaneadas del original. Si no están, fueron calculadas o "
                                + "inventadas: bórralas o elige una lectura del asistente para que ADELA calcule "
                                + "el baremo."));
                continue;
            }
            String fuenteDicha = documento ? "al documento" : "a tus instrucciones";
            String dondeBuscar = documento ? "en el documento original (o en tus instrucciones)"
                    : "en lo que escribiste en las instrucciones";
            marcas.add(Marca.validador(Marca.ALTO, seccion, limitar(refs, MAX_REFS),
                    "Cifras de '" + seccion + "' que la IA atribuye " + fuenteDicha + " y no aparecen en su texto: "
                            + lista(faltantes.keySet()) + ".",
                    "Busca cada cifra " + dondeBuscar + ". Si no está, fue calculada o inventada: bórrala o "
                            + "elige una lectura del asistente para que ADELA calcule el baremo."));
        }
    }

    /** Las cifras de una sección, ya sin los extremos que el documento no suele imprimir. */
    private static List<Cifra> cifras(InterpretacionDTO i, String seccion) {
        List<Cifra> cifras = new ArrayList<>();
        switch (seccion) {
        case "bandas" -> {
            Map<String, List<BandaDTO>> grupos = new LinkedHashMap<>();
            for (BandaDTO b : i.bandas() == null ? List.<BandaDTO>of() : i.bandas()) {
                grupos.computeIfAbsent(b.estilo() + "\u0000" + b.escala(), k -> new ArrayList<>()).add(b);
            }
            for (List<BandaDTO> grupo : grupos.values()) {
                Double piso = grupo.stream().map(BandaDTO::limiteInferior).filter(ProcedenciaNelse::finito)
                        .min(Comparator.naturalOrder()).orElse(null);
                Double techo = grupo.stream().map(BandaDTO::limiteSuperior).filter(ProcedenciaNelse::finito)
                        .max(Comparator.naturalOrder()).orElse(null);
                for (BandaDTO b : grupo) {
                    agregar(cifras, b.limiteInferior(), piso, b.estilo());
                    agregar(cifras, b.limiteSuperior(), techo, b.estilo());
                }
            }
        }
        case "escalones" -> {
            List<EscalonDTO> escalones = i.escalones() == null ? List.of() : i.escalones();
            Double piso = escalones.stream().map(EscalonDTO::totalMin).filter(ProcedenciaNelse::finito)
                    .min(Comparator.naturalOrder()).orElse(null);
            Double techo = escalones.stream().map(EscalonDTO::totalMax).filter(ProcedenciaNelse::finito)
                    .max(Comparator.naturalOrder()).orElse(null);
            for (EscalonDTO e : escalones) {
                agregar(cifras, e.totalMin(), piso, null);
                agregar(cifras, e.totalMax(), techo, null);
                agregar(cifras, e.distancia(), null, null);
            }
        }
        case "plano" -> {
            if (i.plano() != null) {
                agregar(cifras, i.plano().corteX(), 0d, null);
                agregar(cifras, i.plano().corteY(), 0d, null);
            }
        }
        default -> {
        }
        }
        return cifras;
    }

    /** Suma la cifra salvo que sea el extremo exento (o no sea un número). */
    private static void agregar(List<Cifra> cifras, Double valor, Double exento, String estilo) {
        if (finito(valor) && !valor.equals(exento)) {
            cifras.add(new Cifra(numero(valor), estilo));
        }
    }

    private static boolean finito(Double d) {
        return d != null && Double.isFinite(d);
    }

    // ---------------------------------------------------------------- Catálogo

    private void contraElCatalogo(CuestionarioDTO dto, AnalisisIa analisis, Map<String, String> origenes,
            List<Marca> marcas) {
        if (analisis == null || analisis.reconocido() == null) {
            return;
        }
        AnalisisIa.Reconocido reconocido = analisis.reconocido();
        String id = reconocido.catalogo() == null ? "" : reconocido.catalogo().strip();
        if (id.isEmpty() || id.equalsIgnoreCase(NINGUNO)) {
            return;
        }
        Optional<String> json = promptIa.catalogoJson(id);
        if (json.isEmpty()) {
            // No se repite el id: lo escribió el modelo y no hay nada que mostrar de él.
            marcas.add(Marca.validador(Marca.MEDIO, "meta", List.of(),
                    "La IA reconoció un instrumento que no está cargado en ADELA; no se pudo comparar con él.",
                    "Trata todas las cifras y la clave como si vinieran solo del documento: verifícalas."));
            return;
        }
        CuestionarioDTO referencia;
        try {
            referencia = om.readValue(json.get(), CuestionarioDTO.class);
        } catch (IOException e) {
            return;
        }

        if (Boolean.TRUE.equals(reconocido.estructuraIgual())) {
            List<String> diferencias = diferenciasDeEstructura(dto, referencia);
            if (!diferencias.isEmpty()) {
                marcas.add(Marca.validador(Marca.ALTO, "meta", List.of(),
                        "La IA dice que el cuestionario tiene la misma estructura que el instrumento de referencia, "
                                + "pero difiere en " + String.join("; ", diferencias) + ".",
                        "Si la estructura no es idéntica, las cifras y la clave copiadas del catálogo no valen: "
                                + "deben salir del documento."));
            }
        }
        if ("CATALOGO".equals(origenes.get("clave"))) {
            claveContraElCatalogo(dto, referencia, marcas);
        }
        InterpretacionDTO actual = dto.getInterpretacion();
        InterpretacionDTO esperada = referencia.getInterpretacion();
        if (actual == null) {
            return;
        }
        if ("CATALOGO".equals(origenes.get("bandas")) && actual.bandas() != null && !actual.bandas().isEmpty()) {
            comparar("bandas", clavesDeBandas(actual.bandas()),
                    clavesDeBandas(esperada == null ? null : esperada.bandas()), marcas);
        }
        if ("CATALOGO".equals(origenes.get("escalones")) && actual.escalones() != null
                && !actual.escalones().isEmpty()) {
            comparar("escalones", clavesDeEscalones(actual.escalones()),
                    clavesDeEscalones(esperada == null ? null : esperada.escalones()), marcas);
        }
        if ("CATALOGO".equals(origenes.get("plano")) && actual.plano() != null) {
            List<String> a = List.of(numeroONulo(actual.plano().corteX()), numeroONulo(actual.plano().corteY()));
            InterpretacionDTO.PlanoDTO p = esperada == null ? null : esperada.plano();
            List<String> b = p == null ? List.of() : List.of(numeroONulo(p.corteX()), numeroONulo(p.corteY()));
            comparar("plano", a, b, marcas);
        }
    }

    /** Qué difiere entre dos estructuras: ítems, formato y opciones por ítem, y escalas. Vacío = idénticas. */
    private static List<String> diferenciasDeEstructura(CuestionarioDTO dto, CuestionarioDTO referencia) {
        List<String> diferencias = new ArrayList<>();
        List<PreguntaDTO> a = preguntas(dto);
        List<PreguntaDTO> b = preguntas(referencia);
        if (a.size() != b.size()) {
            diferencias.add("el número de ítems (" + a.size() + " frente a " + b.size() + ")");
        } else {
            List<String> opciones = new ArrayList<>();
            List<String> formatos = new ArrayList<>();
            for (int i = 0; i < a.size(); i++) {
                if (opciones(a.get(i)).size() != opciones(b.get(i)).size()) {
                    opciones.add(String.valueOf(i + 1));
                }
                if (a.get(i).getFormato() != b.get(i).getFormato()) {
                    formatos.add(String.valueOf(i + 1));
                }
            }
            if (!opciones.isEmpty()) {
                diferencias.add("las opciones por ítem (ítems " + lista(opciones) + ")");
            }
            if (!formatos.isEmpty()) {
                diferencias.add("el formato de respuesta (ítems " + lista(formatos) + ")");
            }
        }
        if (!escalas(dto).equals(escalas(referencia))) {
            diferencias.add("las escalas o puntajes derivados");
        }
        return diferencias;
    }

    private static Set<String> escalas(CuestionarioDTO dto) {
        Set<String> escalas = new HashSet<>();
        for (EstiloDTO e : dto.getEstilos() == null ? List.<EstiloDTO>of() : dto.getEstilos()) {
            TipoEstilo tipo = e.getTipo() == null ? TipoEstilo.PRIMARIO : e.getTipo();
            escalas.add(plano(e.getNombre()) + "|" + tipo);
        }
        return escalas;
    }

    /** La clave por posición, contra la del catálogo: los pesos distintos de cero de cada opción, por nombre de estilo. */
    private static void claveContraElCatalogo(CuestionarioDTO dto, CuestionarioDTO referencia, List<Marca> marcas) {
        List<PreguntaDTO> a = preguntas(dto);
        List<PreguntaDTO> b = preguntas(referencia);
        if (a.size() != b.size()) {
            marcas.add(Marca.validador(Marca.ALTO, "clave", List.of(),
                    "La clave se marcó como tomada del catálogo, pero el cuestionario tiene " + a.size()
                            + " ítems y el instrumento de referencia " + b.size() + ": esa clave no aplica.",
                    "Contrasta la clave con el documento; sin una clave impresa ahí, la clave es inferida."));
            return;
        }
        Map<Integer, String> nombresA = nombres(dto);
        Map<Integer, String> nombresB = nombres(referencia);
        List<String> distintos = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            if (!pesos(a.get(i), nombresA).equals(pesos(b.get(i), nombresB))) {
                distintos.add("pregunta:" + a.get(i).getOrden());
            }
        }
        if (!distintos.isEmpty()) {
            marcas.add(Marca.validador(Marca.ALTO, "clave", limitar(distintos, MAX_REFS),
                    "La clave de " + distintos.size() + " de " + a.size() + " ítems se marcó como tomada del "
                            + "catálogo y no coincide con la del instrumento de referencia.",
                    "Compara esos ítems con la clave del documento; una clave copiada con errores puntúa mal a "
                            + "todos los estudiantes."));
        }
    }

    private static Map<Integer, String> nombres(CuestionarioDTO dto) {
        Map<Integer, String> nombres = new LinkedHashMap<>();
        for (EstiloDTO e : dto.getEstilos() == null ? List.<EstiloDTO>of() : dto.getEstilos()) {
            nombres.put(e.getId(), plano(e.getNombre()));
        }
        return nombres;
    }

    private static List<Map<String, String>> pesos(PreguntaDTO p, Map<Integer, String> nombres) {
        List<Map<String, String>> opciones = new ArrayList<>();
        for (OpcionDTO o : opciones(p)) {
            Map<String, String> pesos = new TreeMap<>();
            for (OpcionDTO.PesoDTO w : o.getPesos() == null ? List.<OpcionDTO.PesoDTO>of() : o.getPesos()) {
                if (finito(w.peso()) && w.peso() != 0) {
                    pesos.put(nombres.getOrDefault(w.estiloId(), "#" + w.estiloId()), numero(w.peso()));
                }
            }
            opciones.add(pesos);
        }
        return opciones;
    }

    private static List<String> clavesDeBandas(List<BandaDTO> bandas) {
        List<String> claves = new ArrayList<>();
        for (BandaDTO b : bandas == null ? List.<BandaDTO>of() : bandas) {
            claves.add(String.valueOf(b.estilo() == null ? null : b.estilo().strip()) + "|" + b.escala() + "|"
                    + numeroONulo(b.limiteInferior()) + "|" + numeroONulo(b.limiteSuperior()));
        }
        return claves;
    }

    private static List<String> clavesDeEscalones(List<EscalonDTO> escalones) {
        List<String> claves = new ArrayList<>();
        for (EscalonDTO e : escalones == null ? List.<EscalonDTO>of() : escalones) {
            claves.add(numeroONulo(e.totalMin()) + "|" + numeroONulo(e.totalMax()) + "|" + numeroONulo(e.distancia()));
        }
        return claves;
    }

    /** Compara como multiconjuntos; no se copian al mensaje las cifras del catálogo, solo cuántas difieren. */
    private static void comparar(String seccion, List<String> actual, List<String> esperado, List<Marca> marcas) {
        List<String> sobran = new ArrayList<>(actual);
        List<String> faltan = new ArrayList<>(esperado);
        for (String clave : actual) {
            if (faltan.remove(clave)) {
                sobran.remove(clave);
            }
        }
        if (sobran.isEmpty() && faltan.isEmpty()) {
            return;
        }
        marcas.add(Marca.validador(Marca.ALTO, seccion, List.of(),
                "Las cifras de '" + seccion + "' que la IA dice haber copiado del catálogo no coinciden con las del "
                        + "instrumento de referencia: " + sobran.size() + " distintas o de más y " + faltan.size()
                        + " que faltan.",
                "Si el documento imprime sus propios valores, esos mandan; si no, cópialos tal cual del catálogo "
                        + "o deja la sección vacía y elige una lectura del asistente."));
    }

    // ---------------------------------------------------------------- Páginas que no llegaron

    /**
     * Las escaneadas que pasaron el tope de imágenes no se mandaron en ninguna forma: Nelse no
     * sabe que existen y puede entregar el cuestionario sin sus ítems. Solo la persona puede
     * notarlo, así que se le dice. Aplica también si el modelo declaró CREACION.
     */
    private static void paginasOmitidas(FuenteNelse fuente, List<Marca> marcas) {
        int omitidas = fuente.paginasOmitidas();
        if (omitidas <= 0) {
            return;
        }
        marcas.add(Marca.validador(Marca.MEDIO, "items", List.of(),
                omitidas + (omitidas == 1 ? " página escaneada no se envió" : " páginas escaneadas no se enviaron")
                        + " a Nelse (tope de " + fuente.paginasImagen()
                        + (fuente.paginasImagen() == 1 ? " imagen" : " imágenes") + "): revisa que no falten ítems.",
                "Compara el cuestionario con el original; si faltan ítems, agrégalos en el editor o sube el "
                        + "documento por partes."));
    }

    // ---------------------------------------------------------------- Fidelidad al documento

    private static void fidelidad(CuestionarioDTO dto, FuenteNelse fuente, List<Marca> marcas) {
        if (fuente.paginasImagen() > 0) {
            // Lo que viene de una imagen no está en el texto: no se puede decir que "falta". Se avisa una vez.
            marcas.add(Marca.validador(Marca.MEDIO, "items", List.of(),
                    "Parte del documento (" + fuente.paginasImagen() + (fuente.paginasImagen() == 1 ? " página" : " páginas")
                            + ") se leyó desde imagen: los enunciados y opciones de esas páginas no se pueden "
                            + "comparar con el texto.",
                    "Verifica los enunciados y las opciones contra el original, página por página."));
            return;
        }
        if (!fuente.hayDocumento()) {
            return;
        }
        String documento = " " + palabras(sinMarcadores(fuente.documento())) + " ";
        List<PreguntaDTO> preguntas = preguntas(dto);

        List<Integer> sinEnunciado = new ArrayList<>();
        int conEnunciado = 0;
        for (PreguntaDTO p : preguntas) {
            String clave = palabras(sinNumeracion(p.getPregunta()));
            if (clave.isEmpty()) {
                continue;
            }
            conEnunciado++;
            if (!documento.contains(" " + clave + " ")) {
                sinEnunciado.add(p.getOrden());
            }
        }
        boolean traducido = conEnunciado > 0 && sinEnunciado.size() > UMBRAL_TRADUCCION * conEnunciado;
        if (traducido) {
            // Una sola marca: si casi ninguno coincide, listar ítem por ítem solo es ruido, y las
            // opciones tampoco coincidirán por la misma causa.
            marcas.add(Marca.validador(Marca.MEDIO, "items", List.of(),
                    "Los enunciados no coinciden con el documento (¿traducidos o reescritos?): "
                            + sinEnunciado.size() + " de " + conEnunciado + " no aparecen tal cual.",
                    "Compara varios ítems con el original. Si la IA tradujo o reformuló, pídele que los copie "
                            + "sin cambios."));
        } else if (!sinEnunciado.isEmpty()) {
            marcas.add(Marca.validador(Marca.MEDIO, "items",
                    limitar(sinEnunciado.stream().map(n -> "pregunta:" + n).toList(), MAX_REFS),
                    "El texto de " + sinEnunciado.size() + (sinEnunciado.size() == 1 ? " pregunta" : " preguntas")
                            + " no aparece tal cual en el documento: " + lista(sinEnunciado) + ".",
                    "Compara cada enunciado con el original; la IA debía copiarlos sin reescribir, solo "
                            + "arreglando artefactos de la extracción."));
        }

        if (!traducido) {
            opciones(preguntas, documento, marcas);
        }
        numerados(fuente.documento(), preguntas, marcas);
    }

    /** Cada texto de opción distinto, una sola vez: las etiquetas de una escala se repiten en todos los ítems. */
    private static void opciones(List<PreguntaDTO> preguntas, String documento, List<Marca> marcas) {
        Map<String, Integer> primeraPregunta = new LinkedHashMap<>();
        for (PreguntaDTO p : preguntas) {
            for (OpcionDTO o : opciones(p)) {
                String clave = palabras(sinNumeracion(o.getRespuesta()));
                if (!clave.isEmpty()) {
                    primeraPregunta.putIfAbsent(clave, p.getOrden());
                }
            }
        }
        List<Integer> ausentes = new ArrayList<>();
        primeraPregunta.forEach((clave, orden) -> {
            if (!documento.contains(" " + clave + " ")) {
                ausentes.add(orden);
            }
        });
        if (ausentes.isEmpty()) {
            return;
        }
        if (ausentes.size() > UMBRAL_TRADUCCION * primeraPregunta.size()) {
            marcas.add(Marca.validador(Marca.MEDIO, "items", List.of(),
                    "Las opciones de respuesta no coinciden con el documento (¿traducidas o reescritas?): "
                            + ausentes.size() + " de " + primeraPregunta.size() + " textos distintos no aparecen tal cual.",
                    "Compara las etiquetas de la escala con las del original."));
            return;
        }
        List<Integer> unicas = ausentes.stream().distinct().toList();
        marcas.add(Marca.validador(Marca.MEDIO, "items",
                limitar(unicas.stream().map(n -> "pregunta:" + n).toList(), MAX_REFS),
                ausentes.size() + (ausentes.size() == 1 ? " texto de opción" : " textos de opción")
                        + " no aparecen tal cual en el documento (primera aparición en las preguntas "
                        + lista(unicas) + ").",
                "Compara esas opciones con el original; no deben reescribirse."));
    }

    /**
     * El documento numera sus ítems y el cuestionario tiene otra cantidad. Se cuentan las
     * corridas 1, 2, 3… de líneas que empiezan con "N." "N)" o "N-" y una letra. Una leyenda
     * de escala vertical ("1. Nunca", "2. A veces"…) también numera, así que una corrida
     * solo cuenta si es más larga que las opciones de cualquier ítem y de al menos
     * {@value #MIN_NUMERADOS}. Si la numeración se reinicia por parte, vale también la suma.
     */
    private static void numerados(String documento, List<PreguntaDTO> preguntas, List<Marca> marcas) {
        int masOpciones = preguntas.stream().mapToInt(p -> opciones(p).size()).max().orElse(0);
        int minimo = Math.max(MIN_NUMERADOS, masOpciones + 1);
        List<Integer> corridas = new ArrayList<>();
        int actual = 0; // largo de la corrida que empezó en 1 y sigue; 0 = no hay
        Matcher m = LINEA_NUMERADA.matcher(sinMarcadores(documento));
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (actual > 0 && n == actual + 1) {
                actual++;
            } else {
                cerrar(corridas, actual, minimo);
                actual = n == 1 ? 1 : 0;
            }
        }
        cerrar(corridas, actual, minimo);
        if (corridas.isEmpty()) {
            return;
        }
        int mayor = corridas.stream().mapToInt(Integer::intValue).max().getAsInt();
        int suma = corridas.stream().mapToInt(Integer::intValue).sum();
        if (preguntas.size() == mayor || preguntas.size() == suma) {
            return;
        }
        marcas.add(Marca.validador(Marca.MEDIO, "items", List.of(),
                "El documento numera " + mayor + (suma != mayor ? " ítems seguidos (" + suma + " si cada parte "
                        + "reinicia la numeración)" : " ítems") + " y el cuestionario tiene " + preguntas.size()
                        + " preguntas.",
                "Si la IA omitió ítems a propósito (abiertos, condicionales o demográficos) deben estar en "
                        + "'noSoportado'; si no, faltan o sobran ítems: compara con el documento."));
    }

    private static void cerrar(List<Integer> corridas, int largo, int minimo) {
        if (largo >= minimo) {
            corridas.add(largo);
        }
    }

    // ---------------------------------------------------------------- Utilidades

    /** origen por sección en mayúsculas, con la clave normalizada como en el validador. */
    private static Map<String, String> origenes(AnalisisIa analisis) {
        Map<String, String> origenes = new LinkedHashMap<>();
        if (analisis != null && analisis.origen() != null) {
            analisis.origen().forEach((seccion, origen) -> {
                if (seccion != null && origen != null) {
                    origenes.put(seccion.strip().toLowerCase(Locale.ROOT), origen.strip().toUpperCase(Locale.ROOT));
                }
            });
        }
        return origenes;
    }

    /** Los números de uno o más textos, escritos igual: "3,5" = "3.5", "4" = "4.0", sin signo. */
    private static Set<String> numeros(String... textos) {
        Set<String> numeros = new HashSet<>();
        for (String texto : textos) {
            if (texto == null) {
                continue;
            }
            Matcher m = NUMERO.matcher(texto);
            while (m.find()) {
                numeros.add(numero(new BigDecimal(m.group().replace(',', '.'))));
            }
        }
        return numeros;
    }

    private static String numero(double d) {
        return numero(BigDecimal.valueOf(d));
    }

    private static String numero(BigDecimal d) {
        return d.abs().stripTrailingZeros().toPlainString();
    }

    private static String numeroONulo(Double d) {
        return finito(d) ? numero(d) : "null";
    }

    private static String sinMarcadores(String documento) {
        return documento == null ? "" : MARCADOR_PAGINA.matcher(documento).replaceAll("");
    }

    private static String sinNumeracion(String texto) {
        return texto == null ? "" : NUMERACION_INICIAL.matcher(texto).replaceFirst("");
    }

    /** Minúsculas, NFKC y solo letras y dígitos separados por un espacio: compara palabras, no formato. */
    private static String plano(String texto) {
        if (texto == null) {
            return "";
        }
        String t = Normalizer.normalize(texto, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return NO_ALFANUMERICO.matcher(t).replaceAll(" ").strip();
    }

    /**
     * {@link #plano} sin los tokens que son solo dígitos, para comparar enunciados y opciones.
     * En una tabla de PDF la escala "1 2 3 4 5" queda entre las dos mitades de un enunciado que
     * se parte en dos renglones; esos números no son del texto y romperían la coincidencia. Se
     * quitan en el documento y en el texto del modelo por igual. Solo para fidelidad: la
     * comprobación de cifras sí necesita los dígitos.
     */
    private static String palabras(String texto) {
        return SOLO_DIGITOS.matcher(plano(texto)).replaceAll("").replaceAll(" {2,}", " ").strip();
    }

    private static List<PreguntaDTO> preguntas(CuestionarioDTO dto) {
        return dto.getPreguntas() == null ? List.of() : dto.getPreguntas();
    }

    private static List<OpcionDTO> opciones(PreguntaDTO p) {
        return p.getOpciones() == null ? List.of() : p.getOpciones();
    }

    /** "a, b, c y N más", sin pasar de {@value #MAX_LISTA} elementos. */
    private static String lista(Collection<?> elementos) {
        List<String> textos = elementos.stream().map(String::valueOf).toList();
        if (textos.size() <= MAX_LISTA) {
            return String.join(", ", textos);
        }
        return String.join(", ", textos.subList(0, MAX_LISTA)) + " y " + (textos.size() - MAX_LISTA) + " más";
    }

    private static List<String> limitar(Collection<String> refs, int max) {
        return refs.stream().limit(max).toList();
    }
}
