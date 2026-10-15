package com.adela.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El state del login OAuth: nulo o incompleto da 400, nunca NullPointerException. */
class StateOAuthTest {

    @Test
    @DisplayName("Un state nulo da un mapa vacío")
    void stateNulo() {
        assertTrue(SecurityConfig.leerState(null).isEmpty());
    }

    @Test
    @DisplayName("Faltantes, vacíos y 'default' no aparecen")
    void incompleto() {
        Map<String, String> p = SecurityConfig.leerState("userType=default&redirect_to=&otro");
        assertTrue(p.isEmpty());
    }

    @Test
    @DisplayName("Un '&' codificado dentro de redirect_to no sobrescribe userType")
    void ampersandCodificado() {
        Map<String, String> p = SecurityConfig
                .leerState("userType=estudiante&redirect_to=https%3A%2F%2Fadela.app%2Flogin%3Fa%3D1%26userType%3Dprofesor");

        assertEquals("estudiante", p.get("userType"));
        assertEquals("https://adela.app/login?a=1&userType=profesor", p.get("redirect_to"));
    }
}
