package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.FormatoItem;
import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.InterpretacionDTO.PlanoDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.OpcionDTO.PesoDTO;
import com.adela.dto.PreguntaDTO;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estilo;
import com.adela.entities.Pregunta;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.CapsulaRepository;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;

/**
 * La interpretación elegida al crear viaja en la misma petición: si es
 * inválida, el error sale de la misma transacción y no queda un cuestionario
 * sin su lectura.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreacionCuestionarioTest {

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

    @BeforeEach
    void preparar() {
        when(cuestionarioRepository.save(any())).thenAnswer(inv -> {
            Cuestionario c = inv.getArgument(0);
            c.setId(7L);
            return c;
        });
        when(estiloService.crearEstilo(any(), any())).thenAnswer(inv -> {
            Estilo e = new Estilo();
            e.setId(70L);
            e.setNombre(((EstiloDTO) inv.getArgument(1)).getNombre());
            return e;
        });
        when(preguntaService.crearPregunta(any(), any(), any())).thenReturn(new Pregunta());
    }

    private static CuestionarioDTO dto(InterpretacionDTO interpretacion) {
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
        c.setNombre("Canales");
        c.setEstilos(List.of(visual));
        c.setPreguntas(List.of(pregunta));
        c.setInterpretacion(interpretacion);
        return c;
    }

    @Test
    @DisplayName("Guarda la interpretación con el id del cuestionario recién creado")
    void guardaInterpretacion() {
        InterpretacionDTO lectura = new InterpretacionDTO(EsquemaInterpretacion.RELATIVO, 10d, null, null, null);

        service.crearCuestionario(dto(lectura));

        verify(interpretacionService).guardar(7L, lectura);
    }

    @Test
    @DisplayName("La interpretación por cuadrantes viaja con su plano a guardar")
    void guardaPlano() {
        InterpretacionDTO lectura = new InterpretacionDTO(EsquemaInterpretacion.CUADRANTES, null, null, null, null,
                new PlanoDTO("Visual", "Auditivo", 6d, 7d, "Convergente", "Asimilador", "Divergente", "Acomodador", null, null),
                null);

        service.crearCuestionario(dto(lectura));

        verify(interpretacionService).guardar(7L, lectura);
    }

    @Test
    @DisplayName("Sin interpretación no la toca")
    void sinInterpretacion() {
        service.crearCuestionario(dto(null));

        verify(interpretacionService, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("Una interpretación inválida propaga VALIDACION desde la creación")
    void interpretacionInvalida() {
        InterpretacionDTO mala = new InterpretacionDTO(null, null, null, null, null);
        when(interpretacionService.guardar(eq(7L), eq(mala))).thenThrow(
                new AppException(ErrorCode.VALIDACION, "Revisa los campos marcados.", Map.of("esquema", "x")));

        AppException e = assertThrows(AppException.class, () -> service.crearCuestionario(dto(mala)));
        assertEquals(ErrorCode.VALIDACION, e.getCode());
    }
}
