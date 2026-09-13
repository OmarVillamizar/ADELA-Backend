package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.entities.Estudiante;
import com.adela.entities.Grupo;
import com.adela.entities.Profesor;
import com.adela.entities.ResultadoCuestionario;
import com.adela.repositories.GrupoRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Cubre los controles de propiedad añadidos en la ola 1 (SEC-02, SEC-03, SEC-04).
 *
 * Son los únicos que impiden leer datos de otro usuario cambiando un id en la
 * URL, y hasta ahora nada verificaba que siguieran en su sitio: un refactor
 * podía retirarlos sin que nada fallara.
 *
 * Deliberadamente sin contexto de Spring. El objeto bajo prueba es lógica pura;
 * un @SpringBootTest exigiría base de datos y credenciales de Google, así que no
 * correría en CI ni en una máquina sin .env.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultadoCuestionarioServiceTest {

    @Mock
    private ResultadoCuestionarioRepository resultadoCuestionarioRepository;

    @Mock
    private GrupoRepository grupoRepository;

    @InjectMocks
    private ResultadoCuestionarioService service;

    private static Estudiante estudiante(String email) {
        Estudiante e = new Estudiante();
        e.setEmail(email);
        return e;
    }

    private static Profesor profesor(String email) {
        Profesor p = new Profesor();
        p.setEmail(email);
        return p;
    }

    private static Grupo grupoDe(Profesor propietario) {
        Grupo g = new Grupo();
        g.setId(7);
        g.setProfesor(propietario);
        return g;
    }

    @Test
    @DisplayName("SEC-02: un estudiante no puede leer el resultado de otro")
    void resultadoDeOtroEstudianteNoSeDevuelve() {
        ResultadoCuestionario resultado = new ResultadoCuestionario();
        resultado.setId(1L);
        resultado.setEstudiante(estudiante("victima@ufps.edu.co"));
        when(resultadoCuestionarioRepository.findById(1L)).thenReturn(Optional.of(resultado));

        assertThrows(EntityNotFoundException.class,
                () -> service.obtenerResultadoCuestionario(1L, estudiante("atacante@ufps.edu.co")));
    }

    @Test
    @DisplayName("SEC-03: un profesor no puede leer el reporte individual de un grupo ajeno")
    void reporteIndividualDeGrupoAjenoNoSeDevuelve() {
        ResultadoCuestionario resultado = new ResultadoCuestionario();
        resultado.setId(2L);
        resultado.setGrupo(grupoDe(profesor("propietario@ufps.edu.co")));
        when(resultadoCuestionarioRepository.findById(2L)).thenReturn(Optional.of(resultado));

        assertThrows(EntityNotFoundException.class,
                () -> service.obtenerResultadoCuestionario(2L, profesor("intruso@ufps.edu.co")));
    }

    @Test
    @DisplayName("SEC-03: una asignación sin grupo no tiene propietario, se deniega")
    void asignacionSinGrupoSeDeniega() {
        ResultadoCuestionario resultado = new ResultadoCuestionario();
        resultado.setId(3L);
        resultado.setGrupo(null);
        when(resultadoCuestionarioRepository.findById(3L)).thenReturn(Optional.of(resultado));

        assertThrows(EntityNotFoundException.class,
                () -> service.obtenerResultadoCuestionario(3L, profesor("cualquiera@ufps.edu.co")));
    }

    @Test
    @DisplayName("SEC-04: obtenerPorGrupo rechaza un grupo de otro profesor")
    void listadoDeGrupoAjenoNoSeDevuelve() {
        when(grupoRepository.findById(7)).thenReturn(Optional.of(grupoDe(profesor("propietario@ufps.edu.co"))));

        assertThrows(EntityNotFoundException.class,
                () -> service.obtenerPorGrupo(7, profesor("intruso@ufps.edu.co")));
    }

    @Test
    @DisplayName("El propietario sí pasa el control: la guarda no bloquea el caso legítimo")
    void elPropietarioNoEsRechazado() {
        Profesor duenyo = profesor("propietario@ufps.edu.co");
        when(grupoRepository.findById(7)).thenReturn(Optional.of(grupoDe(duenyo)));
        when(resultadoCuestionarioRepository.findByGrupo(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());

        assertDoesNotThrow(() -> service.obtenerPorGrupo(7, duenyo));
    }

    @Test
    @DisplayName("La comparación de correos ignora mayúsculas")
    void laComparacionDeCorreosIgnoraMayusculas() {
        when(grupoRepository.findById(7)).thenReturn(Optional.of(grupoDe(profesor("Propietario@UFPS.edu.co"))));
        when(resultadoCuestionarioRepository.findByGrupo(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());

        assertDoesNotThrow(() -> service.obtenerPorGrupo(7, profesor("propietario@ufps.edu.co")));
    }
}
