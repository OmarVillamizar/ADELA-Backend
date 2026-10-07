package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.PreguntaDTO;

/**
 * Un JSON de creación mal formado se rechaza con un 400 que dice qué corregir,
 * en lugar del NullPointerException que daba con las claves anteriores al
 * renombre de categoría a estilo.
 */
class ValidacionCuestionarioTest {

    private static EstiloDTO estilo(int id, String nombre) {
        EstiloDTO e = new EstiloDTO();
        e.setId(id);
        e.setNombre(nombre);
        return e;
    }

    private static PreguntaDTO pregunta(int orden, OpcionDTO... opciones) {
        PreguntaDTO p = new PreguntaDTO();
        p.setOrden(orden);
        p.setPregunta("Pregunta " + orden);
        p.setOpciones(List.of(opciones));
        return p;
    }

    private static OpcionDTO opcion(int estiloId, Double valor) {
        OpcionDTO o = new OpcionDTO();
        o.setRespuesta("Opción");
        o.setEstiloId(estiloId);
        o.setValor(valor);
        return o;
    }

    private static CuestionarioDTO cuestionario(List<EstiloDTO> estilos, List<PreguntaDTO> preguntas) {
        CuestionarioDTO c = new CuestionarioDTO();
        c.setNombre("VARK");
        c.setEstilos(estilos);
        c.setPreguntas(preguntas);
        return c;
    }

    @Test
    @DisplayName("Un JSON con 'categorias' y 'categoriaId' señala ambas claves")
    void clavesAnteriores() {
        // Jackson ignora 'categorias': estilos llega null y estiloId en 0.
        Map<String, String> e = CuestionarioService.validarEstructura(
                cuestionario(null, List.of(pregunta(1, opcion(0, 1d)))));

        assertTrue(e.get("estilos").contains("'categorias'"));
        assertEquals(1, e.size());
    }

    @Test
    @DisplayName("Una opción con un estilo que no está en la lista menciona 'categoriaId'")
    void estiloDesconocido() {
        Map<String, String> e = CuestionarioService.validarEstructura(cuestionario(
                List.of(estilo(1, "Visual")), List.of(pregunta(1, opcion(1, 1d)), pregunta(2, opcion(0, 1d)))));

        assertEquals(1, e.size());
        assertTrue(e.get("preguntas[1]").contains("'categoriaId'"));
    }

    @Test
    @DisplayName("Sin preguntas, sin opciones o sin valor es error; uno válido pasa")
    void preguntasYOpciones() {
        assertTrue(CuestionarioService.validarEstructura(cuestionario(List.of(estilo(1, "V")), null))
                .containsKey("preguntas"));
        assertTrue(CuestionarioService.validarEstructura(
                cuestionario(List.of(estilo(1, "V")), List.of(pregunta(1)))).containsKey("preguntas[0]"));
        assertTrue(CuestionarioService.validarEstructura(
                cuestionario(List.of(estilo(1, "V")), List.of(pregunta(1, opcion(1, null)))))
                .containsKey("preguntas[0]"));
        assertTrue(CuestionarioService.validarEstructura(
                cuestionario(List.of(estilo(1, "V")), List.of(pregunta(1, opcion(1, 1d))))).isEmpty());
    }
}
