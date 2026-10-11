package com.adela.ia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Cliente de un endpoint compatible con OpenAI ({@code POST {base-url}/chat/completions}),
 * con respuesta en streaming SSE. Solo usa {@code java.net.http}.
 *
 * Se pide siempre streaming: con razonamiento alto el modelo puede tardar minutos sin
 * emitir nada y una respuesta única parecería un cuelgue; el stream mantiene viva la
 * conexión y permite informar el avance.
 *
 * Nunca se registran ni se incluyen en los mensajes de error la llave, el contenido
 * de los mensajes ni el cuerpo de la respuesta de error: pueden llevar el documento
 * de la persona o eco de la petición.
 */
@Component
public class ClienteLlmHttp implements ClienteLlm {

    private static final Logger logger = LoggerFactory.getLogger(ClienteLlmHttp.class);

    private static final Duration TIEMPO_CONEXION = Duration.ofSeconds(10);
    /** El avance se informa a lo sumo cada tanto: el sondeo del frontend es de 2 s, no hace falta más. */
    private static final long INTERVALO_PROGRESO_NS = TimeUnit.MILLISECONDS.toNanos(250);

    /**
     * El timeout de HttpRequest vence al recibir las cabeceras, no cuenta la lectura del
     * cuerpo. Este reloj cierra el stream cuando se agota el tiempo total, para que un
     * proveedor que se queda callado a mitad de respuesta no deje el hilo colgado.
     */
    private static final ScheduledExecutorService RELOJ = relojDemonio();

    private final URI endpoint;
    private final String modelo;
    private final String apiKey;
    private final Duration timeout;
    private final boolean habilitada;
    private final ObjectMapper om;
    private final HttpClient http;

    @Autowired
    public ClienteLlmHttp(@Value("${adela.ia.base-url}") String baseUrl, @Value("${adela.ia.modelo}") String modelo,
            @Value("${adela.ia.api-key}") String apiKey, @Value("${adela.ia.timeout}") Duration timeout,
            @Value("${adela.ia.habilitada}") boolean habilitada, ObjectMapper om) {
        this(baseUrl, modelo, apiKey, timeout, habilitada, om,
                HttpClient.newBuilder().connectTimeout(TIEMPO_CONEXION).build());
    }

    /** Con el HttpClient a la vista para poder probar contra un servidor local. */
    ClienteLlmHttp(String baseUrl, String modelo, String apiKey, Duration timeout, boolean habilitada,
            ObjectMapper om, HttpClient http) {
        this.endpoint = URI.create(baseUrl.strip().replaceAll("/+$", "") + "/chat/completions");
        this.modelo = modelo;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.timeout = timeout;
        this.habilitada = habilitada;
        this.om = om;
        this.http = http;
    }

