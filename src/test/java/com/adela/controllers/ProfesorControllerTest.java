package com.adela.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.entities.Profesor;
import com.adela.entities.ProfesorEstado;
import com.adela.entities.Rol;
import com.adela.entities.UsuarioEstado;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.ProfesorRepository;
import com.adela.repositories.RolRepository;
import com.adela.repositories.UsuarioRepository;

/**
 * SUPERADMIN se gestiona solo desde la base de datos: ninguna acción de la
 * pantalla de cuentas puede desactivarlo, cambiarle el rol ni rechazarlo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfesorControllerTest {

    private static final String CORREO = "superadmin@ufps.edu.co";

    @Mock
    private ProfesorRepository profesorRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolRepository rolRepository;

    @InjectMocks
    private ProfesorController controller;

    private static Profesor conRol(String rol, ProfesorEstado estadoProfesor) {
        Profesor p = new Profesor();
        p.setEmail(CORREO);
        p.setEstado(UsuarioEstado.ACTIVA);
        p.setEstadoProfesor(estadoProfesor);
        p.setRol(new Rol(3, rol));
        return p;
    }

    static Stream<Arguments> acciones() {
        return Stream.of(
                Arguments.of("deactivate", ProfesorEstado.ACTIVA,
                        (Consumer<ProfesorController>) c -> c.eliminarProfesor(CORREO)),
                Arguments.of("activate", ProfesorEstado.INACTIVA,
                        (Consumer<ProfesorController>) c -> c.activarCuentaProfesor(CORREO)),
                Arguments.of("elevate", ProfesorEstado.ACTIVA,
                        (Consumer<ProfesorController>) c -> c.elevarCuentaProfesor(CORREO)),
                Arguments.of("demote", ProfesorEstado.ACTIVA,
                        (Consumer<ProfesorController>) c -> c.bajarCuentaProfesor(CORREO)),
                Arguments.of("reject", ProfesorEstado.INACTIVA,
                        (Consumer<ProfesorController>) c -> c.rechazarSolicitudCuentaProfesor(CORREO)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acciones")
    @DisplayName("Ninguna acción de cuentas toca a un SUPERADMIN")
    void superadminProtegido(String accion, ProfesorEstado estado, Consumer<ProfesorController> llamada) {
        when(profesorRepository.findById(CORREO)).thenReturn(Optional.of(conRol("SUPERADMIN", estado)));

        AppException e = assertThrows(AppException.class, () -> llamada.accept(controller));
        assertEquals(ErrorCode.CUENTA_PROTEGIDA, e.getCode());
        verify(profesorRepository, never()).save(any());
        verify(profesorRepository, never()).delete(any());
    }

    @Test
    @DisplayName("La guarda no bloquea ascender a un profesor normal")
    void elevarProfesorNormal() {
        when(profesorRepository.findById(CORREO))
                .thenReturn(Optional.of(conRol("PROFESOR", ProfesorEstado.ACTIVA)));
        when(rolRepository.findByDescripcion("ADMINISTRADOR")).thenReturn(Optional.of(new Rol(1, "ADMINISTRADOR")));
        when(profesorRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        controller.elevarCuentaProfesor(CORREO);

        verify(profesorRepository).save(any());
    }
}
