package com.adela.ia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Limpieza determinista del texto de un PDF antes de mandarlo al modelo: quita lo que
 * no es contenido (encabezados y pies repetidos, números de página, URL, copyright,
 * caracteres invisibles) para gastar menos tokens, y arregla palabras partidas por el
 * fin de línea.
 *
 * Es pura y sin estado. La regla que manda es la conservadora: el texto limpio es
 * también contra lo que se comprueba que cada cifra del resultado del modelo exista,
 * así que **nunca se toca un dígito de una línea de contenido** (numeración de ítems,
 * escalas "1 2 3 4 5", opciones). Ante la duda, la línea se queda: sobra algo de
 * ruido, no falta contenido.
 *
 * Trabaja por página y conserva su posición: la fase de visión mezcla texto e imágenes
 * por página. {@link #unir} arma el texto único con marcadores.
 */
final class LimpiadorTexto {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Un encabezado o pie está en las primeras o últimas líneas de la página, no en medio. */
    private static final int BORDE = 3;
    /** Más largo que esto es un párrafo, no un encabezado. */
    private static final int LARGO_MAX_ENCABEZADO = 80;
    /** Más corto que esto ("Sí", "Nunca", "Siempre") es casi seguro una opción de respuesta. */
    private static final int LARGO_MIN_ENCABEZADO = 10;

    // Espacios exóticos (NBSP, finos, ideográfico…) que PDFBox deja pasar tal cual.
    private static final Pattern ESPACIOS = Pattern.compile("[ \\t\\u000B\\f\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]+");
    // Cero ancho, marca de orden de bytes y guion blando: invisibles que rompen coincidencias.
    private static final Pattern INVISIBLES = Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF\\u00AD]");
    // El guion blando a fin de línea ES el corte de palabra: se une antes de borrar invisibles.
    private static final Pattern GUION_BLANDO_FIN = Pattern.compile("(\\p{L})\\u00AD\\n(\\p{Ll})");
    private static final Pattern GUION_FIN_DE_LINEA =Pattern.compile("\\p{L}{2}-$");

    private static final Pattern URL = Pattern.compile("\\b(?:https?://|www\\.)\\S+", FLAGS);
    private static final Pattern COPYRIGHT = Pattern.compile(
            "©|^\\(c\\)\\s*(?:19|20)\\d{2}|^copyright\\b|derechos\\s+reservados|all\\s+rights\\s+reserved", FLAGS);

    /** "Página 3", "Pág. 3 de 10", "Page 3 of 10": inequívocos, se quitan en cualquiera de las líneas de borde. */
    private static final Pattern PAGINA_EXPLICITA = Pattern.compile(
            "^(?:p[áa]gina|p[áa]g\\.?|page|p\\.)\\s*\\d{1,3}(?:\\s*(?:de|of|/)\\s*\\d{1,3})?$", FLAGS);
    /**
     * "3", "- 3 -", "3/10": ambiguos (una escala o un ítem también son números sueltos),
     * se quitan solo si son la primera o la última línea con texto de la página.
     */
    private static final Pattern NUMERO_SUELTO = Pattern.compile("^(?:\\d{1,3}|[-–—]\\s*\\d{1,3}\\s*[-–—]|\\d{1,3}\\s*/\\s*\\d{1,3})$");

    private LimpiadorTexto() {
    }

    /** Un texto limpio por página, en el mismo orden (una página sin nada útil queda en ""). */
    static List<String> limpiar(List<String> textos) {
        List<List<String>> paginas = new ArrayList<>(textos.size());
        for (String texto : textos) {
            paginas.add(lineas(texto));
        }
        quitarRepetidas(paginas);

        List<String> limpias = new ArrayList<>(paginas.size());
        for (List<String> lineas : paginas) {
            quitarNumeroDePagina(lineas);
            unirPalabrasPartidas(lineas);
            limpias.add(compactar(lineas));
        }
        return limpias;
    }

    /**
     * Texto único con un marcador "--- página N ---" por página (N = posición en la lista,
     * desde 1). Las páginas vacías se omiten: no aportan y el marcador solo gastaría tokens.
     */
    static String unir(List<String> limpias) {
        return unir(limpias, Set.of());
    }

    /**
     * Igual, pero una página que viaja como imagen conserva su marcador "--- página N (imagen
     * adjunta) ---" con el cuerpo vacío: así el modelo sabe en qué posición del documento va
     * cada imagen, que se adjuntan aparte y en orden. paginasImagen son números de página, desde 1.
     */
    static String unir(List<String> limpias, Collection<Integer> paginasImagen) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limpias.size(); i++) {
            boolean imagen = paginasImagen.contains(i + 1);
            if (limpias.get(i).isBlank() && !imagen) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("--- página ").append(i + 1).append(imagen ? " (imagen adjunta) ---" : " ---");
            if (!imagen) {
                sb.append('\n').append(limpias.get(i));
            }
        }
        return sb.toString();
    }

    // --- paso 1: caracteres, URL y copyright, línea a línea ---

    private static List<String> lineas(String texto) {
        String t = texto == null ? "" : texto.replace("\r\n", "\n").replace('\r', '\n');
        t = GUION_BLANDO_FIN.matcher(t).replaceAll("$1$2");
        t = INVISIBLES.matcher(t).replaceAll("");
        t = ESPACIOS.matcher(t).replaceAll(" ");

        List<String> lineas = new ArrayList<>();
        for (String cruda : t.split("\n", -1)) {
            String linea = cruda.strip();
            if (COPYRIGHT.matcher(linea).find()) {
                continue;
            }
            if (URL.matcher(linea).find()) {
                linea = ESPACIOS.matcher(URL.matcher(linea).replaceAll("")).replaceAll(" ").strip();
                if (!linea.chars().anyMatch(Character::isLetterOrDigit)) {
                    continue; // la línea era solo la URL (y su puntuación)
                }
            }
            lineas.add(linea);
        }
        return lineas;
    }

    // --- paso 2: encabezados y pies repetidos entre páginas ---

    /**
     * Quita las líneas de borde que se repiten en muchas páginas. Umbral: al menos la mitad
     * de las páginas con texto y nunca menos de 3 (con solo 2 páginas, las dos). Se compara
     * con los dígitos normalizados, para que "Página 3 de 10" y "Página 4 de 10" sean la misma.
     *
     * Una línea NO se considera encabezado si:
     * - tiene menos de {@value #LARGO_MIN_ENCABEZADO} caracteres o más de {@value #LARGO_MAX_ENCABEZADO}
     *   (las opciones cortas "Sí", "Nunca" se repiten en cada ítem);
     * - aparece alguna vez fuera de los bordes o dos veces en una página: es contenido que
     *   se repite (por ejemplo la última opción de cada ítem listado en vertical).
     *
     * Un encabezado idéntico se conserva en su primera aparición: una leyenda de escala
     * repetida arriba de cada hoja es contenido que el modelo debe ver al menos una vez, y
     * un título de una sola vez cuesta unos pocos tokens. Si lo que cambia son los dígitos
     * (contadores de página) no hay nada que conservar y se quitan todos.
     */
    private static void quitarRepetidas(List<List<String>> paginas) {
        int conTexto = 0;
        for (List<String> lineas : paginas) {
            if (lineas.stream().anyMatch(l -> !l.isEmpty())) {
                conTexto++;
            }
        }
        if (conTexto < 2) {
            return;
        }
        int requeridas = conTexto == 2 ? 2 : Math.max(3, (int) Math.ceil(conTexto / 2.0));

        Map<String, Integer> paginasEnBorde = new HashMap<>();
        Map<String, Set<String>> variantes = new HashMap<>();
        Set<String> contenido = new HashSet<>();
        for (List<String> lineas : paginas) {
            Set<Integer> borde = indicesDeBorde(lineas);
            Set<String> enBordeAqui = new HashSet<>();
            Set<String> vistasAqui = new HashSet<>();
            for (int i = 0; i < lineas.size(); i++) {
                String linea = lineas.get(i);
                if (!candidata(linea)) {
                    continue;
                }
                String clave = clave(linea);
                variantes.computeIfAbsent(clave, k -> new HashSet<>()).add(linea);
                if (!vistasAqui.add(clave) || !borde.contains(i)) {
                    contenido.add(clave);
                } else {
                    enBordeAqui.add(clave);
                }
            }
            for (String clave : enBordeAqui) {
                paginasEnBorde.merge(clave, 1, Integer::sum);
            }
        }

        Set<String> yaConservadas = new HashSet<>();
        for (List<String> lineas : paginas) {
            Set<Integer> borde = indicesDeBorde(lineas);
            List<Integer> aQuitar = new ArrayList<>();
            for (int i : borde) {
                String linea = lineas.get(i);
                if (!candidata(linea)) {
                    continue;
                }
                String clave = clave(linea);
                if (contenido.contains(clave) || paginasEnBorde.getOrDefault(clave, 0) < requeridas) {
                    continue;
                }
                boolean contador = variantes.get(clave).size() > 1;
                if (contador || !yaConservadas.add(clave)) {
                    aQuitar.add(i);
                }
            }
            quitar(lineas, aQuitar);
        }
    }

    private static boolean candidata(String linea) {
        return linea.length() >= LARGO_MIN_ENCABEZADO && linea.length() <= LARGO_MAX_ENCABEZADO
                && linea.chars().anyMatch(Character::isLetter);
    }

    private static String clave(String linea) {
        return linea.toLowerCase(Locale.ROOT).replaceAll("\\d+", "#");
    }

    // --- paso 3: números de página ---

    /**
     * Quita el número de página. "Página N…" se quita en cualquiera de las líneas de borde.
     * Un número a secas ("3", "- 3 -", "3/10") solo si es la primera o la última línea con
     * texto: en el medio de la página es un ítem, un valor de escala o una opción, y la
     * escala "1 2 3 4 5" nunca coincide con el patrón (son varios números en la línea).
     * Riesgo asumido: un "5" suelto que cierre la página se pierde.
     */
    private static void quitarNumeroDePagina(List<String> lineas) {
        List<Integer> aQuitar = new ArrayList<>();
        for (int i : indicesDeBorde(lineas)) {
            if (PAGINA_EXPLICITA.matcher(lineas.get(i)).matches()) {
                aQuitar.add(i);
            }
        }
        quitar(lineas, aQuitar);

        List<Integer> noVacias = indicesNoVacios(lineas);
        if (noVacias.isEmpty()) {
            return;
        }
        List<Integer> extremos = new ArrayList<>(List.of(noVacias.get(0)));
        if (noVacias.size() > 1) {
            extremos.add(noVacias.get(noVacias.size() - 1));
        }
        aQuitar = new ArrayList<>();
        for (int i : extremos) {
            if (NUMERO_SUELTO.matcher(lineas.get(i)).matches()) {
                aQuitar.add(i);
            }
        }
        quitar(lineas, aQuitar);
    }

    // --- paso 4: palabras partidas con guion a fin de línea ---

    /**
     * "pala-" + "bra" → "palabra". Solo si el guion sigue a dos letras y la línea siguiente
     * (sin hueco en medio) empieza en minúscula; así "socio-afectivo" a media línea, un
     * guion antes de un número o una enumeración "a-" no se tocan. A cambio, un compuesto
     * legítimo que justo se parta al final de la línea queda unido: no hay forma de saberlo.
     */
    private static void unirPalabrasPartidas(List<String> lineas) {
        for (int i = 0; i < lineas.size() - 1;) {
            String actual = lineas.get(i);
            String siguiente = lineas.get(i + 1);
            if (GUION_FIN_DE_LINEA.matcher(actual).find() && !siguiente.isEmpty()
                    && Character.isLowerCase(siguiente.codePointAt(0))) {
                lineas.set(i, actual.substring(0, actual.length() - 1) + siguiente);
                lineas.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    // --- paso 5: armar la página ---

    /** Une las líneas; cualquier racha de líneas en blanco queda en una sola y se recortan los extremos. */
    private static String compactar(List<String> lineas) {
        StringBuilder sb = new StringBuilder();
        boolean pendienteBlanco = false;
        for (String linea : lineas) {
            if (linea.isEmpty()) {
                pendienteBlanco = sb.length() > 0;
                continue;
            }
            if (sb.length() > 0) {
                sb.append(pendienteBlanco ? "\n\n" : "\n");
            }
            sb.append(linea);
            pendienteBlanco = false;
        }
        return sb.toString();
    }

    // --- utilidades ---

    private static List<Integer> indicesNoVacios(List<String> lineas) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            if (!lineas.get(i).isEmpty()) {
                indices.add(i);
            }
        }
        return indices;
    }

    /** Índices de las primeras y últimas {@link #BORDE} líneas con texto. */
    private static Set<Integer> indicesDeBorde(List<String> lineas) {
        List<Integer> noVacias = indicesNoVacios(lineas);
        Set<Integer> borde = new HashSet<>();
        for (int k = 0; k < noVacias.size(); k++) {
            if (k < BORDE || k >= noVacias.size() - BORDE) {
                borde.add(noVacias.get(k));
            }
        }
        return borde;
    }

    private static void quitar(List<String> lineas, List<Integer> indices) {
        indices.stream().distinct().sorted((a, b) -> b - a).forEach(i -> lineas.remove((int) i));
    }
}
