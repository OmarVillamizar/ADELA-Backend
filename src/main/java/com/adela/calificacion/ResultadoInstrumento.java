package com.adela.calificacion;

import java.util.List;

/** perfilEtiqueta: dominantes unidos por " + "; perfilTipo: UNIMODAL o MULTIMODAL. Null sin dominantes. */
public record ResultadoInstrumento(long cuestionarioId, String versionMotor, List<ResultadoEstilo> estilos,
        String perfilEtiqueta, String perfilTipo) {

    public boolean rangosHomogeneos() {
        return RangoTeorico.homogeneos(
                estilos.stream().map(e -> new RangoTeorico.Rango(e.rangoMin(), e.rangoMax())).toList());
    }
}
