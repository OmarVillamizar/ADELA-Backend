package com.adela.calificacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import com.adela.calificacion.RangoTeorico.Rango;

public final class MotorCalificacion {
    public static final String VERSION = "2.0.0";
    /** Fracción máxima de ítems obligatorios omitidos que todavía se prorratea. */
    public static final double UMBRAL_OMISION = 0.10;

    private MotorCalificacion() {
    }

    /** respuestas: itemId -> respuesta. Un ítem ausente cuenta como no respondido. */
    public static ResultadoInstrumento calificar(ClaveInstrumento clave, Map<Long, RespuestaItem> respuestas) {
        List<ResultadoEstilo> lista = new ArrayList<>();
        for (EstiloClave e : clave.estilos())
            lista.add(calificarEstilo(clave, e, respuestas));
        return Interpretador.interpretar(clave, lista);
    }

    static ResultadoEstilo calificarEstilo(ClaveInstrumento clave, EstiloClave e, Map<Long, RespuestaItem> resp) {
        ToDoubleFunction<OpcionClave> w = Pesos.de(e);
        Rango total = Rango.CERO, observado = Rango.CERO;
        double bruto = 0;
        int aportantes = 0, omitidos = 0;

        for (ItemClave it : clave.items()) {
            Rango r = RangoTeorico.deItem(it, w);
            total = total.mas(r);
            if (r.degenerado())
                continue;
            aportantes++;
            RespuestaItem ri = resp.get(it.itemId());
            boolean respondido = ri != null && ri.respondido();
            if (!respondido && it.obligatorio()) {
                omitidos++;
                continue;
            }
            observado = observado.mas(r);
            bruto += Pesos.aporte(it, ri, w);
        }

        if (omitidos == 0)
            return construir(e, bruto, total, EstadoCalculo.CALCULADO);

        boolean excede = aportantes == 0 || (double) omitidos / aportantes > UMBRAL_OMISION;
        if (!clave.permitirOmisiones() || excede || observado.degenerado())
            return construir(e, null, total, EstadoCalculo.NO_CALCULABLE);

        double pObs = (bruto - observado.min()) / (observado.max() - observado.min());
        double estimado = total.min() + pObs * (total.max() - total.min());
        return construir(e, estimado, total, EstadoCalculo.PRORRATEADO);
    }

    private static ResultadoEstilo construir(EstiloClave e, Double bruto, Rango r, EstadoCalculo estado) {
        return new ResultadoEstilo(e.estiloId(), e.nombre(), e.tipo(), e.orden(), bruto, r.min(), r.max(),
                pomp(bruto, r), estado, null, false);
    }

    /** POMP = 100 (s - min) / (max - min). Fuera de rango es un error de la clave, no del dato. */
    static Double pomp(Double bruto, Rango r) {
        if (bruto == null || r.degenerado())
            return null;
        if (bruto < r.min() - Calculos.EPS || bruto > r.max() + Calculos.EPS)
            throw new ClaveInconsistenteException(
                    "Puntaje " + bruto + " fuera del rango [" + r.min() + ", " + r.max() + "]");
        double v = 100.0 * (bruto - r.min()) / (r.max() - r.min());
        return Math.max(0.0, Math.min(100.0, v));
    }
}
