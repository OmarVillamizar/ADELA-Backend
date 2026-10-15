package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
import org.springframework.data.domain.PageImpl;

import com.adela.dto.EstudianteBusquedaDTO;
import com.adela.dto.EstudianteDTO;
import com.adela.entities.Estudiante;
import com.adela.entities.Profesor;
import com.adela.entities.Rol;
import com.adela.repositories.EstudianteRepository;
import com.adela.repositories.GrupoRepository;

import jakarta.persistence.EntityNotFoundException;

/** H-1: un profesor no recorre la tabla de estudiantes ni lee datos personales ajenos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EstudianteServiceTest {

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private GrupoRepository grupoRepository;

    @InjectMocks
    private EstudianteService service;

    private final Profesor profesor = con("profe@ufps.edu.co", "PROFESOR");
    private final Profesor admin = con("admin@ufps.edu.co", "ADMINISTRADOR");
    private final Estudiante estudiante = new Estudiante();

    private static Profesor con(String email, String rol) {
        Profesor p = new Profesor();
        p.setEmail(email);
        p.setRol(new Rol(1, rol));
        return p;
    }

    @BeforeEach
    void preparar() {
        estudiante.setEmail("ana@ufps.edu.co");
        estudiante.setNombre("Ana");
        estudiante.setCodigo("1150000");
        when(estudianteRepository.findByEmailContainingIgnoreCaseOrNombreContainingIgnoreCase(anyString(),
                anyString(), any())).thenReturn(new PageImpl<>(List.of(estudiante)));
        when(estudianteRepository.findById("ana@ufps.edu.co")).thenReturn(Optional.of(estudiante));
    }

    @Test
    @DisplayName("Profesor con menos de 3 caracteres no recibe nada ni consulta la base")
    void busquedaCortaVacia() {
        assertTrue(service.buscar(" an ", 20, profesor).isEmpty());
        verify(estudianteRepository, never())
                .findByEmailContainingIgnoreCaseOrNombreContainingIgnoreCase(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Profesor recibe solo correo y nombre, como mucho 20")
    void profesorSoloCorreoYNombre() {
        List<?> res = service.buscar("ana", 500, profesor);

        assertEquals(new EstudianteBusquedaDTO("ana@ufps.edu.co", "Ana"), res.get(0));
        verify(estudianteRepository).findByEmailContainingIgnoreCaseOrNombreContainingIgnoreCase(eq("ana"),
                eq("ana"), argThat(p -> p.getPageSize() == EstudianteService.TAM_MAX_PROFESOR));
    }

    @Test
    @DisplayName("Admin conserva los datos completos y búsquedas cortas")
    void adminCompleto() {
        List<?> res = service.buscar("", 20, admin);

        assertInstanceOf(EstudianteDTO.class, res.get(0));
        assertEquals("1150000", ((EstudianteDTO) res.get(0)).getCodigo());
    }

    @Test
    @DisplayName("Profesor no consulta a un estudiante fuera de sus grupos: 404")
    void consultarAjenoNoEncontrado() {
        when(grupoRepository.existsByProfesorAndEstudiantes_Email(profesor, "ana@ufps.edu.co")).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.consultar("ana@ufps.edu.co", profesor));
        verify(estudianteRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("Profesor consulta a un estudiante de su grupo")
    void consultarPropio() {
        when(grupoRepository.existsByProfesorAndEstudiantes_Email(profesor, "ana@ufps.edu.co")).thenReturn(true);

        assertEquals("ana@ufps.edu.co", service.consultar("ana@ufps.edu.co", profesor).getEmail());
    }

    @Test
    @DisplayName("Admin consulta a cualquiera")
    void adminConsulta() {
        assertEquals("ana@ufps.edu.co", service.consultar("ana@ufps.edu.co", admin).getEmail());
        verify(grupoRepository, never()).existsByProfesorAndEstudiantes_Email(any(), anyString());
    }
}
