package com.adela.calificacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Claves de prueba que reproducen la estructura de los instrumentos reales. El
 * id de la opción j (1..K) del ítem i es i * 10 + j.
 */
final class Instrumentos {
    static final ConfigInterpretacion SIN_INTERPRETACION = new ConfigInterpretacion(EsquemaInterpretacion.NINGUNA,
            10, List.of(), List.of());

    private Instrumentos() {
    }

    static long opcion(long item, int j) {
        return item * 10 + j;
    }

    static EstiloClave primario(long id, String nombre) {
        return new EstiloClave(id, nombre, TipoEstilo.PRIMARIO, (int) id, null);
    }

    static EstiloClave compuesto(long id, String nombre, Map<Long, Double> coeficientes) {
        return new EstiloClave(id, nombre, TipoEstilo.COMPUESTO, (int) id, coeficientes);
    }

    /** pesosPorOpcion(j) da los pesos de la opción j del ítem. */
    static List<ItemClave> items(int n, int k, FormatoItem formato, boolean obligatorio, Integer puntos,
            Function<Integer, Map<Long, Double>> pesosPorOpcion) {
        List<ItemClave> items = new ArrayList<>();
        for (long i = 1; i <= n; i++) {
            List<OpcionClave> ops = new ArrayList<>();
            for (int j = 1; j <= k; j++)
                ops.add(new OpcionClave(opcion(i, j), pesosPorOpcion.apply(j)));
            items.add(new ItemClave(i, formato, obligatorio, 0, null, puntos, ops));
        }
        return items;
    }

    static ClaveInstrumento clave(List<ItemClave> items, List<EstiloClave> estilos, ConfigInterpretacion cfg) {
        return new ClaveInstrumento(1, false, items, estilos, cfg);
    }

    /** ACRA: cada ítem es una escala Likert 1-4 del mismo estilo. */
    static ClaveInstrumento acra() {
        return clave(items(20, 4, FormatoItem.UNICA, true, null, j -> Map.of(1L, (double) j)),
                List.of(primario(1, "Adquisición")), SIN_INTERPRETACION);
    }

    /** Herrmann/PNL: cada opción apunta a un cuadrante distinto. */
    static ClaveInstrumento herrmann() {
        return clave(items(10, 4, FormatoItem.UNICA, true, null, j -> Map.of((long) j, 1.0)), cuatroEstilos(),
                SIN_INTERPRETACION);
    }

    /** VARK: selección múltiple opcional, cada opción a una modalidad. */
    static ClaveInstrumento vark(ConfigInterpretacion cfg) {
        return clave(items(16, 4, FormatoItem.MULTIPLE, false, null, j -> Map.of((long) j, 1.0)),
                List.of(primario(1, "V"), primario(2, "A"), primario(3, "R"), primario(4, "K")), cfg);
    }

    /** Kolb: jerarquización de 4 opciones; compuesto AC-CE. */
    static ClaveInstrumento kolb() {
        List<EstiloClave> estilos = new ArrayList<>(List.of(primario(1, "CE"), primario(2, "RO"),
                primario(3, "AC"), primario(4, "AE")));
        estilos.add(compuesto(5, "AC-CE", Map.of(3L, 1.0, 1L, -1.0)));
        return clave(items(12, 4, FormatoItem.JERARQUIA, true, null, j -> Map.of((long) j, 1.0)), estilos,
                SIN_INTERPRETACION);
    }

    /** ILS: opción a suma al polo A, b al polo B; el compuesto A-B lleva las bandas. */
    static ClaveInstrumento ils(List<Banda> bandas) {
        return clave(items(11, 2, FormatoItem.UNICA, true, null, j -> Map.of((long) j, 1.0)),
                List.of(primario(1, "A"), primario(2, "B"), compuesto(3, "A-B", Map.of(1L, 1.0, 2L, -1.0))),
                new ConfigInterpretacion(EsquemaInterpretacion.BAREMO, 10, bandas, List.of()));
    }

    /** Herrmann en formato de reparto de 5 puntos. */
    static ClaveInstrumento reparto() {
        return clave(items(6, 4, FormatoItem.REPARTO, true, 5, j -> Map.of((long) j, 1.0)), cuatroEstilos(),
                SIN_INTERPRETACION);
    }

    static List<EstiloClave> cuatroEstilos() {
        return List.of(primario(1, "A"), primario(2, "B"), primario(3, "C"), primario(4, "D"));
    }

    /** Marca la opción j de cada ítem indicado. */
    static Map<Long, RespuestaItem> marcar(Map<Long, Integer> opcionPorItem) {
        Map<Long, RespuestaItem> r = new HashMap<>();
        opcionPorItem.forEach((item, j) -> r.put(item, new RespuestaItem(item, Map.of(opcion(item, j), 1.0))));
        return r;
    }

    /** Todos los ítems de 1 a n responden la misma opción j. */
    static Map<Long, RespuestaItem> todos(int n, int j) {
        Map<Long, Integer> m = new HashMap<>();
        for (long i = 1; i <= n; i++)
            m.put(i, j);
        return marcar(m);
    }

    static ResultadoEstilo estilo(ResultadoInstrumento r, long estiloId) {
        return r.estilos().stream().filter(e -> e.estiloId() == estiloId).findFirst().orElseThrow();
    }

    /** Respuesta válida al azar para el formato del ítem. */
    static RespuestaItem aleatoria(ItemClave it, Random rnd) {
        List<OpcionClave> ops = new ArrayList<>(it.opciones());
        Collections.shuffle(ops, rnd);
        boolean enBlanco = !it.obligatorio() && rnd.nextInt(5) == 0;
        Map<Long, Double> c = new HashMap<>();
        switch (it.formato()) {
            case UNICA -> {
                if (!enBlanco)
                    c.put(ops.get(0).opcionId(), 1.0);
            }
            case MULTIPLE -> {
                int lo = it.minSelEfectivo(), hi = it.maxSelEfectivo();
                int t = enBlanco ? 0 : lo + rnd.nextInt(hi - lo + 1);
                for (int j = 0; j < t; j++)
                    c.put(ops.get(j).opcionId(), 1.0);
            }
            case JERARQUIA -> {
                for (int j = 0; j < ops.size(); j++)
                    c.put(ops.get(j).opcionId(), (double) (j + 1));
            }
            case REPARTO -> {
                ops.forEach(o -> c.put(o.opcionId(), 0.0));
                for (int p = 0; p < it.puntosRepartir(); p++)
                    c.merge(ops.get(rnd.nextInt(ops.size())).opcionId(), 1.0, Double::sum);
            }
        }
        return new RespuestaItem(it.itemId(), c);
    }

    static Map<Long, RespuestaItem> aleatorias(ClaveInstrumento clave, Random rnd) {
        return clave.items().stream().map(it -> aleatoria(it, rnd))
                .collect(Collectors.toMap(RespuestaItem::itemId, r -> r));
    }
}
