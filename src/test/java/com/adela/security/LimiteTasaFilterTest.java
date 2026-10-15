package com.adela.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Protege el límite de las rutas públicas: es lo único que frena a un script que
 * llene una cápsula de respuestas falsas o recorra códigos de resultado.
 */
class LimiteTasaFilterTest {

    /** Reloj que el test puede adelantar. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-10-06T12:00:10Z");

        void adelantar(long segundos) {
            ahora = ahora.plusSeconds(segundos);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private RelojManual reloj;
    private LimiteTasaFilter filtro;

    @BeforeEach
    void preparar() {
        reloj = new RelojManual();
        filtro = new LimiteTasaFilter(new ObjectMapper().registerModule(new JavaTimeModule()), reloj);
    }

    private MockHttpServletResponse enviar(String metodo, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    /** Como enviar, pero el controlador responde 404: un código que no existe. */
    private MockHttpServletResponse fallar(String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                res.setStatus(404);
            }
        }));
        return response;
    }

    private void agotarFallos(String ip) throws Exception {
        for (int i = 0; i < LimiteTasaFilter.FALLOS_POR_MINUTO; i++) {
            assertEquals(404, fallar("/api/publico/capsulas/ZZZZZ" + i, ip).getStatus());
        }
    }

    private void agotarEscrituras(String ip) throws Exception {
        for (int i = 0; i < LimiteTasaFilter.ESCRITURAS_POR_MINUTO; i++) {
            assertEquals(200, enviar("POST", "/api/publico/capsulas/X/respuestas", ip).getStatus());
        }
    }

    @Test
    @DisplayName("Superar el límite responde 429 con Retry-After y el formato de error de la API")
    void superarLimiteEs429() throws Exception {
        agotarEscrituras("10.0.0.1");

        MockHttpServletResponse r = enviar("POST", "/api/publico/capsulas/X/respuestas", "10.0.0.1");
        assertEquals(429, r.getStatus());
        assertEquals("50", r.getHeader("Retry-After"));
        assertTrue(r.getContentAsString().contains("DEMASIADAS_SOLICITUDES"));
    }

    @Test
    @DisplayName("Cada IP tiene su propio límite")
    void otraIpNoSeVeAfectada() throws Exception {
        agotarEscrituras("10.0.0.1");
        assertEquals(200, enviar("POST", "/api/publico/capsulas/X/respuestas", "10.0.0.2").getStatus());
    }

    @Test
    @DisplayName("Agotar las escrituras no bloquea las lecturas")
    void lecturasSeCuentanAparte() throws Exception {
        agotarEscrituras("10.0.0.1");
        assertEquals(200, enviar("GET", "/api/publico/resultados/X", "10.0.0.1").getStatus());
    }

    @Test
    @DisplayName("El minuto siguiente reinicia la cuenta")
    void nuevaVentanaReinicia() throws Exception {
        agotarEscrituras("10.0.0.1");
        reloj.adelantar(60);
        assertEquals(200, enviar("POST", "/api/publico/capsulas/X/respuestas", "10.0.0.1").getStatus());
    }

    @Test
    @DisplayName("Tras 20 códigos inexistentes la IP queda frenada en toda ruta pública")
    void fallosFrenanLaEnumeracion() throws Exception {
        agotarFallos("10.0.0.1");

        assertEquals(429, enviar("GET", "/api/publico/capsulas/ABC234", "10.0.0.1").getStatus());
        assertEquals(429, enviar("POST", "/api/publico/capsulas/ABC234/respuestas", "10.0.0.1").getStatus());
        assertEquals(200, enviar("GET", "/api/publico/capsulas/ABC234", "10.0.0.2").getStatus());
    }

    @Test
    @DisplayName("Los aciertos no cuentan como fallos y el minuto siguiente perdona")
    void aciertosNoCuentanYFallosCaducan() throws Exception {
        for (int i = 0; i < 100; i++) {
            assertEquals(200, enviar("GET", "/api/publico/capsulas/ABC234", "10.0.0.1").getStatus());
        }
        agotarFallos("10.0.0.1");
        reloj.adelantar(60);
        assertEquals(200, enviar("GET", "/api/publico/capsulas/ABC234", "10.0.0.1").getStatus());
    }

    @Test
    @DisplayName("Una cápsula admite un tope de respuestas por minuto aunque lleguen de muchas IP")
    void topePorCapsulaEntreIps() throws Exception {
        for (int i = 0; i < LimiteTasaFilter.RESPUESTAS_POR_CAPSULA_POR_MINUTO; i++) {
            String ip = "10.1." + (i / 100) + "." + (i % 100);
            assertEquals(200, enviar("POST", "/api/publico/capsulas/ABC234/respuestas", ip).getStatus());
        }

        // Variar mayúsculas o meter guiones no abre otro cupo: el código se normaliza.
        assertEquals(429, enviar("POST", "/api/publico/capsulas/abc-234/respuestas", "10.9.9.9").getStatus());
        assertEquals(200, enviar("POST", "/api/publico/capsulas/XYZ789/respuestas", "10.9.9.9").getStatus());
    }

    @Test
    @DisplayName("Las rutas autenticadas no se limitan")
    void rutasPrivadasNoSeLimitan() throws Exception {
        for (int i = 0; i <= LimiteTasaFilter.ESCRITURAS_POR_MINUTO; i++) {
            assertEquals(200, enviar("POST", "/api/capsulas", "10.0.0.1").getStatus());
        }
    }
}
