package com.adela.dto;

import java.util.Map;

import com.adela.calificacion.AgregadoGrupo.EstiloAgregado;
import com.adela.calificacion.EstadoCalculo;
import com.adela.calificacion.Estadistica.Resumen;
import com.adela.calificacion.ResultadoEstilo;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Puntaje de un estilo. valor es el puntaje directo: en un resultado individual
 * la suma; en un reporte, la media del grupo. Los campos de cada caso quedan
 * fuera del JSON del otro.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstiloResultadoDTO {
    private String nombre;
    private Double valor;

    private Double rangoMin;
    private Double rangoMax;

    // Individual
    private Double pomp;
    private EstadoCalculo estado;
    private String nivel;
    private Boolean dominante;

    // Reporte de un grupo o una cápsula
    private Resumen estadisticaBruto;
    private Resumen estadisticaPomp;
    private Map<String, Long> distribucionBandas;
    private Map<String, Long> distribucionBaremoLocal;

    public static EstiloResultadoDTO de(ResultadoEstilo r) {
        EstiloResultadoDTO dto = new EstiloResultadoDTO();
        dto.setNombre(r.nombre());
        dto.setValor(r.bruto());
        dto.setRangoMin(r.rangoMin());
        dto.setRangoMax(r.rangoMax());
        dto.setPomp(r.pomp());
        dto.setEstado(r.estado());
        dto.setNivel(r.banda());
        dto.setDominante(r.dominante());
        return dto;
    }

    /** Sin resultados la media es 0, no null: así se mostraba antes y no hay NaN (BUG-03). */
    public static EstiloResultadoDTO de(EstiloAgregado e) {
        EstiloResultadoDTO dto = new EstiloResultadoDTO();
        dto.setNombre(e.nombre());
        dto.setValor(e.bruto() == null ? 0d : e.bruto().media());
        dto.setRangoMin(e.rangoMin());
        dto.setRangoMax(e.rangoMax());
        dto.setEstadisticaBruto(e.bruto());
        dto.setEstadisticaPomp(e.pomp());
        dto.setDistribucionBandas(e.distribucionBandas());
        dto.setDistribucionBaremoLocal(e.distribucionBaremoLocal());
        return dto;
    }
}
