package com.adela.ia;

import java.util.List;

/**
 * Punto a revisar de un cuestionario importado. No bloquea: la persona lo
 * verifica en el editor antes de guardar.
 *
 * origen: MODELO (lo dijo la IA) o VALIDADOR (lo detectó ADELA). riesgo: ALTO,
 * MEDIO o BAJO. seccion: clave, items, estilos, compuestos, bandas, escalones,
 * plano, esquema, complementaria o meta. refs: "pregunta:12" (12 = el orden del
 * ítem) o "estilo:Activo".
 */
public record Marca(String origen, String riesgo, String seccion, List<String> refs, String mensaje,
        String comoVerificar) {

    public static final String MODELO = "MODELO";
    public static final String VALIDADOR = "VALIDADOR";
    public static final String ALTO = "ALTO";
    public static final String MEDIO = "MEDIO";
    public static final String BAJO = "BAJO";

    static Marca validador(String riesgo, String seccion, List<String> refs, String mensaje,
            String comoVerificar) {
        return new Marca(VALIDADOR, riesgo, seccion, refs, mensaje, comoVerificar);
    }
}
