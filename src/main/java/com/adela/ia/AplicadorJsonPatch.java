package com.adela.ia;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Aplica el JSON Patch (RFC 6902) con que Nelse corrige o refina su propio resultado,
 * restringido a lo que el prompt de corrección permite: {@code add}, {@code replace} y
 * {@code remove}. Sin dependencia nueva: son tres operaciones y la validez de cada una
 * es lo que importa, no la generalidad.
 *
 * Estricto a propósito: el patch lo escribe un modelo y una operación que no encaja casi
 * siempre es un patch equivocado, no uno que haya que adivinar. Cualquier cosa fuera de
 * lo esperado lanza {@link PatchInvalidoException} y el orquestador regenera.
 *
 * Reglas:
 * - El patch es el arreglo de operaciones o el objeto {@code {"patch":[...]}} (la forma que
 *   pide 04_correccion.md); se aceptan las dos. El arreglo vacío no cambia nada.
 * - Se aplican en orden y cada una ve el resultado de la anterior: por eso quitar varios
 *   elementos de un arreglo exige ir del índice mayor al menor.
 * - La raíz ("" como ruta) no se puede añadir, reemplazar ni quitar: el resultado siempre
 *   es el mismo documento corregido, nunca otro.
 * - {@code add}: sobre un objeto crea o reemplaza el miembro; sobre un arreglo inserta en el
 *   índice (0 a tamaño; el tamaño equivale a añadir al final) y {@code -} añade al final.
 *   {@code replace} y {@code remove} exigen que el destino exista.
 * - Los índices son decimales sin ceros a la izquierda ni signo, como pide la RFC.
 * - Devuelve un árbol nuevo; ni la raíz ni el patch se modifican.
 */
public final class AplicadorJsonPatch {

    private static final int MAX_RUTA_EN_MENSAJE = 60;

    private AplicadorJsonPatch() {
    }

    public static JsonNode aplicar(JsonNode raiz, JsonNode patch) {
        if (raiz == null) {
            throw new PatchInvalidoException("No hay documento al que aplicar el patch.");
        }
        JsonNode operaciones = patch != null && patch.isObject() && patch.has("patch") ? patch.get("patch") : patch;
        if (operaciones == null || !operaciones.isArray()) {
            throw new PatchInvalidoException("El patch debe ser un arreglo de operaciones o {\"patch\": [...]}.");
        }

        JsonNode resultado = raiz.deepCopy();
        int n = 0;
        for (JsonNode operacion : operaciones) {
            n++;
            aplicarUna(resultado, operacion, n);
        }
        return resultado;
    }

    private static void aplicarUna(JsonNode raiz, JsonNode operacion, int n) {
        if (operacion == null || !operacion.isObject()) {
            throw invalido(n, "la operación no es un objeto");
        }
        String op = texto(operacion, "op");
        String path = texto(operacion, "path");
        if (op == null) {
            throw invalido(n, "falta 'op'");
        }
        if (!op.equals("add") && !op.equals("replace") && !op.equals("remove")) {
            throw invalido(n, "operación no permitida (solo add, replace y remove)");
        }
        if (path == null) {
            throw invalido(n, "falta 'path'");
        }
        boolean conValor = !op.equals("remove");
        if (conValor && !operacion.has("value")) {
            throw invalido(n, "falta 'value' en " + op + " " + resumir(path));
        }
        if (path.isEmpty()) {
            throw invalido(n, "no se puede modificar la raíz del documento");
        }

        JsonPointer puntero;
        try {
            puntero = JsonPointer.compile(path);
        } catch (IllegalArgumentException e) {
            throw invalido(n, "la ruta " + resumir(path) + " no es un puntero JSON válido");
        }

        // Se camina hasta el contenedor del destino; el último tramo es el miembro o el índice.
        JsonNode padre = raiz;
        JsonPointer resto = puntero;
        while (resto.tail() != null && !resto.tail().matches()) {
            padre = hijo(padre, resto.getMatchingProperty());
            if (padre == null) {
                throw invalido(n, "la ruta " + resumir(path) + " no existe");
            }
            resto = resto.tail();
        }
        String ultimo = resto.getMatchingProperty();

        if (padre.isObject()) {
            objeto((ObjectNode) padre, op, ultimo, operacion, n, path);
        } else if (padre.isArray()) {
            arreglo((ArrayNode) padre, op, ultimo, operacion, n, path);
        } else {
            throw invalido(n, "la ruta " + resumir(path) + " no existe: el elemento padre no es un objeto ni un arreglo");
        }
    }

