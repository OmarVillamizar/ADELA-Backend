package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.dto.CapsulaActualizarDTO;
import com.adela.dto.CapsulaCrearDTO;
import com.adela.dto.CapsulaDTO;
import com.adela.entities.Capsula;
import com.adela.entities.Cuestionario;
import com.adela.entities.ModoIdentificacion;
import com.adela.entities.Profesor;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.CapsulaRepository;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.RespuestaCapsulaRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Cápsulas del lado del profesor. Protege el control de propiedad: una cápsula
 * ajena se trata como inexistente, igual que un grupo ajeno.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapsulaServiceTest {

    @Mock
    private CapsulaRepository capsulaRepository;

    @Mock
    private RespuestaCapsulaRepository respuestaCapsulaRepository;

    @Mock
    private CuestionarioRepository cuestionarioRepository;

    @InjectMocks
    private CapsulaService service;

    private Profesor propietario;
    private Profesor intruso;
    private Cuestionario cuestionario;

    @BeforeEach
    void preparar() {
        propietario = profesor("propietario@ufps.edu.co");
        intruso = profesor("intruso@ufps.edu.co");
        cuestionario = new Cuestionario();
        cuestionario.setId(1L);
        cuestionario.setNombre("VARK");
        cuestionario.setSiglas("VARK");

        Capsula capsula = new Capsula();
        capsula.setId(5L);
        capsula.setCodigo("ABCD2345");
        capsula.setNombre("Charla");
        capsula.setProfesor(propietario);
        capsula.setCuestionario(cuestionario);
        capsula.setModoIdentificacion(ModoIdentificacion.NOMBRE);

        when(capsulaRepository.findByProfesorAndId(propietario, 5L)).thenReturn(Optional.of(capsula));
        when(capsulaRepository.findByProfesorAndId(intruso, 5L)).thenReturn(Optional.empty());
        when(capsulaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cuestionarioRepository.findById(1L)).thenReturn(Optional.of(cuestionario));
    }

    @Test
    @DisplayName("Una cápsula de otro profesor no se consulta, edita ni elimina")
    void capsulaAjenaEsInexistente() {
        assertThrows(EntityNotFoundException.class, () -> service.consultar(5L, intruso));
        assertThrows(EntityNotFoundException.class,
                () -> service.actualizar(5L, new CapsulaActualizarDTO(null, false), intruso));
        assertThrows(EntityNotFoundException.class, () -> service.eliminar(5L, intruso));
        verify(capsulaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Crear asigna un código del alfabeto público y la deja abierta")
    void crearAsignaCodigo() {
        CapsulaDTO dto = service.crear(new CapsulaCrearDTO("  Charla  ", 1L, ModoIdentificacion.ANONIMO),
                propietario);

        assertEquals(CapsulaService.LONGITUD_CODIGO_CAPSULA, dto.codigo().length());
        assertTrue(dto.codigo().chars().allMatch(c -> CodigoAleatorio.ALFABETO.indexOf(c) >= 0));
        assertEquals("Charla", dto.nombre());
        assertTrue(dto.abierta());
    }

    @Test
    @DisplayName("Renombrar con un nombre vacío es VALIDACION")
    void nombreVacioEsValidacion() {
        AppException e = assertThrows(AppException.class,
                () -> service.actualizar(5L, new CapsulaActualizarDTO("   ", null), propietario));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
        assertTrue(e.getFields().containsKey("nombre"));
    }

    @Test
    @DisplayName("Un código en uso se descarta y se genera otro")
    void codigoEnUsoSeReintenta() {
        int[] llamadas = { 0 };
        String codigo = CapsulaService.codigoLibre(8, c -> llamadas[0]++ == 0);
        assertEquals(2, llamadas[0]);
        assertEquals(8, codigo.length());
    }

    @Test
    @DisplayName("El código se acepta con minúsculas, guiones y espacios")
    void codigoSeNormaliza() {
        assertEquals("K7QM2XPA9DTR", CodigoAleatorio.normalizar(" k7qm-2xpa 9dtr "));
    }

    private static Profesor profesor(String email) {
        Profesor p = new Profesor();
        p.setEmail(email);
        return p;
    }
}
