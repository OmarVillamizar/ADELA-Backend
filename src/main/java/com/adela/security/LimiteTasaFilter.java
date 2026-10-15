package com.adela.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import com.adela.dto.ApiError;
import com.adela.exceptions.ErrorCode;
import com.adela.services.CodigoAleatorio;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Límite de peticiones por IP para las rutas sin autenticación (/api/publico/**).
 * Sin él, un script podía llenar una cápsula de respuestas falsas o recorrer
 * códigos de resultado sin freno.
 *
 * Ventana fija de un minuto, en memoria: basta para una sola instancia. Los
 * límites son holgados porque un aula entera sale a internet por la misma IP.
 *
 * El código de cápsula es corto (6 caracteres, 31^6 ≈ 887 millones) para que se
 * dicte en clase; lo que impide recorrerlo es el límite de fallos: quien tiene
 * el código casi nunca recibe un 404 y quien enumera falla casi siempre. Con
 * 100 cápsulas abiertas, una IP tarda unos 300 días en acertar una.
 *
 * Además cada cápsula admite un tope de respuestas por minuto entre todas las
 * IP, para que repartir el envío de basura en muchas direcciones no sirva.
 *
 * No es un @Component: Spring Boot registraría también cualquier Filter bean en
 * el contenedor de servlets y lo aplicaría fuera de la cadena de seguridad.
 */
public class LimiteTasaFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LimiteTasaFilter.class);

    static final String PREFIJO = "/api/publico/";

    static final int LECTURAS_POR_MINUTO = 600;

    static final int ESCRITURAS_POR_MINUTO = 120;

    /** Códigos inexistentes (404) por IP; alcanzado el tope, toda ruta pública responde 429. */
    static final int FALLOS_POR_MINUTO = 20;

    /** Respuestas por cápsula sumando todas las IP: varias aulas a la vez caben de sobra. */
    static final int RESPUESTAS_POR_CAPSULA_POR_MINUTO = 300;

    private static final Pattern RESPONDER_CAPSULA = Pattern.compile("^/api/publico/capsulas/([^/]+)/respuestas$");

    /** Por encima de este tamaño se purgan las ventanas de minutos pasados. */
    private static final int MAX_CLAVES = 10_000;

    private record Ventana(long minuto, int cuenta) {
    }

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    private final Clock clock;

    public LimiteTasaFilter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // El preflight CORS no es una petición del usuario: no cuenta.
        return !request.getRequestURI().startsWith(PREFIJO) || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean lectura = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        String ip = request.getRemoteAddr();
        long ahora = clock.millis();
        long minuto = ahora / 60_000;
        long segundos = 60 - (ahora / 1000) % 60;

        if (ventanas.size() > MAX_CLAVES) {
            ventanas.values().removeIf(v -> v.minuto() < minuto);
        }

        if (cuenta(ip + "|F", minuto) >= FALLOS_POR_MINUTO
                || sumar(ip + (lectura ? "|L" : "|E"), minuto) > (lectura ? LECTURAS_POR_MINUTO : ESCRITURAS_POR_MINUTO)) {
            rechazar(request, response, segundos);
            return;
        }

        Matcher responder = RESPONDER_CAPSULA.matcher(request.getRequestURI());
        if (!lectura && responder.matches() && sumar("capsula:" + CodigoAleatorio.normalizar(responder.group(1)),
                minuto) > RESPUESTAS_POR_CAPSULA_POR_MINUTO) {
            rechazar(request, response, segundos);
            return;
        }

        filterChain.doFilter(request, response);

        if (response.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
            sumar(ip + "|F", minuto);
        }
    }

    /** Suma uno a la ventana del minuto actual y devuelve la cuenta. */
    private int sumar(String clave, long minuto) {
        return ventanas.merge(clave, new Ventana(minuto, 1),
                (actual, nueva) -> actual.minuto() == minuto ? new Ventana(minuto, actual.cuenta() + 1) : nueva)
                .cuenta();
    }

    private int cuenta(String clave, long minuto) {
        Ventana v = ventanas.get(clave);
        return v == null || v.minuto() != minuto ? 0 : v.cuenta();
    }

    /** Mismo cuerpo que GlobalExceptionHandler: un único formato de error en la API. */
    private void rechazar(HttpServletRequest request, HttpServletResponse response, long segundos)
            throws IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        logger.warn("[{}] Límite de tasa superado por {} en {}", traceId, request.getRemoteAddr(),
                request.getRequestURI());

        ErrorCode code = ErrorCode.DEMASIADAS_SOLICITUDES;
        ApiError body = new ApiError(Instant.now(clock), code.getStatus().value(), code.name(),
                "Demasiadas solicitudes. Espera un momento e inténtalo de nuevo.", null, traceId,
                request.getRequestURI());

        response.setStatus(code.getStatus().value());
        response.setHeader("Retry-After", String.valueOf(segundos));
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
