package com.adela.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Escritura de CSV en dos variantes. EXCEL usa ';' y coma decimal con BOM, que
 * es lo que Excel en español abre sin el asistente de importación; RFC4180 usa
 * ',' y punto decimal para R, Python o SPSS.
 */
final class Csv {

    enum Formato {
        EXCEL, RFC4180
    }

    private final Formato formato;
    private final char separador;
    private final StringBuilder sb = new StringBuilder();

    Csv(Formato formato) {
        this.formato = formato;
        this.separador = formato == Formato.EXCEL ? ';' : ',';
        if (formato == Formato.EXCEL) {
            sb.append('﻿');
        }
    }

    Csv fila(List<String> campos) {
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                sb.append(separador);
            }
            sb.append(escapar(campos.get(i)));
        }
        sb.append("\r\n");
        return this;
    }

    /** Hasta 4 decimales, sin ceros sobrantes; vacío si no hay valor. */
    String numero(Double v) {
        if (v == null) {
            return "";
        }
        String s = BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return formato == Formato.EXCEL ? s.replace('.', ',') : s;
    }

    /**
     * Texto libre (nombres, etiquetas). Un valor que empieza por = + - @ lo
     * ejecutaría Excel como fórmula; el apóstrofo lo deja como texto.
     */
    static String texto(String v) {
        if (v == null) {
            return "";
        }
        return !v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 ? "'" + v : v;
    }

    private String escapar(String campo) {
        if (campo.indexOf(separador) >= 0 || campo.indexOf('"') >= 0 || campo.indexOf('\n') >= 0
                || campo.indexOf('\r') >= 0) {
            return '"' + campo.replace("\"", "\"\"") + '"';
        }
        return campo;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
