package com.adela.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import com.adela.dto.ApiError;
import com.adela.exceptions.ErrorCode;
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
 * No es un @Component: Spring Boot registraría también cualquier Filter bean en
 * el contenedor de servlets y lo aplicaría fuera de la cadena de seguridad.
 */
public class LimiteTasaFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LimiteTasaFilter.class);

    static final String PREFIJO = "/api/publico/";

    static final int LECTURAS_POR_MINUTO = 600;

    static final int ESCRITURAS_POR_MINUTO = 120;

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
        int limite = lectura ? LECTURAS_POR_MINUTO : ESCRITURAS_POR_MINUTO;
        String clave = request.getRemoteAddr() + (lectura ? "|L" : "|E");
        long ahora = clock.millis();
        long minuto = ahora / 60_000;

        if (ventanas.size() > MAX_CLAVES) {
            ventanas.values().removeIf(v -> v.minuto() < minuto);
        }
        Ventana ventana = ventanas.merge(clave, new Ventana(minuto, 1),
                (actual, nueva) -> actual.minuto() == minuto ? new Ventana(minuto, actual.cuenta() + 1) : nueva);

        if (ventana.cuenta() > limite) {
            long segundos = 60 - (ahora / 1000) % 60;
            rechazar(request, response, segundos);
            return;
        }
        filterChain.doFilter(request, response);
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
