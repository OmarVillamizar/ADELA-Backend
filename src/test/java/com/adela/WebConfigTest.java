package com.adela;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SEC-14: CORS con credenciales no arranca con una lista de orígenes inservible. */
class WebConfigTest {

    @Test
    @DisplayName("Recorta espacios y descarta entradas vacías")
    void normaliza() {
        assertArrayEquals(new String[] { "http://localhost:3000", "https://adela.app" },
                WebConfig.origenes(" http://localhost:3000 , ,https://adela.app"));
    }

    @Test
    @DisplayName("Vacía o nula no arranca")
    void vacia() {
        assertThrows(IllegalStateException.class, () -> WebConfig.origenes(""));
        assertThrows(IllegalStateException.class, () -> WebConfig.origenes(" , "));
        assertThrows(IllegalStateException.class, () -> WebConfig.origenes(null));
    }

    @Test
    @DisplayName("Un comodín no arranca")
    void comodin() {
        assertThrows(IllegalStateException.class, () -> WebConfig.origenes("*"));
        assertThrows(IllegalStateException.class, () -> WebConfig.origenes("http://localhost:3000,https://*.adela.app"));
    }
}
