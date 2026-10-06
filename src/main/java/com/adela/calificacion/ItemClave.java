package com.adela.calificacion;

import java.util.List;

/**
 * Ítem (pregunta) de la clave. maxSelecciones null significa "todas las
 * opciones"; puntosRepartir solo aplica a REPARTO.
 */
public record ItemClave(long itemId, FormatoItem formato, boolean obligatorio, int minSelecciones,
        Integer maxSelecciones, Integer puntosRepartir, List<OpcionClave> opciones) {

    public ItemClave {
        opciones = List.copyOf(opciones);
        if (opciones.isEmpty())
            throw new ClaveInconsistenteException("Ítem " + itemId + " sin opciones");
        if (formato == FormatoItem.REPARTO && (puntosRepartir == null || puntosRepartir <= 0))
            throw new ClaveInconsistenteException("Ítem " + itemId + ": REPARTO requiere puntosRepartir > 0");
        // En el constructor compacto los campos aún no están asignados: usar los parámetros.
        if (minSel(formato, obligatorio, minSelecciones) > maxSel(formato, maxSelecciones, opciones.size()))
            throw new ClaveInconsistenteException("Ítem " + itemId + ": minSelecciones > maxSelecciones");
    }

    /** Mínimo de opciones marcadas cuando el ítem se responde. */
    public int minSelEfectivo() {
        return minSel(formato, obligatorio, minSelecciones);
    }

    public int maxSelEfectivo() {
        return maxSel(formato, maxSelecciones, opciones.size());
    }

    private static int minSel(FormatoItem formato, boolean obligatorio, int minSelecciones) {
        return switch (formato) {
            case UNICA -> 1;
            case MULTIPLE -> Math.max(minSelecciones, obligatorio ? 1 : 0);
            case JERARQUIA, REPARTO -> 0;
        };
    }

    private static int maxSel(FormatoItem formato, Integer maxSelecciones, int k) {
        return switch (formato) {
            case UNICA -> 1;
            case MULTIPLE -> maxSelecciones == null ? k : Math.min(maxSelecciones, k);
            case JERARQUIA, REPARTO -> k;
        };
    }
}
