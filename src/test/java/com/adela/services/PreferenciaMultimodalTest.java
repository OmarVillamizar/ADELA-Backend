package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.TipoEstilo;
import com.adela.dto.EstiloResultadoDTO;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estudiante;
import com.adela.entities.PreferenciaMultimodal;
import com.adela.entities.ResultadoCuestionario;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.ResultadoCuestionarioRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * La preferencia multimodal se declara una sola vez, solo sobre un resultado
 * propio y solo si el perfil reúne todas las modalidades.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreferenciaMultimodalTest {

    @Mock
    private ResultadoCuestionarioRepository resultadoCuestionarioRepository;

    @InjectMocks
    private ResultadoCuestionarioService service;

    private static Estudiante estudiante(String email) {
        Estudiante e = new Estudiante();
        e.setEmail(email);
        return e;
    }

    private static EstiloResultadoDTO estilo(TipoEstilo tipo, boolean dominante) {
        EstiloResultadoDTO e = new EstiloResultadoDTO();
        e.setTipo(tipo);
        e.setDominante(dominante);
        return e;
    }

    private static Cuestionario cuestionario(EsquemaInterpretacion esquema) {
        Cuestionario c = new Cuestionario();
        c.setEsquemaInterpretacion(esquema);
        c.setPreguntaPreferencia(true);
        return c;
    }

    @Test
    void aplicaSoloConTodasLasPrimariasEnElPerfilEscalonado() {
        Cuestionario escalonado = cuestionario(EsquemaInterpretacion.RELATIVO_ESCALONADO);
        List<EstiloResultadoDTO> todas = List.of(estilo(TipoEstilo.PRIMARIO, true), estilo(TipoEstilo.PRIMARIO, true),
                estilo(TipoEstilo.PRIMARIO, true), estilo(TipoEstilo.PRIMARIO, true));
        List<EstiloResultadoDTO> tres = List.of(estilo(TipoEstilo.PRIMARIO, true), estilo(TipoEstilo.PRIMARIO, true),
                estilo(TipoEstilo.PRIMARIO, true), estilo(TipoEstilo.PRIMARIO, false));

        assertTrue(EvaluacionRespuestas.pidePreferencia(escalonado, todas));
        assertFalse(EvaluacionRespuestas.pidePreferencia(escalonado, tres));
        assertFalse(EvaluacionRespuestas.pidePreferencia(cuestionario(EsquemaInterpretacion.RELATIVO), todas));
        assertFalse(EvaluacionRespuestas.pidePreferencia(escalonado, List.of(estilo(TipoEstilo.PRIMARIO, true))));
    }

    @Test
    void sinActivarlaElPerfilMultimodalQuedaComoSiempre() {
        Cuestionario apagada = cuestionario(EsquemaInterpretacion.RELATIVO_ESCALONADO);
        apagada.setPreguntaPreferencia(false);
        List<EstiloResultadoDTO> todas = List.of(estilo(TipoEstilo.PRIMARIO, true), estilo(TipoEstilo.PRIMARIO, true));

        assertFalse(EvaluacionRespuestas.pidePreferencia(apagada, todas));
    }

    @Test
    void noSeDeclaraSobreElResultadoDeOtro() {
        ResultadoCuestionario rc = new ResultadoCuestionario();
        rc.setEstudiante(estudiante("duenyo@ufps.edu.co"));
        when(resultadoCuestionarioRepository.findById(1L)).thenReturn(Optional.of(rc));

        assertThrows(EntityNotFoundException.class, () -> service.declararPreferencia(1L,
                estudiante("otro@ufps.edu.co"), PreferenciaMultimodal.SELECTIVO));
        verify(resultadoCuestionarioRepository, never()).save(any());
    }

    @Test
    void unaVezDeclaradaNoCambia() {
        ResultadoCuestionario rc = new ResultadoCuestionario();
        rc.setEstudiante(estudiante("duenyo@ufps.edu.co"));
        rc.setPreferenciaMultimodal(PreferenciaMultimodal.SELECTIVO);
        when(resultadoCuestionarioRepository.findById(1L)).thenReturn(Optional.of(rc));

        AppException e = assertThrows(AppException.class, () -> service.declararPreferencia(1L,
                estudiante("duenyo@ufps.edu.co"), PreferenciaMultimodal.INTEGRATIVO));
        assertEquals(ErrorCode.PREFERENCIA_YA_DECLARADA, e.getCode());
        verify(resultadoCuestionarioRepository, never()).save(any());
    }
}
