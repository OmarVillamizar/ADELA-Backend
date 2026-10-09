package com.adela.dto;

import java.util.List;
import java.util.Map;

import com.adela.calificacion.AgregadoGrupo.Agregado;
import com.adela.calificacion.ClaveInstrumento;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.EstiloClave;
import com.adela.calificacion.Interpretador;
import com.adela.calificacion.MotorCalificacion;
import com.adela.calificacion.Plano;
import com.adela.calificacion.ResultadoInstrumento;
import com.adela.dto.InterpretacionDTO.PlanoDTO;
import com.adela.entities.Cuestionario;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Datos de la calificación que no son de un estilo. rangosHomogeneos indica si
 * el puntaje directo es comparable entre estilos (si no, el gráfico va en % del
 * máximo). El perfil (y su código de niveles) es del resultado individual; las
 * distribuciones, del reporte.
 * plano aparece con el esquema CUADRANTES; puntosPlano, solo en el reporte de
 * grupo: un punto anónimo por resultado con los dos ejes calculables.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CalificacionDTO(String versionMotor, EsquemaInterpretacion esquema, boolean esIpsativo,
        boolean rangosHomogeneos, String perfilEtiqueta, String perfilTipo, String perfilCodigo,
        Map<String, Long> distribucionPerfiles, Boolean baremoLocalDisponible, PlanoDTO plano,
        List<PuntoPlanoDTO> puntosPlano) {

    public record PuntoPlanoDTO(double x, double y) {
    }

    public static CalificacionDTO individual(Cuestionario c, ResultadoInstrumento r, ClaveInstrumento clave) {
        return new CalificacionDTO(r.versionMotor(), c.getEsquemaInterpretacion(), c.isEsIpsativo(),
                r.rangosHomogeneos(), r.perfilEtiqueta(), r.perfilTipo(), r.perfilCodigo(), null, null,
                plano(c, clave), null);
    }

    public static CalificacionDTO grupal(Cuestionario c, Agregado a, ClaveInstrumento clave,
            List<ResultadoInstrumento> resultados) {
        Plano p = planoActivo(c, clave);
        List<PuntoPlanoDTO> puntos = p == null ? null
                : resultados.stream().map(r -> punto(p, r)).filter(x -> x != null).toList();
        return new CalificacionDTO(MotorCalificacion.VERSION, c.getEsquemaInterpretacion(), c.isEsIpsativo(),
                a.rangosHomogeneos(), null, null, null, a.distribucionPerfiles(), a.baremoLocalDisponible(),
                plano(c, clave), puntos);
    }

    private static Plano planoActivo(Cuestionario c, ClaveInstrumento clave) {
        return c.getEsquemaInterpretacion() == EsquemaInterpretacion.CUADRANTES ? clave.config().plano() : null;
    }

    private static PuntoPlanoDTO punto(Plano p, ResultadoInstrumento r) {
        Double x = Interpretador.brutoCalculado(r.estilos(), p.ejeX());
        Double y = Interpretador.brutoCalculado(r.estilos(), p.ejeY());
        return x == null || y == null ? null : new PuntoPlanoDTO(x, y);
    }

    /** Los ejes se nombran con los estilos de la clave. */
    private static PlanoDTO plano(Cuestionario c, ClaveInstrumento clave) {
        Plano p = planoActivo(c, clave);
        if (p == null)
            return null;
        return new PlanoDTO(nombre(clave, p.ejeX()), nombre(clave, p.ejeY()), p.corteX(), p.corteY(),
                p.xAltoYAlto(), p.xBajoYAlto(), p.xBajoYBajo(), p.xAltoYBajo(), p.invertirX(), p.invertirY());
    }

    private static String nombre(ClaveInstrumento clave, long estiloId) {
        return clave.estilos().stream().filter(e -> e.estiloId() == estiloId).map(EstiloClave::nombre).findFirst()
                .orElse(null);
    }
}