    @Override
    public RespuestaLlm completar(PeticionLlm peticion, Consumer<ProgresoLlm> progreso) {
        if (!habilitada || apiKey.isEmpty()) {
            throw new AppException(ErrorCode.IA_DESHABILITADA, "El asistente de IA no está habilitado.");
        }
        long inicio = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo(peticion))).build();

        AtomicBoolean vencido = new AtomicBoolean(false);
        try {
            HttpResponse<InputStream> respuesta = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream cuerpo = respuesta.body()) {
                int status = respuesta.statusCode();
                if (status / 100 != 2) {
                    logger.warn("El proveedor de IA respondió HTTP {}", status);
                    throw new AppException(ErrorCode.IA_NO_DISPONIBLE,
                            "El servicio de IA respondió con un error (HTTP " + status + "). Inténtalo de nuevo más tarde.");
                }
                long restanteMs = Math.max(1, timeout.toMillis() - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
                var vigilante = RELOJ.schedule(() -> {
                    vencido.set(true);
                    cerrar(cuerpo);
                }, restanteMs, TimeUnit.MILLISECONDS);
                try {
                    return leerStream(cuerpo, progreso, inicio);
                } finally {
                    vigilante.cancel(false);
                }
            }
        } catch (IOException e) {
            // HttpTimeoutException, ConnectException, corte del stream o cierre del vigilante.
            logger.warn("Falló la llamada al proveedor de IA: {}", e.getClass().getSimpleName());
            throw new AppException(ErrorCode.IA_NO_DISPONIBLE, vencido.get() || e instanceof HttpTimeoutException
                    ? "El servicio de IA tardó demasiado en responder. Inténtalo de nuevo."
                    : "No se pudo conectar con el servicio de IA. Inténtalo de nuevo más tarde.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.IA_NO_DISPONIBLE, "La llamada al servicio de IA fue cancelada.");
        }
    }

    private String cuerpo(PeticionLlm p) {
        ObjectNode raiz = om.createObjectNode();
        raiz.put("model", modelo);
        ArrayNode mensajes = raiz.putArray("messages");
        p.mensajes().forEach(m -> mensajes.addObject().put("role", m.rol()).put("content", m.contenido()));
        raiz.put("stream", true);
        raiz.putObject("stream_options").put("include_usage", true);
        if (p.esfuerzo() != null) {
            raiz.put("reasoning_effort", p.esfuerzo());
        }
        raiz.put("max_tokens", p.maxTokens());
        if (p.json()) {
            raiz.putObject("response_format").put("type", "json_object");
        }
        try {
            return om.writeValueAsString(raiz);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo armar la petición a la IA", e);
        }
    }

    private RespuestaLlm leerStream(InputStream cuerpo, Consumer<ProgresoLlm> progreso, long inicio)
            throws IOException {
        StringBuilder contenido = new StringBuilder();
        int razonamiento = 0;
        String finishReason = null;
        UsoLlm uso = new UsoLlm(0, 0, 0, 0, 0);
        long ultimoAviso = inicio;

        BufferedReader lector = new BufferedReader(new InputStreamReader(cuerpo, StandardCharsets.UTF_8));
        String linea;
        while ((linea = lector.readLine()) != null) {
            // Líneas en blanco (separador de eventos) y comentarios ": keep-alive" no llevan datos.
            if (!linea.startsWith("data:")) {
                continue;
            }
            String datos = linea.substring(5).strip();
            if (datos.equals("[DONE]")) {
                break;
            }
            if (datos.isEmpty()) {
                continue;
            }
            JsonNode fragmento;
            try {
                fragmento = om.readTree(datos);
            } catch (JsonProcessingException e) {
                throw new AppException(ErrorCode.IA_RESPUESTA_INVALIDA,
                        "La respuesta de la IA llegó mal formada. Inténtalo de nuevo.");
            }
            JsonNode eleccion = fragmento.path("choices").path(0);
            JsonNode delta = eleccion.path("delta");
            if (delta.path("content").isTextual()) {
                contenido.append(delta.path("content").asText());
            }
            if (delta.path("reasoning_content").isTextual()) {
                razonamiento += delta.path("reasoning_content").asText().length();
            }
            if (eleccion.path("finish_reason").isTextual()) {
                finishReason = eleccion.path("finish_reason").asText();
            }
            if (fragmento.path("usage").isObject()) {
                uso = uso(fragmento.path("usage"));
            }
            long ahora = System.nanoTime();
            if (progreso != null && ahora - ultimoAviso >= INTERVALO_PROGRESO_NS) {
                ultimoAviso = ahora;
                progreso.accept(new ProgresoLlm(contenido.length(), razonamiento));
            }
        }
        if (contenido.toString().isBlank()) {
            throw new AppException(ErrorCode.IA_RESPUESTA_INVALIDA, "La IA no devolvió contenido. Inténtalo de nuevo.");
        }
        if (progreso != null) {
            progreso.accept(new ProgresoLlm(contenido.length(), razonamiento));
        }
        Duration duracion = Duration.ofNanos(System.nanoTime() - inicio);
        logger.debug("Llamada a la IA: {} ms, finish={}, {}", duracion.toMillis(), finishReason, uso);
        return new RespuestaLlm(contenido.toString(), finishReason, uso, duracion);
    }

    private static UsoLlm uso(JsonNode u) {
        return new UsoLlm(u.path("prompt_tokens").asInt(0), u.path("completion_tokens").asInt(0),
                u.path("prompt_cache_hit_tokens").asInt(0), u.path("prompt_cache_miss_tokens").asInt(0),
                u.path("completion_tokens_details").path("reasoning_tokens").asInt(0));
    }

    private static void cerrar(InputStream cuerpo) {
        try {
            cuerpo.close();
        } catch (IOException e) {
            // Ya se está abortando: lo que importa es que el hilo lector despierte.
        }
    }

    private static ScheduledExecutorService relojDemonio() {
        ScheduledThreadPoolExecutor reloj = new ScheduledThreadPoolExecutor(1, tarea -> {
            Thread hilo = new Thread(tarea, "cliente-llm-reloj");
            hilo.setDaemon(true);
            return hilo;
        });
        reloj.setRemoveOnCancelPolicy(true);
        return reloj;
    }
}
