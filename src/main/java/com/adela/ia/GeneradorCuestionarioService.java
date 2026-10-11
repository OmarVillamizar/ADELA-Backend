package com.adela.ia;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.ia.ClienteLlm.MensajeLlm;
import com.adela.ia.ClienteLlm.PeticionLlm;
import com.adela.ia.ClienteLlm.ProgresoLlm;
import com.adela.ia.ClienteLlm.RespuestaLlm;
import com.adela.ia.DocumentoExtraido.PaginaExtraida;
import com.adela.ia.EstadoTrabajo.Fase;
import com.adela.ia.EstadoTrabajo.ResultadoNelse;
import com.adela.ia.Trabajo.Contexto;
import com.adela.ia.Trabajo.Medicion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * El orquestador de Nelse: una máquina de estados determinista en Java. El modelo extrae y
 * decide dentro de las reglas del prompt; el código lee, valida, corrige y marca.
 *
 * <pre>
 * LEYENDO     PDF a texto limpio (si hay PDF)
 * GENERANDO   modelo: prefijo fijo + DOCUMENTO + PROFESOR
 * VALIDANDO   ValidadorGeneracion: errores duros y marcas
 * CORRIGIENDO si hay errores duros: modelo -> JSON Patch -> aplicar -> VALIDANDO (hasta 2 rondas)
 * LISTO       resultado (con los errores duros que queden, para que la persona los arregle en el editor)
 * </pre>
 *
 * Un patch vacío, que no aplica o truncado, y una salida ilegible, gastan la única
 * regeneración completa del trabajo. Si tampoco así se obtiene una salida legible, FALLIDO.
 *
 * El orden de los mensajes no se toca: prefijo del sistema, luego DOCUMENTO/PROFESOR y por
 * último los turnos de corrección, de modo que cada llamada reutiliza de la caché del
 * proveedor todo lo anterior (plan, tácticas 1 y 2).
 *
 * Refinamiento: una vez por trabajo, sobre un resultado LISTO, con el mismo mecanismo de
 * patch. Si no se logra aplicar, el trabajo vuelve a LISTO con el resultado anterior intacto y
 * un {@code aviso}; el ajuste nunca deja a la persona peor que antes.
 *
 * Ni el documento, ni las instrucciones, ni la salida del modelo van al log o a la respuesta.
 */
@Service
public class GeneradorCuestionarioService {

    private static final Logger logger = LoggerFactory.getLogger(GeneradorCuestionarioService.class);

    static final int MAX_RONDAS = 2;

    /**
     * Tamaño típico de la salida (caracteres de JSON), solo para convertir el avance del stream
     * en un porcentaje aproximado; si el modelo escribe más, el avance se queda en el tope.
     */
    private static final int CARACTERES_ESPERADOS = 25_000;

    static final String AVISO_REFINAMIENTO = "No se pudo aplicar el ajuste; se conserva el resultado anterior.";

    private final ClienteLlm llm;
    private final PromptIa prompt;
    private final ValidadorGeneracion validador;
    private final ExtractorDocumento extractor;
    private final TrabajosIa trabajos;
    private final ObjectMapper om;
    private final String esfuerzoGenerar;
    private final String esfuerzoCorregir;
    private final int maxSalida;

    /** Lo que sale de una llamada de generación: el JSON raíz {cuestionario, analisis} y la salida tal cual. */
    private record Generado(ObjectNode raiz, String crudo, String formato) {
    }

    /** El JSON vigente y su validación. */
    private record Salida(ObjectNode raiz, ResultadoImportacion validacion) {
    }

    /** Lo que se publica al terminar bien. */
    private record Cierre(ResultadoNelse resultado, String aviso) {
    }

