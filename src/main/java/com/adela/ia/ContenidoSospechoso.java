package com.adela.ia;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.InterpretacionDTO.ComplementariaConfigDTO;
import com.adela.dto.InterpretacionDTO.OpcionComplementariaDTO;
import com.adela.dto.InterpretacionDTO.PlanoDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.PreguntaDTO;

/**
 * Revisión determinista de todo texto que un usuario verá: HTML o código, frases
 * que parecen órdenes para una IA y cadenas con forma de llave secreta. Un
 * documento o una IA externa pueden colar esas cosas. Nunca se modifica el
 * texto: lo decide una persona, y por eso cada hallazgo es una marca ALTO.
 */
final class ContenidoSospechoso {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<Pattern> EJECUTABLE = List.of(
            Pattern.compile("<\\s*/?\\s*(?:script|iframe|object|embed|img|svg|style|link|meta)\\b", FLAGS),
            Pattern.compile("\\bon[a-z]+\\s*=", FLAGS),
            Pattern.compile("javascript\\s*:", FLAGS),
            Pattern.compile("data\\s*:\\s*text/html", FLAGS));

    private static final List<Pattern> INSTRUCCION = List.of(
            Pattern.compile("ignora(?:r)?\\s+(?:todas\\s+)?las\\s+instrucciones", FLAGS),
            Pattern.compile("ignore\\s+(?:all\\s+)?(?:previous|above)\\s+instructions", FLAGS),
            Pattern.compile("\\bsystem\\s*:", FLAGS),
            Pattern.compile("\\bprompts?\\b", FLAGS),
            Pattern.compile("api[ _-]?key", FLAGS),
            Pattern.compile("nuevo\\s+rol", FLAGS));

    private static final Pattern SECRETO = Pattern.compile("sk-[A-Za-z0-9]{16,}");

    /** Dónde está el texto: sección de la marca, referencias y cómo nombrarlo en el mensaje. */
    private record Sitio(String seccion, List<String> refs, String donde) {
    }

    private ContenidoSospechoso() {
    }

    static List<Marca> revisar(CuestionarioDTO dto) {
        List<Marca> marcas = new ArrayList<>();
        Sitio meta = new Sitio("meta", List.of(), "");
        revisar(marcas, dto.getNombre(), conDonde(meta, "el nombre"));
        revisar(marcas, dto.getSiglas(), conDonde(meta, "las siglas"));
        revisar(marcas, dto.getDescripcion(), conDonde(meta, "la descripción"));
        revisar(marcas, dto.getAutor(), conDonde(meta, "el autor"));
        revisar(marcas, dto.getVersion(), conDonde(meta, "la versión"));

        if (dto.getEstilos() != null) {
            for (EstiloDTO e : dto.getEstilos()) {
                revisar(marcas, e.getNombre(), new Sitio("estilos", List.of("estilo:" + e.getNombre()),
                        "el nombre de un estilo"));
            }
        }
        if (dto.getPreguntas() != null) {
            for (PreguntaDTO p : dto.getPreguntas()) {
                List<String> refs = List.of("pregunta:" + p.getOrden());
                revisar(marcas, p.getPregunta(), new Sitio("items", refs, "la pregunta " + p.getOrden()));
                if (p.getOpciones() != null) {
                    for (OpcionDTO o : p.getOpciones()) {
                        revisar(marcas, o.getRespuesta(), new Sitio("items", refs,
                                "la opción " + o.getOrden() + " de la pregunta " + p.getOrden()));
                    }
                }
            }
        }
        if (dto.getInterpretacion() != null) {
            interpretacion(marcas, dto.getInterpretacion());
        }
        return marcas;
    }

    private static void interpretacion(List<Marca> marcas, InterpretacionDTO i) {
        if (i.bandas() != null) {
            i.bandas().forEach(b -> revisar(marcas, b.etiqueta(), new Sitio("bandas",
                    List.of("estilo:" + b.estilo()), "la etiqueta de una banda de '" + b.estilo() + "'")));
        }
        PlanoDTO plano = i.plano();
        if (plano != null) {
            Sitio sitio = new Sitio("plano", List.of(), "el plano de cuadrantes");
            for (String texto : new String[] { plano.xAltoYAlto(), plano.xBajoYAlto(), plano.xBajoYBajo(),
                    plano.xAltoYBajo() }) {
                revisar(marcas, texto, sitio);
            }
        }
        ComplementariaConfigDTO c = i.complementaria();
        if (c != null) {
            Sitio sitio = new Sitio("complementaria", List.of(), "la pregunta complementaria");
            for (String texto : new String[] { c.titulo(), c.introduccion(), c.enunciado(), c.nota() }) {
                revisar(marcas, texto, sitio);
            }
            if (c.opciones() != null) {
                for (OpcionComplementariaDTO o : c.opciones()) {
                    for (String texto : new String[] { o.texto(), o.descripcion(), o.resultado(),
                            o.resultadoDescripcion() }) {
                        revisar(marcas, texto, sitio);
                    }
                }
            }
        }
    }

    private static Sitio conDonde(Sitio base, String donde) {
        return new Sitio(base.seccion(), base.refs(), donde);
    }

    /** Una marca por categoría y texto, aunque coincidan varios patrones. */
    private static void revisar(List<Marca> marcas, String texto, Sitio sitio) {
        if (texto == null || texto.isBlank()) {
            return;
        }
        if (coincide(EJECUTABLE, texto)) {
            marcas.add(Marca.validador(Marca.ALTO, sitio.seccion(), sitio.refs(),
                    "Contenido ejecutable o HTML en " + sitio.donde()
                            + ": se mostrará como texto; elimínalo si no es parte del instrumento.",
                    "Abre ese texto en el editor y bórralo si no pertenece al instrumento."));
        }
        if (coincide(INSTRUCCION, texto)) {
            marcas.add(Marca.validador(Marca.ALTO, sitio.seccion(), sitio.refs(),
                    "Texto que parece una instrucción para la IA en " + sitio.donde()
                            + ": revisa que no sea parte del documento original.",
                    "Compáralo con el documento original; si no está ahí, bórralo."));
        }
        if (SECRETO.matcher(texto).find()) {
            marcas.add(Marca.validador(Marca.ALTO, "meta", sitio.refs(),
                    "Parece una llave o un secreto en " + sitio.donde() + ": elimínalo antes de guardar.",
                    "Si era una llave real, cámbiala: ya pasó por esta conversación."));
        }
    }

    private static boolean coincide(List<Pattern> patrones, String texto) {
        return patrones.stream().anyMatch(p -> p.matcher(texto).find());
    }
}
