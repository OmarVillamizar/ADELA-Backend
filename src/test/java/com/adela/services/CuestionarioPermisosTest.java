package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.calificacion.FormatoItem;
import com.adela.dto.CuestionarioDTO;
import com.adela.dto.CuestionarioResumidoDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.OpcionDTO.PesoDTO;
import com.adela.dto.PreguntaDTO;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estilo;
import com.adela.entities.Estudiante;
import com.adela.entities.Pregunta;
import com.adela.entities.Profesor;
import com.adela.entities.Rol;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.CapsulaRepository;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Generales (sin dueño) solo los gestiona SUPERADMIN; un personal, solo su
 * dueño. Uno ajeno no se distingue de uno inexistente (404); uno que se ve pero
 * no se puede cambiar responde 403.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CuestionarioPermisosTest {

    @Mock
    private CuestionarioRepository cuestionarioRepository;
    @Mock
    private ResultadoCuestionarioRepository resultadoCuestionarioRepository;
    @Mock
    private CapsulaRepository capsulaRepository;
    @Mock
    private EstiloService estiloService;
    @Mock
    private PreguntaService preguntaService;
    @Mock
    private InterpretacionService interpretacionService;
    @InjectMocks
    private CuestionarioService service;

    private static Profesor con(String email, String rol) {
        Profesor p = new Profesor();
        p.setEmail(email);
        p.setRol(new Rol(1, rol));
        return p;
    }

    private final Profesor superadmin = con("super@ufps.edu.co", "SUPERADMIN");
    private final Profesor admin = con("admin@ufps.edu.co", "ADMINISTRADOR");
    private final Profesor otroAdmin = con("otro@ufps.edu.co", "ADMINISTRADOR");

    private static Cuestionario cuestionario(Long id, Profesor duenyo) {
        Cuestionario c = new Cuestionario();
        c.setId(id);
        c.setProfesor(duenyo);
        return c;
    }

    @BeforeEach
    void preparar() {
        when(cuestionarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estiloService.crearEstilo(any(), any())).thenAnswer(inv -> {
            Estilo e = new Estilo();
            e.setId(70L);
            return e;
        });
        when(preguntaService.crearPregunta(any(), any(), any())).thenReturn(new Pregunta());
    }

    private static CuestionarioDTO dto(Boolean general) {
        EstiloDTO visual = new EstiloDTO();
        visual.setId(1);
        visual.setNombre("Visual");
        OpcionDTO opcion = new OpcionDTO();
        opcion.setRespuesta("Mirar un mapa");
        opcion.setPesos(List.of(new PesoDTO(1, 1d)));
        PreguntaDTO pregunta = new PreguntaDTO();
        pregunta.setOrden(1);
        pregunta.setPregunta("¿Cómo llegas a un sitio nuevo?");
        pregunta.setFormato(FormatoItem.UNICA);
        pregunta.setOpciones(List.of(opcion));
        CuestionarioDTO c = new CuestionarioDTO();
        c.setNombre("Prueba");
        c.setEstilos(List.of(visual));
        c.setPreguntas(List.of(pregunta));
        c.setGeneral(general);
        return c;
    }

    // ------------------------------------------------------------------ Crear

    @Test
    @DisplayName("Un administrador no puede publicar un cuestionario general")
    void adminNoCreaGeneral() {
        AppException e = assertThrows(AppException.class, () -> service.crearCuestionario(dto(true), admin));
        assertEquals(ErrorCode.ACCESO_DENEGADO, e.getCode());
        verify(cuestionarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("El superadmin publica un general: queda sin dueño")
    void superadminCreaGeneralSinDuenyo() {
        assertNull(service.crearCuestionario(dto(true), superadmin).getProfesor());
    }

    @Test
    @DisplayName("Sin general, el cuestionario queda personal de quien lo crea")
    void personalQuedaDeQuienLoCrea() {
        assertSame(admin, service.crearCuestionario(dto(null), admin).getProfesor());
        assertSame(superadmin, service.crearCuestionario(dto(false), superadmin).getProfesor());
    }

    // --------------------------------------------------------------- Archivar

    @Test
    @DisplayName("Un administrador no puede archivar un general: 403 y nada cambia")
    void adminNoArchivaGeneral() {
        Cuestionario general = cuestionario(1L, null);
        when(cuestionarioRepository.findVisible(1L, admin)).thenReturn(Optional.of(general));

        AppException e = assertThrows(AppException.class, () -> service.archivar(1L, admin));
        assertEquals(ErrorCode.ACCESO_DENEGADO, e.getCode());
        assertFalse(general.isArchivado());
        verify(resultadoCuestionarioRepository, never()).borrarPendientes(any());
        verify(capsulaRepository, never()).cerrarDeCuestionario(any());
    }

    @Test
    @DisplayName("Archivar marca el cuestionario, borra las pendientes y cierra sus cápsulas")
    void superadminArchivaGeneral() {
        Cuestionario general = cuestionario(1L, null);
        when(cuestionarioRepository.findVisible(1L, superadmin)).thenReturn(Optional.of(general));

        service.archivar(1L, superadmin);

        assertTrue(general.isArchivado());
        verify(resultadoCuestionarioRepository).borrarPendientes(general);
        verify(capsulaRepository).cerrarDeCuestionario(general);
    }

    @Test
    @DisplayName("Un personal ajeno no se ve: archivarlo responde 404")
    void personalAjenoNoSeArchiva() {
        when(cuestionarioRepository.findVisible(2L, otroAdmin)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.archivar(2L, otroAdmin));
        verify(resultadoCuestionarioRepository, never()).borrarPendientes(any());
    }

    @Test
    @DisplayName("El dueño archiva su personal, aunque su correo cambie de mayúsculas")
    void duenyoArchivaPersonal() {
        Cuestionario propio = cuestionario(2L, con("ADMIN@ufps.edu.co", "ADMINISTRADOR"));
        when(cuestionarioRepository.findVisible(2L, admin)).thenReturn(Optional.of(propio));

        service.archivar(2L, admin);

        assertTrue(propio.isArchivado());
    }

    @Test
    @DisplayName("El superadmin no archiva el personal de otro: no lo ve")
    void superadminNoVePersonalAjeno() {
        when(cuestionarioRepository.findVisible(2L, superadmin)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.archivar(2L, superadmin));
    }

    // ---------------------------------------------------------- Interpretación

    @Test
    @DisplayName("Un administrador lee la interpretación de un general pero no la cambia")
    void adminLeePeroNoCambiaInterpretacionGeneral() {
        when(cuestionarioRepository.findVisible(1L, admin)).thenReturn(Optional.of(cuestionario(1L, null)));

        service.obtenerInterpretacion(1L, admin);
        verify(interpretacionService).obtener(1L);

        AppException e = assertThrows(AppException.class,
                () -> service.guardarInterpretacion(1L, new InterpretacionDTO(null, null, null, null, null, null,
                        null, null), admin));
        assertEquals(ErrorCode.ACCESO_DENEGADO, e.getCode());
        verify(interpretacionService, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("El dueño guarda la interpretación de su personal")
    void duenyoGuardaInterpretacion() {
        when(cuestionarioRepository.findVisible(2L, admin)).thenReturn(Optional.of(cuestionario(2L, admin)));

        service.guardarInterpretacion(2L, null, admin);

        verify(interpretacionService).guardar(eq(2L), any());
    }

    // ------------------------------------------------------------ Listar / ver

    @Test
    @DisplayName("La lista marca editables: un general solo para el superadmin, un personal siempre")
    void listaMarcaEditables() {
        when(cuestionarioRepository.resumirVisibles(any())).thenAnswer(inv -> new ArrayList<>(List.of(
                new CuestionarioResumidoDTO(1L, "General", "G", "1", "a", "d", 3, true),
                new CuestionarioResumidoDTO(2L, "Propio", "P", "1", "a", "d", 3, false))));

        List<CuestionarioResumidoDTO> deAdmin = service.getCuestionarios(admin);
        assertFalse(deAdmin.get(0).isEditable());
        assertTrue(deAdmin.get(1).isEditable());

        assertTrue(service.getCuestionarios(superadmin).get(0).isEditable());
    }

    @Test
    @DisplayName("Un profesor solo abre generales y propios")
    void verFiltraProfesor() {
        when(cuestionarioRepository.findVisible(3L, admin)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.obtenerParaResponder(3L, admin));
    }

    @Test
    @DisplayName("Un estudiante sin asignación no abre el cuestionario: 404")
    void estudianteSinAsignacionNoAbre() {
        Estudiante estudiante = new Estudiante();
        estudiante.setEmail("ana@ufps.edu.co");
        when(cuestionarioRepository.findById(3L)).thenReturn(Optional.of(cuestionario(3L, otroAdmin)));
        when(resultadoCuestionarioRepository.existsByCuestionario_IdAndEstudiante_Email(3L, "ana@ufps.edu.co"))
                .thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.obtenerParaEstudiante(3L, estudiante));
        verify(cuestionarioRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Un estudiante abre lo que tiene asignado")
    void estudianteConAsignacionAbre() {
        Estudiante estudiante = new Estudiante();
        estudiante.setEmail("ana@ufps.edu.co");
        when(cuestionarioRepository.findById(3L)).thenReturn(Optional.of(cuestionario(3L, otroAdmin)));
        when(resultadoCuestionarioRepository.existsByCuestionario_IdAndEstudiante_Email(3L, "ana@ufps.edu.co"))
                .thenReturn(true);

        service.obtenerParaEstudiante(3L, estudiante);
        ArgumentCaptor<Long> id = ArgumentCaptor.forClass(Long.class);
        verify(cuestionarioRepository).findById(id.capture());
        assertEquals(3L, id.getValue());
    }
}
