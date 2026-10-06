package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.adela.entities.Insignia;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.InsigniaEstudianteRepository;

/**
 * El código de insignia llega de la URL: se comprueba que un valor ajeno al
 * catálogo y una insignia no ganada respondan con su error, no con un 500.
 */
@ExtendWith(MockitoExtension.class)
class InsigniaServiceTest {

    @Mock
    private InsigniaEstudianteRepository repositorio;

    @InjectMocks
    private InsigniaService service;

    @Test
    @DisplayName("Código fuera del catálogo: VALIDACION y no toca la BD")
    void codigoInexistente() {
        AppException e = assertThrows(AppException.class, () -> service.marcarCelebrada("a@b.co", "HACKER"));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
        verify(repositorio, never()).marcarCelebrada(anyString(), any());
    }

    @Test
    @DisplayName("Insignia no ganada por el estudiante: RECURSO_NO_ENCONTRADO")
    void noGanada() {
        when(repositorio.marcarCelebrada("a@b.co", Insignia.PRIMER_REPORTE)).thenReturn(0);
        AppException e = assertThrows(AppException.class,
                () -> service.marcarCelebrada("a@b.co", "PRIMER_REPORTE"));
        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO, e.getCode());
    }

    @Test
    @DisplayName("Insignia ganada: se marca sin error")
    void ganada() {
        when(repositorio.marcarCelebrada("a@b.co", Insignia.PRIMER_REPORTE)).thenReturn(1);
        assertDoesNotThrow(() -> service.marcarCelebrada("a@b.co", "PRIMER_REPORTE"));
    }
}
