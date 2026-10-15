package com.adela.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SEC-09: un secreto JWT corto o mal formado impide arrancar, no el primer login. */
class JwtUtilTest {

    private static String base64DeBytes(int n) {
        return Base64.getEncoder().encodeToString(new byte[n]);
    }

    @Test
    @DisplayName("32 bytes o más arrancan")
    void suficiente() {
        assertDoesNotThrow(() -> JwtUtil.validar(base64DeBytes(32)));
        assertDoesNotThrow(() -> JwtUtil.validar(base64DeBytes(64)));
    }

    @Test
    @DisplayName("Menos de 32 bytes, vacío o nulo no arrancan")
    void corto() {
        assertThrows(IllegalStateException.class, () -> JwtUtil.validar(base64DeBytes(31)));
        assertThrows(IllegalStateException.class, () -> JwtUtil.validar(""));
        assertThrows(IllegalStateException.class, () -> JwtUtil.validar(null));
    }

    @Test
    @DisplayName("Un secreto que no es Base64 no arranca")
    void noBase64() {
        assertThrows(IllegalStateException.class, () -> JwtUtil.validar("esto no es base64!!"));
    }
}
