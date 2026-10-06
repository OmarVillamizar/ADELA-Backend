package com.adela.calificacion;

import java.util.Map;
import java.util.function.ToDoubleFunction;

public final class Pesos {
    private Pesos() {
    }

    /**
     * w(o,e) para un primario, o w_k(o) = SUMA c(k,e) w(o,e) para un compuesto.
     * Por linealidad, calificar el compuesto con w_k da lo mismo que combinar los
     * puntajes de sus primarios, y el mismo algoritmo de rango sirve para ambos.
     */
    public static ToDoubleFunction<OpcionClave> de(EstiloClave estilo) {
        if (estilo.tipo() == TipoEstilo.PRIMARIO) {
            long id = estilo.estiloId();
            return o -> o.peso(id);
        }
        Map<Long, Double> c = estilo.coeficientes();
        return o -> {
            double s = 0;
            for (Map.Entry<Long, Double> e : c.entrySet())
                s += e.getValue() * o.peso(e.getKey());
            return s;
        };
    }

    /** Contribución de un ítem respondido: SUMA a(o) * w(o). */
    public static double aporte(ItemClave item, RespuestaItem r, ToDoubleFunction<OpcionClave> w) {
        if (r == null || !r.respondido())
            return 0.0;
        double s = 0;
        for (OpcionClave o : item.opciones()) {
            Double a = r.cantidades().get(o.opcionId());
            if (a != null)
                s += a * w.applyAsDouble(o);
        }
        return s;
    }
}
