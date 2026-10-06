package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;

/**
 * El código solo se exige a cuentas UFPS. Los usuarios externos lo inventaban
 * para poder seguir y chocaban con CODIGO_DUPLICADO.
 */
class CodigoInstitucionalTest {

    @Test
    @DisplayName("Solo el dominio ufps.edu.co exige código, sin importar mayúsculas")
    void requiereSoloUfps() {
        assertTrue(CodigoInstitucional.requiere("ana@ufps.edu.co"));
        assertTrue(CodigoInstitucional.requiere("Ana@UFPS.EDU.CO"));
        assertFalse(CodigoInstitucional.requiere("ana@gmail.com"));
        assertFalse(CodigoInstitucional.requiere("ana@ufps.edu.co.evil.com"));
        assertFalse(CodigoInstitucional.requiere("ana@noufps.edu.co"));
        assertFalse(CodigoInstitucional.requiere(null));
    }

    @Test
    @DisplayName("Fuera de la UFPS se guarda null aunque el cliente mande un código")
    void externoGuardaNull() {
        assertNull(CodigoInstitucional.aGuardar("ana@gmail.com", "1152239"));
        assertNull(CodigoInstitucional.aGuardar("ana@gmail.com", ""));
    }

    @Test
    @DisplayName("Cuenta UFPS sin código responde VALIDACION")
    void ufpsSinCodigoFalla() {
        AppException ex = assertThrows(AppException.class,
                () -> CodigoInstitucional.aGuardar("ana@ufps.edu.co", " "));
        assertEquals(ErrorCode.VALIDACION, ex.getCode());
        assertThrows(AppException.class, () -> CodigoInstitucional.aGuardar("ana@ufps.edu.co", null));
    }

    @Test
    @DisplayName("Cuenta UFPS con código lo guarda")
    void ufpsConCodigo() {
        assertEquals("1152239", CodigoInstitucional.aGuardar("ana@ufps.edu.co", "1152239"));
    }
}
