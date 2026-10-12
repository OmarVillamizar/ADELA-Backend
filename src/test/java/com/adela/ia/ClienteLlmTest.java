package com.adela.ia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.ia.ClienteLlm.MensajeLlm;
import com.adela.ia.ClienteLlm.PeticionLlm;
import com.adela.ia.ClienteLlm.ProgresoLlm;
import com.adela.ia.ClienteLlm.RespuestaLlm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * El cliente se prueba contra un servidor falso del JDK que habla SSE como lo hace
 * el proveedor: sin red externa, sin llave real y sin contexto de Spring.
 */
class ClienteLlmTest {

    private static final String LLAVE = "llave-secreta-de-prueba";
    private static final ObjectMapper OM = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private HttpServer servidor;

    @AfterEach
    void apagar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    // ---------------------------------------------------------------- Ayudas

    /** Arranca el servidor con el handler dado y devuelve la base-url (con un prefijo /v1, como en la práctica). */
    private String servir(HttpHandler handler) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        // Un hilo por petición: un handler dormido no debe bloquear al siguiente.
        servidor.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        servidor.createContext("/", handler);
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1";
    }

    private static ClienteLlmHttp cliente(String baseUrl, String llave, Duration timeout, boolean habilitada) {
        return new ClienteLlmHttp(baseUrl, "modelo-falso", llave, timeout, habilitada, OM,
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
    }

    private static PeticionLlm peticion(boolean json) {
        return new PeticionLlm(
                List.of(new MensajeLlm("system", "Eres un asistente."), new MensajeLlm("user", "Hola \"mundo\"")),
                "high", 1234, json);
    }

    private static void sse(HttpExchange ex, String... eventos) throws IOException {
        ex.getResponseHeaders().add("Content-Type", "text/event-stream");
        ex.sendResponseHeaders(200, 0);
        try (OutputStream os = ex.getResponseBody()) {
            for (String evento : eventos) {
                os.write((evento + "\n\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }
    }

    private static String contenido(String texto) {
        return "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":" + texto + "},\"finish_reason\":null}]}";
    }

    private static AppException falla(ClienteLlmHttp cliente) {
        return assertThrows(AppException.class, () -> cliente.completar(peticion(false), null));
    }

    // ---------------------------------------------------------------- Petición

    @Test
    @DisplayName("La petición lleva modelo, mensajes, stream, esfuerzo, tope, formato JSON y la llave")
    void cuerpoDeLaPeticion() throws Exception {
        AtomicReference<JsonNode> cuerpo = new AtomicReference<>();
        AtomicReference<String> autorizacion = new AtomicReference<>();
        AtomicReference<String> ruta = new AtomicReference<>();
        AtomicReference<String> metodo = new AtomicReference<>();
        String base = servir(ex -> {
            metodo.set(ex.getRequestMethod());
            ruta.set(ex.getRequestURI().getPath());
            autorizacion.set(ex.getRequestHeaders().getFirst("Authorization"));
            cuerpo.set(OM.readTree(ex.getRequestBody().readAllBytes()));
            sse(ex, contenido("\"ok\""), "data: [DONE]");
        });

        // La barra final de la base-url no debe duplicar el separador.
        cliente(base + "/", LLAVE, TIMEOUT, true).completar(peticion(true), null);

        assertEquals("POST", metodo.get());
        assertEquals("/v1/chat/completions", ruta.get());
        assertEquals("Bearer " + LLAVE, autorizacion.get());
        JsonNode json = cuerpo.get();
        assertEquals("modelo-falso", json.path("model").asText());
        assertTrue(json.path("stream").asBoolean());
        assertTrue(json.path("stream_options").path("include_usage").asBoolean());
        assertEquals("high", json.path("reasoning_effort").asText());
        assertEquals(1234, json.path("max_tokens").asInt());
        assertEquals("json_object", json.path("response_format").path("type").asText());
        assertEquals(2, json.path("messages").size());
        assertEquals("system", json.path("messages").path(0).path("role").asText());
        assertEquals("Eres un asistente.", json.path("messages").path(0).path("content").asText());
        assertEquals("user", json.path("messages").path(1).path("role").asText());
        assertEquals("Hola \"mundo\"", json.path("messages").path(1).path("content").asText());
    }

    @Test
    @DisplayName("Un mensaje de solo texto va con el contenido como cadena, byte por byte como antes de las imágenes")
    void cuerpoDeSoloTextoNoCambia() throws Exception {
        AtomicReference<String> cuerpo = new AtomicReference<>();
        String base = servir(ex -> {
            cuerpo.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sse(ex, contenido("\"ok\""), "data: [DONE]");
        });

        cliente(base, LLAVE, TIMEOUT, true).completar(peticion(true), null);

        // Si esto cambia, el prefijo que el proveedor tiene en caché deja de coincidir.
        assertEquals("{\"model\":\"modelo-falso\",\"messages\":[{\"role\":\"system\",\"content\":\"Eres un asistente.\"},"
                + "{\"role\":\"user\",\"content\":\"Hola \\\"mundo\\\"\"}],\"stream\":true,"
                + "\"stream_options\":{\"include_usage\":true},\"reasoning_effort\":\"high\",\"max_tokens\":1234,"
                + "\"response_format\":{\"type\":\"json_object\"}}", cuerpo.get());
    }

    @Test
    @DisplayName("Con imágenes el contenido es un arreglo: el texto primero y cada página como data URI JPEG, en orden")
    void cuerpoConImagenes() throws Exception {
        byte[] primera = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3 };
        byte[] segunda = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xFB, (byte) 0xFF, 0, 9 };
        AtomicReference<JsonNode> cuerpo = new AtomicReference<>();
        String base = servir(ex -> {
            cuerpo.set(OM.readTree(ex.getRequestBody().readAllBytes()));
            sse(ex, contenido("\"ok\""), "data: [DONE]");
        });
        PeticionLlm peticion = new PeticionLlm(List.of(new MensajeLlm("system", "Eres un asistente."),
                new MensajeLlm("user", "DOCUMENTO:\ntexto", List.of(primera, segunda))), "high", 1234, true);

        cliente(base, LLAVE, TIMEOUT, true).completar(peticion, null);

        JsonNode mensajes = cuerpo.get().path("messages");
        assertEquals(2, mensajes.size());
        // El mensaje sin imágenes conserva su forma de cadena aunque otro del mismo pedido las lleve.
        assertEquals("Eres un asistente.", mensajes.path(0).path("content").asText());
        JsonNode partes = mensajes.path(1).path("content");
        assertTrue(partes.isArray());
        assertEquals(3, partes.size());
        assertEquals("text", partes.path(0).path("type").asText());
        assertEquals("DOCUMENTO:\ntexto", partes.path(0).path("text").asText());
        byte[][] enviadas = { primera, segunda };
        for (int i = 0; i < enviadas.length; i++) {
            JsonNode parte = partes.path(i + 1);
            assertEquals("image_url", parte.path("type").asText());
            String url = parte.path("image_url").path("url").asText();
            assertTrue(url.startsWith("data:image/jpeg;base64,"), "página " + (i + 1));
            assertArrayEquals(enviadas[i], Base64.getDecoder().decode(url.substring("data:image/jpeg;base64,".length())));
        }
    }

    @Test
    @DisplayName("Sin json no se pide response_format")
    void sinFormatoJson() throws Exception {
        AtomicReference<JsonNode> cuerpo = new AtomicReference<>();
        String base = servir(ex -> {
            cuerpo.set(OM.readTree(ex.getRequestBody().readAllBytes()));
            sse(ex, contenido("\"ok\""), "data: [DONE]");
        });

        cliente(base, LLAVE, TIMEOUT, true).completar(peticion(false), null);

        assertFalse(cuerpo.get().has("response_format"));
    }

    // ---------------------------------------------------------------- Stream

    @Test
    @DisplayName("Concatena el contenido de los fragmentos, ignora el razonamiento y los comentarios, y lee el uso")
    void parseaElStream() throws Exception {
        String base = servir(ex -> sse(ex,
                ": keep-alive",
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":null,\"reasoning_content\":\"pienso\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\" mucho\"}}]}",
                contenido("\"{\\\"a\\\":\""),
                contenido("\"1,\\\"b\\\":\""),
                contenido("\"\\\"ñ\\\"}\""),
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":40,"
                        + "\"prompt_cache_hit_tokens\":80,\"prompt_cache_miss_tokens\":20,"
                        + "\"completion_tokens_details\":{\"reasoning_tokens\":25}}}",
                "data: [DONE]",
                "data: {\"choices\":[{\"delta\":{\"content\":\"NO SE LEE\"}}]}"));

        List<ProgresoLlm> avances = new CopyOnWriteArrayList<>();
        RespuestaLlm respuesta = cliente(base, LLAVE, TIMEOUT, true).completar(peticion(true), avances::add);

        assertEquals("{\"a\":1,\"b\":\"ñ\"}", respuesta.contenido());
        assertEquals("stop", respuesta.finishReason());
        assertEquals(100, respuesta.uso().promptTokens());
        assertEquals(40, respuesta.uso().completionTokens());
        assertEquals(80, respuesta.uso().promptCacheHit());
        assertEquals(20, respuesta.uso().promptCacheMiss());
        assertEquals(25, respuesta.uso().reasoningTokens());
        assertNotNull(respuesta.duracion());
        assertFalse(respuesta.duracion().isNegative());
        // Siempre hay un aviso final con los totales, aunque el estrangulador haya omitido los intermedios.
        assertFalse(avances.isEmpty());
        assertEquals(new ProgresoLlm(respuesta.contenido().length(), "pienso mucho".length()),
                avances.get(avances.size() - 1));
    }

    @Test
    @DisplayName("Un uso incompleto o ausente deja los campos en 0")
    void usoIncompleto() throws Exception {
        String base = servir(ex -> sse(ex, contenido("\"x\""),
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3}}", "data: [DONE]"));

        RespuestaLlm respuesta = cliente(base, LLAVE, TIMEOUT, true).completar(peticion(false), null);

        assertEquals(7, respuesta.uso().promptTokens());
        assertEquals(3, respuesta.uso().completionTokens());
        assertEquals(0, respuesta.uso().promptCacheHit());
        assertEquals(0, respuesta.uso().promptCacheMiss());
        assertEquals(0, respuesta.uso().reasoningTokens());
        assertNull(respuesta.finishReason());
    }

    @Test
    @DisplayName("Un stream que termina sin [DONE] pero con contenido igualmente se acepta")
    void sinDone() throws Exception {
        String base = servir(ex -> sse(ex, contenido("\"abc\"")));

        assertEquals("abc", cliente(base, LLAVE, TIMEOUT, true).completar(peticion(false), null).contenido());
    }

    // ---------------------------------------------------------------- Errores

    @Test
    @DisplayName("HTTP 500 y 429 son IA_NO_DISPONIBLE, con el status y sin la llave ni el cuerpo del error")
    void errorHttp() throws Exception {
        AtomicInteger status = new AtomicInteger(500);
        String base = servir(ex -> {
            byte[] cuerpo = ("{\"error\":\"detalle interno " + LLAVE + "\"}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status.get(), cuerpo.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(cuerpo);
            }
        });
        ClienteLlmHttp cliente = cliente(base, LLAVE, TIMEOUT, true);

        for (int codigo : new int[] { 500, 429 }) {
            status.set(codigo);
            AppException e = falla(cliente);
            assertEquals(ErrorCode.IA_NO_DISPONIBLE, e.getCode());
            assertTrue(e.getMessage().contains("HTTP " + codigo), e.getMessage());
            assertFalse(e.getMessage().contains(LLAVE));
            assertFalse(e.getMessage().contains("detalle interno"));
        }
    }

    @Test
    @DisplayName("Sin servidor escuchando es IA_NO_DISPONIBLE")
    void sinConexion() throws Exception {
        String base = servir(ex -> ex.sendResponseHeaders(204, -1));
        servidor.stop(0);

        assertEquals(ErrorCode.IA_NO_DISPONIBLE, falla(cliente(base, LLAVE, TIMEOUT, true)).getCode());
    }

    @Test
    @DisplayName("Deshabilitada, o con la llave vacía, es IA_DESHABILITADA y no toca la red")
    void deshabilitada() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();
        String base = servir(ex -> {
            llamadas.incrementAndGet();
            sse(ex, contenido("\"x\""), "data: [DONE]");
        });

        assertEquals(ErrorCode.IA_DESHABILITADA, falla(cliente(base, LLAVE, TIMEOUT, false)).getCode());
        assertEquals(ErrorCode.IA_DESHABILITADA, falla(cliente(base, "", TIMEOUT, true)).getCode());
        assertEquals(ErrorCode.IA_DESHABILITADA, falla(cliente(base, "   ", TIMEOUT, true)).getCode());
        assertEquals(ErrorCode.IA_DESHABILITADA, falla(cliente(base, null, TIMEOUT, true)).getCode());
        assertEquals(0, llamadas.get());
    }

    @Test
    @DisplayName("Un fragmento con JSON mal formado es IA_RESPUESTA_INVALIDA")
    void fragmentoMalFormado() throws Exception {
        String base = servir(ex -> sse(ex, contenido("\"abc\""), "data: {no es json", "data: [DONE]"));

        AppException e = falla(cliente(base, LLAVE, TIMEOUT, true));

        assertEquals(ErrorCode.IA_RESPUESTA_INVALIDA, e.getCode());
        assertFalse(e.getMessage().contains("no es json"));
    }

    @Test
    @DisplayName("Un stream sin contenido (vacío, solo [DONE] o solo razonamiento) es IA_RESPUESTA_INVALIDA")
    void sinContenido() throws Exception {
        List<String[]> flujos = new ArrayList<>();
        flujos.add(new String[] {});
        flujos.add(new String[] { "data: [DONE]" });
        flujos.add(new String[] {
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"solo pienso\"}}]}", "data: [DONE]" });
        flujos.add(new String[] { contenido("\"   \""), "data: [DONE]" });
        AtomicInteger turno = new AtomicInteger();
        String base = servir(ex -> sse(ex, flujos.get(turno.getAndIncrement())));
        ClienteLlmHttp cliente = cliente(base, LLAVE, TIMEOUT, true);

        for (int i = 0; i < flujos.size(); i++) {
            assertEquals(ErrorCode.IA_RESPUESTA_INVALIDA, falla(cliente).getCode(), "flujo " + i);
        }
    }

    @Test
    @DisplayName("Si el servidor tarda en contestar más que el timeout es IA_NO_DISPONIBLE")
    void timeoutAntesDeLasCabeceras() throws Exception {
        String base = servir(ex -> {
            try {
                Thread.sleep(3000);
                sse(ex, contenido("\"tarde\""), "data: [DONE]");
            } catch (InterruptedException | IOException e) {
                // El cliente ya se fue.
            }
        });
        long inicio = System.nanoTime();

        AppException e = falla(cliente(base, LLAVE, Duration.ofMillis(400), true));

        assertEquals(ErrorCode.IA_NO_DISPONIBLE, e.getCode());
        assertTrue(Duration.ofNanos(System.nanoTime() - inicio).toMillis() < 2500, "No respetó el timeout");
    }

    @Test
    @DisplayName("Si el stream se queda callado a mitad de respuesta, el timeout total lo corta")
    void timeoutDuranteElStream() throws Exception {
        String base = servir(ex -> {
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, 0);
            try {
                OutputStream os = ex.getResponseBody();
                os.write((contenido("\"parcial\"") + "\n\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                Thread.sleep(5000);
            } catch (InterruptedException | IOException e) {
                // El cliente ya se fue.
            }
        });
        long inicio = System.nanoTime();

        AppException e = falla(cliente(base, LLAVE, Duration.ofMillis(600), true));

        assertEquals(ErrorCode.IA_NO_DISPONIBLE, e.getCode());
        assertTrue(Duration.ofNanos(System.nanoTime() - inicio).toMillis() < 3500, "No cortó el stream detenido");
    }

    @Test
    @DisplayName("Una interrupción del hilo es IA_NO_DISPONIBLE y deja la bandera de interrupción puesta")
    void interrupcion() throws Exception {
        String base = servir(ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                // El cliente ya se fue.
            }
        });
        ClienteLlmHttp cliente = cliente(base, LLAVE, TIMEOUT, true);

        Thread.currentThread().interrupt();
        try {
            AppException e = falla(cliente);
            assertEquals(ErrorCode.IA_NO_DISPONIBLE, e.getCode());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // no contaminar las demás pruebas
        }
    }
}