    private static void objeto(ObjectNode padre, String op, String miembro, JsonNode operacion, int n, String path) {
        switch (op) {
        case "add" -> padre.set(miembro, operacion.get("value").deepCopy());
        case "replace" -> {
            existe(padre.has(miembro), n, path);
            padre.set(miembro, operacion.get("value").deepCopy());
        }
        default -> {
            existe(padre.has(miembro), n, path);
            padre.remove(miembro);
        }
        }
    }

    private static void arreglo(ArrayNode padre, String op, String segmento, JsonNode operacion, int n, String path) {
        if (op.equals("add") && segmento.equals("-")) {
            padre.add(operacion.get("value").deepCopy());
            return;
        }
        int indice = indice(segmento);
        if (indice < 0) {
            throw invalido(n, "el índice de " + resumir(path) + " no es un número válido");
        }
        switch (op) {
        case "add" -> {
            if (indice > padre.size()) {
                throw invalido(n, "el índice de " + resumir(path) + " está fuera de rango (el arreglo tiene "
                        + padre.size() + " elementos)");
            }
            padre.insert(indice, operacion.get("value").deepCopy());
        }
        case "replace" -> {
            existe(indice < padre.size(), n, path);
            padre.set(indice, operacion.get("value").deepCopy());
        }
        default -> {
            existe(indice < padre.size(), n, path);
            padre.remove(indice);
        }
        }
    }

    /** Hijo por nombre o índice; null si no existe o el nodo no es un contenedor. */
    private static JsonNode hijo(JsonNode nodo, String segmento) {
        if (nodo.isObject()) {
            return nodo.get(segmento);
        }
        if (nodo.isArray()) {
            int indice = indice(segmento);
            return indice >= 0 && indice < nodo.size() ? nodo.get(indice) : null;
        }
        return null;
    }

    /** Índice decimal estricto de la RFC 6902 ("0" o sin ceros a la izquierda); -1 si no lo es. */
    private static int indice(String segmento) {
        if (segmento.isEmpty() || segmento.length() > 9 || (segmento.length() > 1 && segmento.charAt(0) == '0')) {
            return -1;
        }
        for (int i = 0; i < segmento.length(); i++) {
            if (segmento.charAt(i) < '0' || segmento.charAt(i) > '9') {
                return -1;
            }
        }
        return Integer.parseInt(segmento);
    }

    private static void existe(boolean existe, int n, String path) {
        if (!existe) {
            throw invalido(n, "la ruta " + resumir(path) + " no existe");
        }
    }

    private static String texto(JsonNode operacion, String campo) {
        JsonNode valor = operacion.get(campo);
        return valor != null && valor.isTextual() ? valor.asText() : null;
    }

    /** La ruta la escribió el modelo: se acorta para que el mensaje no crezca. */
    private static String resumir(String path) {
        String corta = path.length() > MAX_RUTA_EN_MENSAJE ? path.substring(0, MAX_RUTA_EN_MENSAJE) + "…" : path;
        return "'" + corta.replaceAll("[\\p{Cntrl}]", " ") + "'";
    }

    private static PatchInvalidoException invalido(int n, String motivo) {
        return new PatchInvalidoException("Operación " + n + " del patch: " + motivo + ".");
    }
}
