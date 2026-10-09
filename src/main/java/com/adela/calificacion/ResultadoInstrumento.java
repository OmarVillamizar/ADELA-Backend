package com.adela.calificacion;

import java.util.List;

/**
 * perfilEtiqueta: dominantes unidos por " + "; perfilTipo: UNIMODAL o
 * MULTIMODAL, CUADRANTE, o SIMPLE, DOBLE, TRIPLE, CUADRUPLE o MULTIPLE con
 * NIVEL_SUPERIOR. Null sin dominantes. Con NIVEL_SUPERIOR, si ningún estilo
 * queda en su nivel más alto ni en el más bajo: "Dominancia media" y MEDIA.
 * perfilCodigo: solo con NIVEL_SUPERIOR, el nivel de cada primario contado
 * desde arriba, en orden (1-2-3-3 = el primero en su nivel más alto).
 */
public record ResultadoInstrumento(long cuestionarioId, String versionMotor, List<ResultadoEstilo> estilos,
        String perfilEtiqueta, String perfilTipo, String perfilCodigo) {

    public ResultadoInstrumento(long cuestionarioId, String versionMotor, List<ResultadoEstilo> estilos,
            String perfilEtiqueta, String perfilTipo) {
        this(cuestionarioId, versionMotor, estilos, perfilEtiqueta, perfilTipo, null);
    }

    /** Solo primarios: un compuesto (AC-CE, polos del ILS) tiene otro rango por construcción. */
    public boolean rangosHomogeneos() {
        return RangoTeorico.homogeneos(estilos.stream().filter(e -> e.tipo() == TipoEstilo.PRIMARIO)
                .map(e -> new RangoTeorico.Rango(e.rangoMin(), e.rangoMax())).toList());
    }
}
