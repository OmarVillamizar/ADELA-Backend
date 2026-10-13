package com.adela.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * SUPERADMIN debe pasar todo hasRole('ADMINISTRADOR') sin tocar cada anotación.
 *
 * Contexto mínimo: solo seguridad de métodos y el bean de SecurityConfig, sin
 * base de datos ni credenciales de Google. Un test del bean aislado no bastaría:
 * lo que importa es que @PreAuthorize lo use de verdad.
 */
@SpringJUnitConfig(JerarquiaRolesTest.Config.class)
class JerarquiaRolesTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        Protegido protegido() {
            return new Protegido();
        }
    }

    static class Protegido {
        @PreAuthorize("hasRole('ADMINISTRADOR')")
        public void deAdmin() {
        }

        @PreAuthorize("hasRole('SUPERADMIN')")
        public void deSuperadmin() {
        }
    }

    @Autowired
    private Protegido protegido;

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    @DisplayName("SUPERADMIN pasa los endpoints de ADMINISTRADOR")
    void superadminHeredaAdministrador() {
        assertDoesNotThrow(protegido::deAdmin);
        assertDoesNotThrow(protegido::deSuperadmin);
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("ADMINISTRADOR no hereda SUPERADMIN")
    void administradorNoEsSuperadmin() {
        assertDoesNotThrow(protegido::deAdmin);
        assertThrows(AccessDeniedException.class, protegido::deSuperadmin);
    }

    @Test
    @WithMockUser(roles = "PROFESOR")
    @DisplayName("PROFESOR no pasa los endpoints de ADMINISTRADOR")
    void profesorNoEsAdministrador() {
        assertThrows(AccessDeniedException.class, protegido::deAdmin);
    }
}