    /** Se lanza entre pasos al ver el trabajo cancelado; no es un error y no deja rastro. */
    private static final class Cancelado extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Cancelado() {
            super(null, null, false, false);
        }
    }

    @Autowired
    public GeneradorCuestionarioService(ClienteLlm llm, PromptIa prompt, ValidadorGeneracion validador,
            ExtractorDocumento extractor, TrabajosIa trabajos, ObjectMapper om,
            @Value("${adela.ia.esfuerzo-generar}") String esfuerzoGenerar,
            @Value("${adela.ia.esfuerzo-corregir}") String esfuerzoCorregir,
            @Value("${adela.ia.max-salida}") int maxSalida) {
        this.llm = llm;
        this.prompt = prompt;
        this.validador = validador;
        this.extractor = extractor;
        this.trabajos = trabajos;
        this.om = om;
        this.esfuerzoGenerar = esfuerzoGenerar;
        this.esfuerzoCorregir = esfuerzoCorregir;
        this.maxSalida = maxSalida;
    }

    // ---------------------------------------------------------------- Entradas

    /**
     * Encola un trabajo. pdf e instrucciones son opcionales pero no ambos (lo comprueba el
     * controlador); los bytes ya se leyeron en la petición, porque el MultipartFile no vale
     * cuando esta termina.
     */
    public Trabajo crear(String email, byte[] pdf, String instrucciones) {
        return trabajos.iniciar(email, trabajo -> correr(trabajo, null, () -> generar(trabajo, pdf, instrucciones)));
    }

    /** Encola el único ajuste del trabajo. 409 si no está LISTO o ya se ajustó. */
    public Trabajo refinar(String email, UUID id, SolicitudRefinamiento solicitud) {
        return trabajos.refinar(email, id, trabajo -> {
            Contexto contexto = trabajo.contexto();
            correr(trabajo, contexto.previo(), () -> refinar(trabajo, contexto, solicitud));
        });
    }

    // ---------------------------------------------------------------- Corrida

    /**
     * Ejecuta un paso del trabajo y publica el desenlace. previo es el resultado al que volver si
     * esto es un refinamiento que falla (null en la generación: ahí un fallo es FALLIDO).
     */
    private void correr(Trabajo t, ResultadoNelse previo, Supplier<Cierre> paso) {
        Medicion m = t.nuevaMedicion();
        String estado;
        ResultadoNelse resultado = null;
        try {
            Cierre cierre = paso.get();
            resultado = cierre.resultado();
            t.listo(resultado, cierre.aviso());
            estado = t.cancelado() ? "CANCELADO" : "LISTO";
        } catch (RuntimeException e) {
            if (t.cancelado() || e instanceof Cancelado) {
                estado = "CANCELADO";
            } else if (previo != null) {
                // Refinamiento: la persona ya tenía un resultado bueno; no se lo quitamos.
                logger.warn("Trabajo IA {}: el ajuste falló ({})", t.id(), e.getClass().getSimpleName());
                resultado = previo;
                t.listo(previo, AVISO_REFINAMIENTO);
                estado = "LISTO_SIN_AJUSTE";
            } else if (e instanceof AppException app) {
                t.fallido(app.getCode(), app.getMessage());
                estado = "FALLIDO:" + app.getCode();
            } else {
                // Solo la traza y el id del trabajo: nada del documento ni de la salida del modelo.
                logger.error("Trabajo IA {}: error inesperado", t.id(), e);
                t.fallido(ErrorCode.ERROR_INTERNO,
                        "Ocurrió un error inesperado al preparar el cuestionario. Inténtalo de nuevo.");
                estado = "FALLIDO:" + ErrorCode.ERROR_INTERNO;
            }
        }
        m.cerrar();
        registrar(t, m, estado, resultado);
    }

    /** Una línea por corrida: qué costó y cómo terminó. Sin contenido del documento ni del modelo. */
    private void registrar(Trabajo t, Medicion m, String estado, ResultadoNelse r) {
        long altos = contar(r, "ALTO");
        long medios = contar(r, "MEDIO");
        long bajos = contar(r, "BAJO");
        logger.info("Trabajo IA {}: estado={} segundos={} tokens[entrada={} cacheHit={} cacheMiss={} salida={} "
                + "razonamiento={}] usd={} rondas={} regeneraciones={} marcas[alto={} medio={} bajo={}] "
                + "erroresDuros={} versionPrompt={}", t.id(), estado, m.segundos(), m.promptTokens, m.cacheHit,
                m.cacheMiss, m.completionTokens, m.reasoningTokens, String.format(Locale.ROOT, "%.5f", m.usd()),
                m.rondas, m.regeneraciones, altos, medios, bajos, r == null ? 0 : r.erroresDuros().size(),
                prompt.versionPrompt());
    }

    private static long contar(ResultadoNelse r, String riesgo) {
        return r == null ? 0 : r.marcas().stream().filter(x -> riesgo.equals(x.riesgo())).count();
    }

    // ---------------------------------------------------------------- Generación

    private Cierre generar(Trabajo t, byte[] pdf, String instrucciones) {
        t.fase(Fase.LEYENDO, 5);
        String documento = null;
        int paginasImagen = 0;
        boolean hayInstrucciones = instrucciones != null && !instrucciones.isBlank();
        if (pdf != null) {
            DocumentoExtraido extraido = extractor.extraer(pdf);
            // Una página escaneada solo trae ruido de texto: se deja vacía (conservando su posición
            // para el marcador de página) y se cuenta, que L4 avisa de lo que no se pudo comprobar.
            List<String> textos = extraido.paginas().stream().map(p -> p.escaneada() ? "" : p.texto()).toList();
            documento = LimpiadorTexto.unir(LimpiadorTexto.limpiar(textos));
            paginasImagen = (int) extraido.paginas().stream().filter(PaginaExtraida::escaneada).count();
            if (documento.isBlank() && !hayInstrucciones) {
                throw new AppException(ErrorCode.DOCUMENTO_INVALIDO,
                        "El PDF no tiene texto legible; la lectura de escaneados llega pronto.");
            }
        }
        comprobar(t);

        FuenteNelse fuente = new FuenteNelse(documento, instrucciones, paginasImagen);
        String mensajeUsuario = mensajeUsuario(documento, instrucciones);
        List<MensajeLlm> base = base(mensajeUsuario);

        Medicion m = t.medicion();
        Generado generado = pedirGeneracion(t, base);
        if (generado == null && m.regeneraciones == 0) {
            m.regeneraciones++;
            generado = pedirGeneracion(t, base);
        }
        if (generado == null) {
            throw new AppException(ErrorCode.IA_RESPUESTA_INVALIDA,
                    "Nelse no logró producir un cuestionario legible. Inténtalo de nuevo.");
        }

        Salida salida = validarYCorregir(t, base, generado, fuente, true);
        ResultadoImportacion v = salida.validacion();
        if (v.cuestionario() == null) {
            // Sigue sin poder leerse tras corregir: no hay nada que mostrar en el editor.
            throw new AppException(ErrorCode.IA_RESPUESTA_INVALIDA,
                    "Nelse produjo un cuestionario que no se pudo leer. Inténtalo de nuevo.");
        }
        ResultadoNelse resultado = resultado(m, v);
        t.contexto(new Contexto(fuente, mensajeUsuario, salida.raiz(), generado.formato(), resultado));
        return new Cierre(resultado, null);
    }

    /**
     * Valida y, mientras haya errores duros, pide un patch (máximo MAX_RONDAS rondas). Si el
     * patch no sirve y se permite, regenera una sola vez en todo el trabajo y empieza de cero
     * las rondas; si no, se queda con lo que hay y sus errores.
     */
    private Salida validarYCorregir(Trabajo t, List<MensajeLlm> base, Generado inicial, FuenteNelse fuente,
            boolean permiteRegenerar) {
        Medicion m = t.medicion();
        ObjectNode raiz = inicial.raiz();
        // La salida cruda se reenvía tal cual mientras el JSON no cambie: así su prefijo sigue igual.
        String actual = inicial.crudo();
        int rondas = 0;
        ResultadoImportacion validacion = validar(t, raiz, inicial.formato(), fuente);

        while (!validacion.erroresDuros().isEmpty() && rondas < MAX_RONDAS) {
            rondas++;
            m.rondas++;
            t.fase(Fase.CORRIGIENDO, 80 + 8 * (rondas - 1));
            List<MensajeLlm> conversacion = new ArrayList<>(base);
            conversacion.add(new MensajeLlm("assistant", actual != null ? actual : serializar(raiz)));
            conversacion.add(new MensajeLlm("user", prompt.correccion() + lineas(validacion)));

            ObjectNode corregido = aplicarPatch(raiz, llamar(t, conversacion, esfuerzoCorregir, null));
            if (corregido != null) {
                raiz = corregido;
                actual = null;
                validacion = validar(t, raiz, inicial.formato(), fuente);
                continue;
            }
            if (!permiteRegenerar || m.regeneraciones > 0) {
                break;
            }
            m.regeneraciones++;
            Generado otro = pedirGeneracion(t, base);
            if (otro == null) {
                break;
            }
            raiz = otro.raiz();
            actual = otro.crudo();
            rondas = 0;
            validacion = validar(t, raiz, otro.formato(), fuente);
        }
        return new Salida(raiz, validacion);
    }

    /** Una línea "- campo: mensaje" por error duro, debajo del encabezado del turno de corrección. */
    private static String lineas(ResultadoImportacion validacion) {
        StringBuilder sb = new StringBuilder();
        validacion.erroresDuros().forEach((campo, mensaje) -> sb.append("- ").append(campo).append(": ")
                .append(mensaje).append('\n'));
        return sb.toString();
    }

    /** La generación completa; null si la salida no se puede usar (truncada o sin JSON de cuestionario). */
    private Generado pedirGeneracion(Trabajo t, List<MensajeLlm> base) {
        t.fase(Fase.GENERANDO, 10);
        RespuestaLlm r = llamar(t, base, esfuerzoGenerar, p -> t.progreso(10
                + Math.min(60, (int) ((long) p.caracteresContenido() * 60 / CARACTERES_ESPERADOS))));
        if (!utilizable(r)) {
            return null;
        }
        try {
            LectorJsonTolerante.Entrada entrada = LectorJsonTolerante.leer(r.contenido(), om);
            ObjectNode raiz = om.createObjectNode();
            raiz.set("cuestionario", entrada.cuestionario());
            if (entrada.analisis() != null) {
                raiz.set("analisis", entrada.analisis());
            }
            return new Generado(raiz, r.contenido(), entrada.formato());
        } catch (AppException e) {
            return null;
        }
    }

    private ResultadoImportacion validar(Trabajo t, ObjectNode raiz, String formato, FuenteNelse fuente) {
        t.fase(Fase.VALIDANDO, 75);
        comprobar(t);
        JsonNode analisis = raiz.get("analisis");
        return validador.validar(new LectorJsonTolerante.Entrada(raiz.get("cuestionario"),
                analisis != null && analisis.isObject() ? analisis : null, formato), fuente);
    }

    // ---------------------------------------------------------------- Patch

    /**
     * Aplica la respuesta del modelo a la raíz. null si no sirve: truncada, ilegible, vacía
     * ({"patch":[]} = "no se puede con un patch"), que no aplica o que deja de ser un cuestionario.
     */
    private ObjectNode aplicarPatch(ObjectNode raiz, RespuestaLlm r) {
        if (!utilizable(r)) {
            return null;
        }
        try {
            JsonNode patch = om.readTree(r.contenido());
            JsonNode operaciones = patch.get("patch");
            if (operaciones == null || !operaciones.isArray() || operaciones.isEmpty()) {
                return null;
            }
            JsonNode aplicado = AplicadorJsonPatch.aplicar(raiz, patch);
            return aplicado instanceof ObjectNode objeto && objeto.path("cuestionario").isObject() ? objeto : null;
        } catch (JsonProcessingException | PatchInvalidoException e) {
            return null;
        }
    }

    /** Salida completa: no cortada por el tope de tokens y con contenido. */
    private static boolean utilizable(RespuestaLlm r) {
        return r != null && r.contenido() != null && !r.contenido().isBlank()
                && !"length".equals(r.finishReason());
    }

    // ---------------------------------------------------------------- Refinamiento

    /**
     * El ajuste: el modelo ve el prefijo y el mensaje originales, su propio JSON y las respuestas
     * de la persona, y contesta con un patch. Las respuestas cuentan como fuente PROFESOR al
     * validar. Se conserva lo anterior si el patch no sirve o si el resultado queda con más
     * errores duros que antes.
     */
    private Cierre refinar(Trabajo t, Contexto c, SolicitudRefinamiento solicitud) {
        t.fase(Fase.REFINANDO, 5);
        Medicion m = t.medicion();
        List<MensajeLlm> base = base(c.mensajeUsuario());
        List<MensajeLlm> conversacion = new ArrayList<>(base);
        conversacion.add(new MensajeLlm("assistant", serializar(c.raiz())));
        conversacion.add(new MensajeLlm("user", prompt.refinamiento() + solicitud.paraElModelo()));

        ObjectNode refinado = aplicarPatch(c.raiz(), llamar(t, conversacion, esfuerzoGenerar, null));
        if (refinado == null) {
            return new Cierre(c.previo(), AVISO_REFINAMIENTO);
        }

        String profesor = c.fuente().profesor() == null ? "" : c.fuente().profesor() + "\n\n";
        FuenteNelse fuente = new FuenteNelse(c.fuente().documento(), profesor + solicitud.comoFuente(),
                c.fuente().paginasImagen());
        Salida salida = validarYCorregir(t, base, new Generado(refinado, null, c.formato()), fuente, false);

        ResultadoImportacion v = salida.validacion();
        if (v.cuestionario() == null || v.erroresDuros().size() > c.previo().erroresDuros().size()) {
            return new Cierre(c.previo(), AVISO_REFINAMIENTO);
        }
        return new Cierre(resultado(m, v), null);
    }

    // ---------------------------------------------------------------- Apoyos

    /** Prefijo fijo y mensaje de usuario: lo que no cambia entre la generación, las correcciones y el ajuste. */
    private List<MensajeLlm> base(String mensajeUsuario) {
        return List.of(new MensajeLlm("system", prompt.prefijoNelse()), new MensajeLlm("user", mensajeUsuario));
    }

    /** Igual que ia-eval/eval.py (mensaje_usuario): así es como se evaluó el prompt. */
    static String mensajeUsuario(String documento, String instrucciones) {
        String usuario = documento == null || documento.isBlank() ? "" : "DOCUMENTO:\n" + documento + "\n\n";
        return usuario + "PROFESOR:\n"
                + (instrucciones == null || instrucciones.isBlank() ? "(sin instrucciones)" : instrucciones);
    }

    private RespuestaLlm llamar(Trabajo t, List<MensajeLlm> mensajes, String esfuerzo,
            Consumer<ProgresoLlm> progreso) {
        comprobar(t);
        RespuestaLlm r = llm.completar(new PeticionLlm(mensajes, esfuerzo, maxSalida, true), progreso);
        if (r != null) {
            t.medicion().sumar(r.uso());
        }
        comprobar(t);
        return r;
    }

    private String serializar(ObjectNode raiz) {
        try {
            return om.writeValueAsString(raiz);
        } catch (JsonProcessingException e) {
            // Un árbol ya parseado siempre se escribe; si no, es un fallo interno, no del modelo.
            throw new IllegalStateException("No se pudo serializar el JSON del trabajo", e);
        }
    }

    private static void comprobar(Trabajo t) {
        if (t.cancelado()) {
            throw new Cancelado();
        }
    }

    private ResultadoNelse resultado(Medicion m, ResultadoImportacion v) {
        return new ResultadoNelse(v.cuestionario(), v.analisis(), v.marcas(), v.erroresDuros(),
                m.metricas(prompt.versionPrompt()));
    }
}
