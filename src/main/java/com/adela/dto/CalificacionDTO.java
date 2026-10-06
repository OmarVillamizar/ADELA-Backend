package com.adela.dto;

import java.util.Map;

import com.adela.calificacion.AgregadoGrupo.Agregado;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.MotorCalificacion;
import com.adela.calificacion.ResultadoInstrumento;
import com.adela.entities.Cuestionario;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Datos de la calificación que no son de un estilo. rangosHomogeneos indica si
 * el puntaje directo es comparable entre estilos (si no, el gráfico va en % del
 * máximo). El perfil es del resultado individual; las distribuciones, del reporte.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CalificacionDTO(String versionMotor, EsquemaInterpretacion esquema, boolean esIpsativo,
        boolean rangosHomogeneos, String perfilEtiqueta, String perfilTipo, Map<String, Long> distribucionPerfiles,
        Boolean baremoLocalDisponible) {

    public static CalificacionDTO individual(Cuestionario c, ResultadoInstrumento r) {
        return new CalificacionDTO(r.versionMotor(), c.getEsquemaInterpretacion(), c.isEsIpsativo(),
                r.rangosHomogeneos(), r.perfilEtiqueta(), r.perfilTipo(), null, null);
    }

    public static CalificacionDTO grupal(Cuestionario c, Agregado a) {
        return new CalificacionDTO(MotorCalificacion.VERSION, c.getEsquemaInterpretacion(), c.isEsIpsativo(),
                a.rangosHomogeneos(), null, null, a.distribucionPerfiles(), a.baremoLocalDisponible());
    }
}
