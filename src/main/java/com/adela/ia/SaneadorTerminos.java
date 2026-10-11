package com.adela.ia;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Quita los nombres de instrumentos publicados de los textos que ve el usuario
 * (el informe de la IA y las marcas): la interfaz no nombra instrumentos. Cambia
 * cada término, sin distinguir mayúsculas y solo como palabra completa, por una
 * descripción genérica. El cuestionario nunca pasa por aquí.
 */
final class SaneadorTerminos {

    static final String REEMPLAZO = "un instrumento de referencia";

    /** Una sola pasada con los términos de mayor a menor largo: el más largo gana y el reemplazo no se vuelve a tocar. */
    private final Pattern patron;

    SaneadorTerminos(Set<String> terminos) {
        String alternativas = terminos.stream().filter(t -> t != null && !t.isBlank()).map(String::strip)
                .distinct().sorted(Comparator.comparingInt(String::length).reversed().thenComparing(t -> t))
                .map(Pattern::quote).collect(Collectors.joining("|"));
        this.patron = alternativas.isEmpty() ? null
                : Pattern.compile("(?<![\\p{L}\\p{N}_])(?:" + alternativas + ")(?![\\p{L}\\p{N}_])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    String sanear(String texto) {
        return texto == null || patron == null ? texto
                : patron.matcher(texto).replaceAll(Matcher.quoteReplacement(REEMPLAZO));
    }

    private List<String> sanear(List<String> textos) {
        return textos == null ? null : textos.stream().map(this::sanear).toList();
    }

    /** Solo el mensaje y cómo verificar; las referencias son identificadores, no texto. */
    Marca sanear(Marca m) {
        return patron == null ? m
                : new Marca(m.origen(), m.riesgo(), m.seccion(), m.refs(), sanear(m.mensaje()),
                        sanear(m.comoVerificar()));
    }

    /**
     * Los textos libres del informe. Los códigos (modo, esquema, origen, el id de
     * reconocido.catalogo) quedan como están: la interfaz no los muestra.
     */
    AnalisisIa sanear(AnalisisIa a) {
        if (a == null || patron == null) {
            return a;
        }
        AnalisisIa.Reconocido reconocido = a.reconocido() == null ? null
                : new AnalisisIa.Reconocido(a.reconocido().catalogo(), a.reconocido().confianza(),
                        a.reconocido().estructuraIgual(), sanear(a.reconocido().senales()),
                        sanear(a.reconocido().diferencias()));
        AnalisisIa.Lectura lectura = a.lectura() == null ? null
                : new AnalisisIa.Lectura(a.lectura().esquema(), a.lectura().lecturaAsistente(), a.lectura().corte(),
                        sanear(a.lectura().motivo()),
                        a.lectura().descartados() == null ? null
                                : a.lectura().descartados().stream()
                                        .map(d -> new AnalisisIa.Descartado(d.esquema(), sanear(d.porque())))
                                        .toList(),
                        sanear(a.lectura().sugerencia()));
        List<AnalisisIa.NoSoportado> noSoportado = a.noSoportado() == null ? null
                : a.noSoportado().stream()
                        .map(n -> new AnalisisIa.NoSoportado(sanear(n.que()), sanear(n.resolucion()))).toList();
        List<AnalisisIa.Revision> revision = a.revision() == null ? null
                : a.revision().stream()
                        .map(r -> new AnalisisIa.Revision(r.prioridad(), r.riesgo(), r.seccion(),
                                sanear(r.ubicacion()), sanear(r.porque()), sanear(r.comoVerificar())))
                        .toList();
        return new AnalisisIa(a.modo(), reconocido, a.modeloAsistente(), lectura, a.origen(),
                sanear(a.faltantes()), noSoportado, revision, sanear(a.preguntasAlProfesor()));
    }
}
