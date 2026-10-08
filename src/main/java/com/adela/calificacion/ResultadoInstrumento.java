package com.adela.calificacion;

import java.util.List;

/** perfilEtiqueta: dominantes unidos por " + "; perfilTipo: UNIMODAL o MULTIMODAL. Null sin dominantes. */
public record ResultadoInstrumento(long cuestionarioId, String versionMotor, List<ResultadoEstilo> estilos,
        String perfilEtiqueta, String perfilTipo) {

    /** Solo primarios: un compuesto (AC-CE, polos del ILS) tiene otro rango por construcción. */
    public boolean rangosHomogeneos() {
        return RangoTeorico.homogeneos(estilos.stream().filter(e -> e.tipo() == TipoEstilo.PRIMARIO)
                .map(e -> new RangoTeorico.Rango(e.rangoMin(), e.rangoMax())).toList());
    }
}
