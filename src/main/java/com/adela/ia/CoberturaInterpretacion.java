package com.adela.ia;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.adela.calificacion.EscalaBanda;
import com.adela.calificacion.FormatoItem;
import com.adela.calificacion.TipoEstilo;
import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.InterpretacionDTO.BandaDTO;
import com.adela.dto.InterpretacionDTO.EscalonDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.PreguntaDTO;

/**
 * Cobertura del baremo (L4): las bandas deben cubrir el rango teórico de su
 * estilo y los escalones el total posible, sin huecos. Un hueco deja a alguien
 * sin nivel, así que es un punto a revisar, no un error.
 *
 * El rango es un port de rangoEstilo / rangoPregunta del editor (borrador.js),
 * para que el servidor y la pantalla midan igual. Solo se llama con la
 * estructura ya validada (L2).
 */
final class CoberturaInterpretacion {

    /** Tolerancias: BRUTO va en pasos enteros; POMP admite el 0,01 de 49,99 a 50. */
    private static final double EPS = 1e-9;
    private static final double HUECO_POMP = 0.011;

    private record Rango(double min, double max, int preguntas) {
    }

    private CoberturaInterpretacion() {
    }

    static List<Marca> revisar(CuestionarioDTO dto) {
        List<Marca> marcas = new ArrayList<>();
        InterpretacionDTO interpretacion = dto.getInterpretacion();
        if (interpretacion == null) {
            return marcas;
        }
        bandas(dto, interpretacion, marcas);
        escalones(dto, interpretacion, marcas);
        return marcas;
    }

    private static void bandas(CuestionarioDTO dto, InterpretacionDTO interpretacion, List<Marca> marcas) {
        if (interpretacion.bandas() == null) {
            return;
        }
        Map<String, List<BandaDTO>> grupos = new LinkedHashMap<>();
        for (BandaDTO b : interpretacion.bandas()) {
            if (b.estilo() != null && b.escala() != null && b.limiteInferior() != null
                    && b.limiteSuperior() != null) {
                grupos.computeIfAbsent(b.estilo() + "\u0000" + b.escala(), k -> new ArrayList<>()).add(b);
            }
        }
        for (List<BandaDTO> grupo : grupos.values()) {
            String nombre = grupo.get(0).estilo();
            EstiloDTO estilo = dto.getEstilos().stream().filter(e -> nombre.equals(e.getNombre())).findFirst()
                    .orElse(null);
            if (estilo == null) {
                continue;
            }
            EscalaBanda escala = grupo.get(0).escala();
            Rango rango = rangoEstilo(dto, estilo);
            if (escala == EscalaBanda.BRUTO && rango.preguntas() == 0) {
                continue;
            }
            List<Double[]> tramos = grupo.stream()
                    .sorted(Comparator.comparingDouble(BandaDTO::limiteInferior))
                    .map(b -> new Double[] { b.limiteInferior(), b.limiteSuperior() }).toList();
            boolean bruto = escala == EscalaBanda.BRUTO;
            double desde = bruto ? rango.min() : 0;
            double hasta = bruto ? rango.max() : 100;
            List<String> huecos = huecos(tramos, desde, hasta, bruto ? EPS : HUECO_POMP,
                    bruto ? 1 + EPS : HUECO_POMP);
            if (!huecos.isEmpty()) {
                String que = bruto ? "puntaje directo" : "porcentaje";
                marcas.add(Marca.validador(Marca.MEDIO, "bandas", List.of("estilo:" + nombre),
                        "Las bandas de '" + nombre + "' (" + que + ") no cubren todo el rango posible "
                                + num(desde) + "–" + num(hasta) + ": sin nivel " + String.join(", ", huecos) + ".",
                        "Compáralas con la tabla de baremos del documento. Si el documento deja ese hueco, "
                                + "se queda así; si no, completa las bandas en el editor."));
            }
        }
    }

    private static void escalones(CuestionarioDTO dto, InterpretacionDTO interpretacion, List<Marca> marcas) {
        if (interpretacion.escalones() == null || interpretacion.escalones().isEmpty()) {
            return;
        }
        double total = dto.getEstilos().stream().filter(e -> tipo(e) == TipoEstilo.PRIMARIO)
                .mapToDouble(e -> rangoEstilo(dto, e).max()).sum();
        if (total < 1) {
            return;
        }
        List<Double[]> tramos = interpretacion.escalones().stream()
                .filter(s -> s.totalMin() != null && s.totalMax() != null)
                .sorted(Comparator.comparingDouble(EscalonDTO::totalMin))
                .map(s -> new Double[] { s.totalMin(), s.totalMax() }).toList();
        List<String> huecos = huecos(tramos, 1, total, EPS, 1 + EPS);
        if (!huecos.isEmpty()) {
            marcas.add(Marca.validador(Marca.MEDIO, "escalones", List.of(),
                    "Los escalones no cubren todos los totales posibles " + num(1) + "–" + num(total)
                            + ": sin distancia " + String.join(", ", huecos) + ".",
                    "Compáralos con la tabla de distancias del documento. Un total sin escalón no recibe perfil."));
        }
    }

