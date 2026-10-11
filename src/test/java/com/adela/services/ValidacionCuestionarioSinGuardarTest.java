package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.calificacion.EscalaBanda;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.FormatoItem;
import com.adela.calificacion.TipoEstilo;
import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.InterpretacionDTO.BandaDTO;
import com.adela.dto.InterpretacionDTO.EscalonDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.OpcionDTO.PesoDTO;
import com.adela.dto.PreguntaDTO;

/**
 * Validar sin guardar corre los mismos validadores que el POST, con estilos
 * transitorios. Si una convención del asistente va a llenar los números más
 * tarde, no se exige lo que esa convención llena.
 */
class ValidacionCuestionarioSinGuardarTest {

    private static EstiloDTO estilo(int id, String nombre, TipoEstilo tipo) {
        EstiloDTO e = new EstiloDTO();
        e.setId(id);
        e.setNombre(nombre);
        e.setTipo(tipo);
        return e;
    }

    private static PreguntaDTO pregunta(int orden) {
        OpcionDTO a = new OpcionDTO();
        a.setOrden(1);
        a.setRespuesta("Sí");
        a.setPesos(List.of(new PesoDTO(1, 1d)));
        OpcionDTO b = new OpcionDTO();
        b.setOrden(2);
        b.setRespuesta("No");
        b.setPesos(List.of());
        PreguntaDTO p = new PreguntaDTO();
        p.setOrden(orden);
        p.setPregunta("Pregunta " + orden);
        p.setFormato(FormatoItem.UNICA);
        p.setOpciones(List.of(a, b));
        return p;
    }

    private static CuestionarioDTO cuestionario(InterpretacionDTO interpretacion) {
        CuestionarioDTO c = new CuestionarioDTO();
        c.setNombre("Escala");
        c.setEstilos(List.of(estilo(1, "Activo", null), estilo(2, "Total", TipoEstilo.COMPUESTO)));
        c.getEstilos().get(1).setCoeficientes(List.of(new EstiloDTO.CoeficienteDTO(1, 1d)));
        c.setPreguntas(List.of(pregunta(1), pregunta(2)));
        c.setInterpretacion(interpretacion);
        return c;
    }

    private static InterpretacionDTO interpretacion(EsquemaInterpretacion esquema, List<BandaDTO> bandas,
            List<EscalonDTO> escalones) {
        return new InterpretacionDTO(esquema, 10d, null, bandas, escalones);
    }

    @Test
    @DisplayName("Un cuestionario válido, con o sin interpretación, devuelve un mapa vacío")
    void validoDevuelveVacio() {
        BandaDTO banda = new BandaDTO("Activo", EscalaBanda.BRUTO, 0d, 2d, "Todo", 1);

        assertTrue(ValidacionCuestionario.validarSinGuardar(cuestionario(null), null).isEmpty());
        assertTrue(ValidacionCuestionario.validarSinGuardar(
                cuestionario(interpretacion(EsquemaInterpretacion.BAREMO, List.of(banda), List.of())), null)
                .isEmpty());
    }

    @Test
    @DisplayName("Mezcla los errores de estructura y de interpretación con sus claves originales")
    void mezclaErrores() {
        CuestionarioDTO dto = cuestionario(interpretacion(EsquemaInterpretacion.BAREMO,
                List.of(new BandaDTO("Inexistente", EscalaBanda.BRUTO, 0d, 1d, "Bajo", 1)), List.of()));
        dto.getPreguntas().get(0).setOpciones(List.of());

        Map<String, String> errores = ValidacionCuestionario.validarSinGuardar(dto, null);

        assertTrue(errores.containsKey("preguntas[0]"), errores.toString());
        assertTrue(errores.containsKey("bandas[0]"), errores.toString());
    }

    @Test
    @DisplayName("NIVEL_SUPERIOR sin bandas falla, salvo que la lectura DOMINANCIA las vaya a llenar")
    void dominanciaNeutralizada() {
        CuestionarioDTO dto = cuestionario(interpretacion(EsquemaInterpretacion.NIVEL_SUPERIOR, List.of(), List.of()));

        assertTrue(ValidacionCuestionario.validarSinGuardar(dto, null).containsKey("bandas"));
        assertTrue(ValidacionCuestionario.validarSinGuardar(dto, "NIVELES").containsKey("bandas"));
        assertTrue(ValidacionCuestionario.validarSinGuardar(dto, "DOMINANCIA").isEmpty());
    }

    @Test
    @DisplayName("RELATIVO_ESCALONADO sin escalones falla, salvo que la lectura ESCALONADO los vaya a llenar")
    void escalonadoNeutralizado() {
        CuestionarioDTO dto = cuestionario(
                interpretacion(EsquemaInterpretacion.RELATIVO_ESCALONADO, List.of(), List.of()));

        assertTrue(ValidacionCuestionario.validarSinGuardar(dto, null).containsKey("escalones"));
        assertTrue(ValidacionCuestionario.validarSinGuardar(dto, "PREDOMINANTE").containsKey("escalones"));
        assertTrue(ValidacionCuestionario.validarSinGuardar(dto, "ESCALONADO").isEmpty());
    }

    @Test
    @DisplayName("La neutralización no oculta otros errores de la interpretación")
    void neutralizacionNoOcultaOtrosErrores() {
        CuestionarioDTO dto = cuestionario(interpretacion(EsquemaInterpretacion.NIVEL_SUPERIOR,
                List.of(new BandaDTO("Inexistente", EscalaBanda.BRUTO, 0d, 1d, "Bajo", 1)), List.of()));

        Map<String, String> errores = ValidacionCuestionario.validarSinGuardar(dto, "DOMINANCIA");

        assertEquals(List.of("bandas[0]"), List.copyOf(errores.keySet()));
        assertFalse(errores.containsKey("bandas"));
    }

    @Test
    @DisplayName("Un esquema que no corresponde a la lectura no se neutraliza")
    void lecturaQueNoCorresponde() {
        CuestionarioDTO dto = cuestionario(interpretacion(EsquemaInterpretacion.NIVEL_SUPERIOR, List.of(), List.of()));

        assertTrue(ValidacionCuestionario.validarSinGuardar(dto, "ESCALONADO").containsKey("bandas"));
    }
}
