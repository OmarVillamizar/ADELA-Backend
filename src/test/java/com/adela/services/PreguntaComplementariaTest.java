package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.adela.calificacion.EstadoCalculo;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.ResultadoEstilo;
import com.adela.calificacion.ResultadoInstrumento;
import com.adela.calificacion.TipoEstilo;
import com.adela.dto.ComplementariaDTO;
import com.adela.dto.EstiloResultadoDTO;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estudiante;
import com.adela.entities.OpcionComplementaria;
import com.adela.entities.PreguntaComplementaria;
import com.adela.entities.ResultadoCuestionario;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.ResultadoCuestionarioRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * La pregunta complementaria se hace solo si el cuestionario la define, su
 * esquema destaca estilos y el perfil los destaca todos; se responde una vez,
 * sobre un resultado propio y con una opción de esa pregunta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreguntaComplementariaTest {

    @Mock
    private ResultadoCuestionarioRepository resultadoCuestionarioRepository;

    @InjectMocks
    private ResultadoCuestionarioService service;

    private static Estudiante estudiante(String email) {
        Estudiante e = new Estudiante();
        e.setEmail(email);
        return e;
    }

    private static EstiloResultadoDTO estilo(boolean dominante) {
        EstiloResultadoDTO e = new EstiloResultadoDTO();
        e.setTipo(TipoEstilo.PRIMARIO);
        e.setDominante(dominante);
        return e;
    }

    private static OpcionComplementaria opcion(PreguntaComplementaria p, long id, String resultado) {
        OpcionComplementaria o = new OpcionComplementaria();
        o.setId(id);
        o.setPregunta(p);
        o.setOrden((int) id);
        o.setTexto("Opción " + id);
        o.setResultado(resultado);
        p.getOpciones().add(o);
        return o;
    }

    /** Cuestionario con una pregunta de tres opciones (ids 1, 2 y 3). */
    private static Cuestionario cuestionario(EsquemaInterpretacion esquema) {
        Cuestionario c = new Cuestionario();
        c.setEsquemaInterpretacion(esquema);
        PreguntaComplementaria p = new PreguntaComplementaria();
        p.setCuestionario(c);
        p.setTitulo("¿Cómo prefieres aprender?");
        p.setEnunciado("Elige la que más se parezca a ti");
        opcion(p, 1, "Uno");
        opcion(p, 2, "Dos");
        opcion(p, 3, "Tres");
        c.setPreguntaComplementaria(p);
        return c;
    }

    private static ResultadoInstrumento resultado(boolean... dominantes) {
        List<ResultadoEstilo> estilos = new ArrayList<>();
        for (int i = 0; i < dominantes.length; i++) {
            estilos.add(new ResultadoEstilo(i, "E" + i, TipoEstilo.PRIMARIO, i, 1.0, 0, 1, 100.0,
                    EstadoCalculo.CALCULADO, null, dominantes[i]));
        }
        return new ResultadoInstrumento(1, "v", estilos, null, null);
    }

    @Test
    void seHaceSoloConTodosLosEstilosDestacadosYUnEsquemaQueDestaque() {
        List<EstiloResultadoDTO> todos = List.of(estilo(true), estilo(true), estilo(true));
        List<EstiloResultadoDTO> dos = List.of(estilo(true), estilo(true), estilo(false));

        assertNotNull(EvaluacionRespuestas.complementaria(cuestionario(EsquemaInterpretacion.RELATIVO), todos));
        assertNotNull(EvaluacionRespuestas.complementaria(
                cuestionario(EsquemaInterpretacion.RELATIVO_ESCALONADO), todos));
        assertNull(EvaluacionRespuestas.complementaria(cuestionario(EsquemaInterpretacion.RELATIVO), dos));
        assertNull(EvaluacionRespuestas.complementaria(cuestionario(EsquemaInterpretacion.BAREMO), todos));
        assertNull(EvaluacionRespuestas.complementaria(cuestionario(EsquemaInterpretacion.RELATIVO),
                List.of(estilo(true))));
    }

    @Test
    void sinPreguntaDefinidaElPerfilQuedaComoSiempre() {
        Cuestionario c = cuestionario(EsquemaInterpretacion.RELATIVO_ESCALONADO);
        c.setPreguntaComplementaria(null);

        assertNull(EvaluacionRespuestas.complementaria(c, List.of(estilo(true), estilo(true))));
        assertNull(EvaluacionRespuestas.conteo(c, List.of(resultado(true, true)), Arrays.asList((OpcionComplementaria) null)));
    }

    @Test
    void soloSeEligeUnaOpcionDeLaPregunta() {
        Cuestionario c = cuestionario(EsquemaInterpretacion.RELATIVO);
        List<EstiloResultadoDTO> todos = List.of(estilo(true), estilo(true));

        assertEquals(2L, EvaluacionRespuestas.elegir(c, todos, null, 2L).getId());
        AppException ajena = assertThrows(AppException.class, () -> EvaluacionRespuestas.elegir(c, todos, null, 99L));
        assertEquals(ErrorCode.VALIDACION, ajena.getCode());
        AppException noAplica = assertThrows(AppException.class,
                () -> EvaluacionRespuestas.elegir(c, List.of(estilo(true), estilo(false)), null, 1L));
        assertEquals(ErrorCode.COMPLEMENTARIA_NO_APLICA, noAplica.getCode());
    }

    @Test
    void elConteoSigueElOrdenDeLasOpcionesYCuentaSinDeclarar() {
        Cuestionario c = cuestionario(EsquemaInterpretacion.RELATIVO);
        OpcionComplementaria tres = c.getPreguntaComplementaria().getOpciones().get(2);

        ComplementariaDTO.Conteo conteo = EvaluacionRespuestas.conteo(c,
                List.of(resultado(true, true), resultado(true, true), resultado(true, false)),
                Arrays.asList(tres, null, null));

        assertEquals("¿Cómo prefieres aprender?", conteo.titulo());
        assertEquals(List.of("Uno", "Dos", "Tres", EvaluacionRespuestas.SIN_DECLARAR),
                List.copyOf(conteo.respuestas().keySet()));
        assertEquals(Map.of("Uno", 0L, "Dos", 0L, "Tres", 1L, EvaluacionRespuestas.SIN_DECLARAR, 1L),
                conteo.respuestas());
    }

    @Test
    void noSeRespondeSobreElResultadoDeOtro() {
        ResultadoCuestionario rc = new ResultadoCuestionario();
        rc.setEstudiante(estudiante("duenyo@ufps.edu.co"));
        when(resultadoCuestionarioRepository.findById(1L)).thenReturn(Optional.of(rc));

        assertThrows(EntityNotFoundException.class,
                () -> service.responderComplementaria(1L, estudiante("otro@ufps.edu.co"), 1L));
        verify(resultadoCuestionarioRepository, never()).save(any());
    }

    @Test
    void unaVezRespondidaNoCambia() {
        ResultadoCuestionario rc = new ResultadoCuestionario();
        rc.setEstudiante(estudiante("duenyo@ufps.edu.co"));
        rc.setOpcionComplementaria(new OpcionComplementaria());
        when(resultadoCuestionarioRepository.findById(1L)).thenReturn(Optional.of(rc));

        AppException e = assertThrows(AppException.class,
                () -> service.responderComplementaria(1L, estudiante("duenyo@ufps.edu.co"), 2L));
        assertEquals(ErrorCode.COMPLEMENTARIA_YA_RESPONDIDA, e.getCode());
        verify(resultadoCuestionarioRepository, never()).save(any());
    }
}
