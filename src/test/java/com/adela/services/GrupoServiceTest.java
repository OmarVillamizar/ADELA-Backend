package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.dto.EstudianteCrearDTO;
import com.adela.dto.GrupoDTO;
import com.adela.dto.GrupoDetalleDTO;
import com.adela.entities.Estudiante;
import com.adela.entities.Grupo;
import com.adela.entities.Profesor;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.EstudianteRepository;
import com.adela.repositories.GrupoRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;

/**
 * Cubre la creación de grupos y el alta de estudiantes: grupo vacío, correos
 * normalizados, nombres repetidos y preinscripción de quien aún no ha entrado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrupoServiceTest {

    @Mock
    private GrupoRepository grupoRepository;

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private ResultadoCuestionarioService resultadoCuestionarioService;

    @Mock
    private ResultadoCuestionarioRepository resultadoCuestionarioRepository;

    @InjectMocks
    private GrupoService service;

    private final Profesor profesor = new Profesor();

    @BeforeEach
    void preparar() {
        profesor.setEmail("profe@ufps.edu.co");
        when(grupoRepository.save(any(Grupo.class))).then(AdditionalAnswers.returnsFirstArg());
        when(grupoRepository.findByProfesor(profesor)).thenReturn(List.of());
        when(estudianteRepository.findById(anyString())).thenReturn(Optional.empty());
    }

    private static EstudianteCrearDTO alta(String email, String nombre) {
        EstudianteCrearDTO dto = new EstudianteCrearDTO();
        dto.setEmail(email);
        dto.setNombre(nombre);
        return dto;
    }

    private static GrupoDTO grupo(String nombre, Set<EstudianteCrearDTO> estudiantes) {
        return new GrupoDTO(nombre, estudiantes);
    }

    @Test
    @DisplayName("crear admite un grupo sin estudiantes")
    void crearGrupoVacio() {
        GrupoDetalleDTO creado = service.crear(grupo("  Cálculo I  ", null), profesor);

        assertEquals("Cálculo I", creado.nombre());
        assertTrue(creado.estudiantes().isEmpty());
    }

    @Test
    @DisplayName("crear rechaza un nombre vacío")
    void crearSinNombre() {
        AppException e = assertThrows(AppException.class, () -> service.crear(grupo("   ", Set.of()), profesor));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
    }

    @Test
    @DisplayName("crear rechaza un nombre que el profesor ya usa, sin distinguir mayúsculas")
    void crearNombreRepetido() {
        Grupo existente = new Grupo();
        existente.setId(3);
        existente.setNombre("Cálculo I");
        when(grupoRepository.findByProfesor(profesor)).thenReturn(List.of(existente));

        AppException e = assertThrows(AppException.class, () -> service.crear(grupo("cálculo i", Set.of()), profesor));
        assertEquals(ErrorCode.GRUPO_DUPLICADO, e.getCode());
        verify(grupoRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear normaliza los correos y une los repetidos de la lista")
    void crearNormalizaCorreos() {
        Set<EstudianteCrearDTO> lista = new HashSet<>(List.of(
                alta(" Ana@UFPS.edu.co ", "Ana Pérez"),
                alta("ana@ufps.edu.co", ""),
                alta("luis@ufps.edu.co", null)));

        GrupoDetalleDTO creado = service.crear(grupo("Física", lista), profesor);

        assertEquals(2, creado.estudiantes().size());
        assertTrue(creado.estudiantes().stream()
                .anyMatch(e -> e.getEmail().equals("ana@ufps.edu.co") && "Ana Pérez".equals(e.getNombre())));
    }

    @Test
    @DisplayName("crear rechaza la lista entera si un correo no es válido, sin guardar nada")
    void crearCorreoInvalido() {
        Set<EstudianteCrearDTO> lista = Set.of(alta("ana@ufps.edu.co", "Ana"), alta("no-es-correo", "Luis"));

        AppException e = assertThrows(AppException.class, () -> service.crear(grupo("Física", lista), profesor));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
        verify(grupoRepository, never()).save(any());
    }

    @Test
    @DisplayName("agregarEstudiantes preinscribe a quien no existe y omite a quien ya está en el grupo")
    void agregarPreinscribe() {
        Estudiante miembro = new Estudiante();
        miembro.setEmail("ana@ufps.edu.co");
        Grupo g = new Grupo();
        g.setId(7);
        g.setNombre("Física");
        g.setProfesor(profesor);
        g.setEstudiantes(new HashSet<>(Set.of(miembro)));
        when(grupoRepository.findByProfesorAndId(profesor, 7)).thenReturn(Optional.of(g));
        when(estudianteRepository.findById("ana@ufps.edu.co")).thenReturn(Optional.of(miembro));

        GrupoDetalleDTO actualizado = service.agregarEstudiantes(7,
                List.of(alta("ANA@ufps.edu.co", "Ana"), alta("nuevo@ufps.edu.co", "Nuevo")), profesor);

        assertEquals(2, actualizado.estudiantes().size());
        verify(resultadoCuestionarioService).asignarCuestionariosAsignadosAlGrupoAEstudiantesNuevos(
                any(), argThat(s -> s.size() == 1
                        && s.iterator().next().getEmail().equals("nuevo@ufps.edu.co")));
    }

    @Test
    @DisplayName("Una lista de más de 500 estudiantes se rechaza sin consultar la base")
    void listaEnormeRechazada() {
        List<EstudianteCrearDTO> enorme = new java.util.ArrayList<>();
        for (int i = 0; i <= GrupoService.MAX_ESTUDIANTES_POR_PETICION; i++) {
            enorme.add(alta("e" + i + "@ufps.edu.co", null));
        }
        Grupo g = new Grupo();
        g.setId(7);
        g.setProfesor(profesor);
        g.setEstudiantes(new HashSet<>());
        when(grupoRepository.findByProfesorAndId(profesor, 7)).thenReturn(Optional.of(g));

        AppException e = assertThrows(AppException.class, () -> service.agregarEstudiantes(7, enorme, profesor));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
        verify(estudianteRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("Quitar con un correo nulo responde no encontrado, no un 500")
    void quitarCorreoNulo() {
        Grupo g = new Grupo();
        g.setId(7);
        g.setProfesor(profesor);
        g.setEstudiantes(new HashSet<>());
        when(grupoRepository.findByProfesorAndId(profesor, 7)).thenReturn(Optional.of(g));
        List<String> emails = new java.util.ArrayList<>();
        emails.add(null);

        AppException e = assertThrows(AppException.class, () -> service.eliminarEstudiantes(7, emails, profesor));
        assertEquals(ErrorCode.ESTUDIANTES_NO_ENCONTRADOS, e.getCode());
    }
}