    /**
     * Zonas que los tramos (ordenados por inicio) dejan sin cubrir dentro de
     * [desde, hasta]. tolBorde es lo que se tolera en los extremos y
     * tolHueco el salto máximo entre un tramo y el siguiente.
     */
    private static List<String> huecos(List<Double[]> tramos, double desde, double hasta, double tolBorde,
            double tolHueco) {
        List<String> huecos = new ArrayList<>();
        if (tramos.isEmpty()) {
            huecos.add("en todo el rango");
            return huecos;
        }
        if (tramos.get(0)[0] > desde + tolBorde) {
            huecos.add("por debajo de " + num(tramos.get(0)[0]));
        }
        double techo = tramos.get(0)[1];
        for (int i = 1; i < tramos.size(); i++) {
            if (tramos.get(i)[0] - techo > tolHueco) {
                huecos.add("entre " + num(techo) + " y " + num(tramos.get(i)[0]));
            }
            techo = Math.max(techo, tramos.get(i)[1]);
        }
        if (techo < hasta - tolBorde) {
            huecos.add("por encima de " + num(techo));
        }
        return huecos;
    }

    /** Mínimo y máximo posible de un estilo y cuántas preguntas lo puntúan. */
    private static Rango rangoEstilo(CuestionarioDTO dto, EstiloDTO estilo) {
        double min = 0;
        double max = 0;
        int preguntas = 0;
        for (PreguntaDTO p : dto.getPreguntas()) {
            double[] w = p.getOpciones().stream().mapToDouble(o -> pesoEn(o, estilo)).toArray();
            if (Arrays.stream(w).allMatch(v -> v == 0)) {
                continue;
            }
            preguntas++;
            double[] r = rangoPregunta(p, w);
            min += r[0];
            max += r[1];
        }
        return new Rango(min, max, preguntas);
    }

    /** Peso que suma una opción a un estilo: directo, o combinado si es compuesto. */
    private static double pesoEn(OpcionDTO o, EstiloDTO e) {
        if (tipo(e) != TipoEstilo.COMPUESTO) {
            return peso(o, e.getId());
        }
        double suma = 0;
        for (EstiloDTO.CoeficienteDTO c : e.getCoeficientes()) {
            suma += valor(c.coeficiente()) * peso(o, c.estiloId());
        }
        return suma;
    }

    private static double peso(OpcionDTO o, int estiloId) {
        for (OpcionDTO.PesoDTO w : o.getPesos()) {
            if (w.estiloId() == estiloId) {
                return valor(w.peso());
            }
        }
        return 0;
    }

    private static double valor(Double d) {
        return d == null || !Double.isFinite(d) ? 0 : d;
    }

    private static TipoEstilo tipo(EstiloDTO e) {
        return e.getTipo() != null ? e.getTipo() : TipoEstilo.PRIMARIO;
    }

    /** Sin 'obligatoria' se conserva la regla de siempre: múltiple opcional, el resto obligatoria. */
    private static boolean obligatoria(PreguntaDTO p) {
        return p.getObligatoria() != null ? p.getObligatoria() : p.getFormato() != FormatoItem.MULTIPLE;
    }

    /** Mínimo y máximo que aporta una pregunta a un estilo (mismas reglas que el motor). */
    private static double[] rangoPregunta(PreguntaDTO p, double[] w) {
        int k = w.length;
        double lo;
        double hi;
        double[] asc = w.clone();
        Arrays.sort(asc);
        if (p.getFormato() == FormatoItem.MULTIPLE) {
            int min = Math.max(p.getMinSelecciones() != null ? p.getMinSelecciones() : 0, obligatoria(p) ? 1 : 0);
            int max = p.getMaxSelecciones() == null ? k : Math.min(p.getMaxSelecciones(), k);
            lo = Double.POSITIVE_INFINITY;
            hi = Double.NEGATIVE_INFINITY;
            for (int t = min; t <= max; t++) {
                double menores = 0;
                double mayores = 0;
                for (int j = 0; j < t; j++) {
                    menores += asc[j];
                    mayores += asc[k - 1 - j];
                }
                lo = Math.min(lo, menores);
                hi = Math.max(hi, mayores);
            }
        } else if (p.getFormato() == FormatoItem.JERARQUIA) {
            // El rango más alto (k) va a la opción de mayor peso para el máximo.
            lo = 0;
            hi = 0;
            for (int i = 0; i < k; i++) {
                hi += asc[i] * (i + 1);
                lo += asc[i] * (k - i);
            }
        } else if (p.getFormato() == FormatoItem.REPARTO) {
            int puntos = p.getPuntosRepartir() != null ? p.getPuntosRepartir() : 0;
            lo = puntos * asc[0];
            hi = puntos * asc[k - 1];
        } else {
            lo = asc[0];
            hi = asc[k - 1];
        }
        return obligatoria(p) ? new double[] { lo, hi } : new double[] { Math.min(lo, 0), Math.max(hi, 0) };
    }

    /** Entero sin decimales; si no, hasta dos y sin ceros de más. */
    private static String num(double v) {
        return BigDecimal.valueOf(Math.round(v * 100) / 100.0).stripTrailingZeros().toPlainString();
    }
}
