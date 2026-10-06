package com.adela.calificacion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Reglas de una respuesta válida por formato. Lista vacía = válida. */
public final class ValidadorRespuesta {
    private ValidadorRespuesta() {
    }

    public static List<String> errores(ItemClave it, RespuestaItem r) {
        List<String> e = new ArrayList<>();
        Map<Long, Double> c = (r == null || r.cantidades() == null) ? Map.of() : r.cantidades();
        Set<Long> validas = it.opciones().stream().map(OpcionClave::opcionId).collect(Collectors.toSet());
        for (Long id : c.keySet())
            if (!validas.contains(id))
                e.add("La opción " + id + " no pertenece al ítem " + it.itemId());
        if (c.isEmpty()) {
            if (it.obligatorio())
                e.add("El ítem " + it.itemId() + " es obligatorio");
            return e;
        }
        switch (it.formato()) {
            case UNICA, MULTIPLE -> {
                if (c.values().stream().anyMatch(a -> a == null || a != 1.0))
                    e.add("Ítem " + it.itemId() + ": cantidad inválida");
                int t = c.size();
                if (t < it.minSelEfectivo() || t > it.maxSelEfectivo())
                    e.add("Ítem " + it.itemId() + ": se marcaron " + t + " opciones; se permiten entre "
                            + it.minSelEfectivo() + " y " + it.maxSelEfectivo());
            }
            case JERARQUIA -> {
                int k = it.opciones().size();
                Set<Integer> rangos = new HashSet<>();
                for (Double a : c.values())
                    if (a != null && a == Math.rint(a))
                        rangos.add(a.intValue());
                Set<Integer> esperado = IntStream.rangeClosed(1, k).boxed().collect(Collectors.toSet());
                if (c.size() != k || !rangos.equals(esperado))
                    e.add("Ítem " + it.itemId() + ": los rangos deben ser una permutación de 1 a " + k);
            }
            case REPARTO -> {
                double suma = 0;
                for (Double a : c.values()) {
                    if (a == null || a < 0 || a != Math.rint(a))
                        e.add("Ítem " + it.itemId() + ": los puntos deben ser enteros no negativos");
                    else
                        suma += a;
                }
                if (Math.abs(suma - it.puntosRepartir()) > Calculos.EPS)
                    e.add("Ítem " + it.itemId() + ": los puntos deben sumar " + it.puntosRepartir());
            }
        }
        return e;
    }
}
