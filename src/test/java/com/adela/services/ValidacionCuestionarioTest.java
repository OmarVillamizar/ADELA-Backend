package com.adela.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.calificacion.FormatoItem;
import com.adela.calificacion.TipoEstilo;
import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.EstiloDTO.CoeficienteDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.OpcionDTO.PesoDTO;
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

    private static EstiloDTO compuesto(int id, String nombre, CoeficienteDTO... coeficientes) {
        EstiloDTO e = estilo(id, nombre);
        e.setTipo(TipoEstilo.COMPUESTO);
        e.setCoeficientes(List.of(coeficientes));
        return e;
    }

    private static PreguntaDTO pregunta(int orden, FormatoItem formato, OpcionDTO... opciones) {
        PreguntaDTO p = new PreguntaDTO();
        p.setOrden(orden);
        p.setPregunta("Pregunta " + orden);
        p.setFormato(formato);
        p.setOpciones(List.of(opciones));
        return p;
    }

    private static PreguntaDTO pregunta(int orden, OpcionDTO... opciones) {
        return pregunta(orden, FormatoItem.UNICA, opciones);
    }

    private static OpcionDTO opcion(PesoDTO... pesos) {
        OpcionDTO o = new OpcionDTO();
        o.setRespuesta("Opción");
        o.setPesos(List.of(pesos));
        return o;
    }

    private static OpcionDTO opcion(int estiloId, Double valor) {
        return opcion(new PesoDTO(estiloId, valor));
    }

    private static CuestionarioDTO cuestionario(List<EstiloDTO> estilos, List<PreguntaDTO> preguntas) {
        CuestionarioDTO c = new CuestionarioDTO();
        c.setNombre("VARK");
        c.setEstilos(estilos);
        c.setPreguntas(preguntas);
        return c;
    }

    private static Map<String, String> validar(List<EstiloDTO> estilos, PreguntaDTO... preguntas) {
        return CuestionarioService.validarEstructura(cuestionario(estilos, List.of(preguntas)));
    }

    private static final List<EstiloDTO> KOLB = List.of(estilo(1, "CE"), estilo(2, "RO"), estilo(3, "AC"),
            estilo(4, "AE"), compuesto(5, "AC-CE", new CoeficienteDTO(3, 1d), new CoeficienteDTO(1, -1d)));

    @Test
    @DisplayName("Un JSON con 'categorias' señala la clave")
    void clavesAnteriores() {
        // Jackson ignora 'categorias': estilos llega null.
        Map<String, String> e = validar(null, pregunta(1, opcion(0, 1d)));

        assertTrue(e.get("estilos").contains("'categorias'"));
        assertEquals(1, e.size());
    }

    @Test
    @DisplayName("Una opción con el formato anterior (valor y estiloId) indica cómo pasar a 'pesos'")
    void opcionSinPesos() {
        OpcionDTO anterior = new OpcionDTO();
        anterior.setRespuesta("Opción");
        Map<String, String> e = validar(List.of(estilo(1, "V")), pregunta(1, anterior));

        assertTrue(e.get("preguntas[0]").contains("'pesos'"));
    }

    @Test
    @DisplayName("Un peso a un estilo que no está en la lista se señala en su pregunta")
    void estiloDesconocido() {
        Map<String, String> e = validar(List.of(estilo(1, "Visual")), pregunta(1, opcion(1, 1d)),
                pregunta(2, opcion(7, 1d)));

        assertEquals(1, e.size());
        assertTrue(e.get("preguntas[1]").contains("estilo 7"));
    }

    @Test
    @DisplayName("Sin preguntas, sin opciones, sin formato o con peso vacío es error; uno válido pasa")
    void preguntasYOpciones() {
        List<EstiloDTO> v = List.of(estilo(1, "V"));
        assertTrue(CuestionarioService.validarEstructura(cuestionario(v, null)).containsKey("preguntas"));
        assertTrue(validar(v, pregunta(1)).containsKey("preguntas[0]"));
        assertTrue(validar(v, pregunta(1, (FormatoItem) null, opcion(1, 1d))).containsKey("preguntas[0]"));
        assertTrue(validar(v, pregunta(1, opcion(1, null))).containsKey("preguntas[0]"));
        assertTrue(validar(v, pregunta(1, opcion(1, 1d))).isEmpty());
    }

    @Test
    @DisplayName("Kolb válido: opciones con varios pesos, opción sin pesos y compuesto AC-CE")
    void kolbValido() {
        PreguntaDTO p = pregunta(1, FormatoItem.JERARQUIA, opcion(1, 1d), opcion(2, 1d),
                opcion(new PesoDTO(3, 1d), new PesoDTO(4, 0.5)), opcion());
        assertTrue(validar(KOLB, p).isEmpty());
    }

    @Test
    @DisplayName("Nombres de estilo repetidos o sin ningún primario son error")
    void estilosInvalidos() {
        assertTrue(validar(List.of(estilo(1, "V"), estilo(2, "V")), pregunta(1, opcion(1, 1d)))
                .containsKey("estilos[1]"));
        assertTrue(validar(List.of(compuesto(1, "X", new CoeficienteDTO(1, 1d))), pregunta(1, opcion()))
                .containsKey("estilos"));
    }

    @Test
    @DisplayName("Un compuesto solo combina primarios, con coeficientes no nulos; un primario no lleva")
    void compuestos() {
        EstiloDTO a = estilo(1, "A");
        EstiloDTO ab = compuesto(3, "A-B", new CoeficienteDTO(1, 1d), new CoeficienteDTO(2, -1d));
        PreguntaDTO p = pregunta(1, opcion(1, 1d));

        // Referencia a otro compuesto.
        Map<String, String> e = validar(List.of(a, estilo(2, "B"), ab,
                compuesto(4, "X", new CoeficienteDTO(3, 1d))), p);
        assertTrue(e.get("estilos[3]").contains("no es un primario"));
        // Sin coeficientes, coeficiente 0, primario con coeficientes.
        assertTrue(validar(List.of(a, compuesto(2, "X")), p).containsKey("estilos[1]"));
        assertTrue(validar(List.of(a, compuesto(2, "X", new CoeficienteDTO(1, 0d))), p).containsKey("estilos[1]"));
        EstiloDTO primarioConCoef = estilo(2, "B");
        primarioConCoef.setCoeficientes(List.of(new CoeficienteDTO(1, 1d)));
        assertTrue(validar(List.of(a, primarioConCoef), p).containsKey("estilos[1]"));
    }

    @Test
    @DisplayName("Un peso no puede apuntar a un compuesto")
    void pesoACompuesto() {
        assertTrue(validar(KOLB, pregunta(1, opcion(5, 1d))).get("preguntas[0]").contains("no es un primario"));
    }

    @Test
    @DisplayName("Reglas por formato: reparto con puntos, jerarquía con 2+ opciones, mínimo y máximo coherentes")
    void formatos() {
        List<EstiloDTO> v = List.of(estilo(1, "V"));

        PreguntaDTO reparto = pregunta(1, FormatoItem.REPARTO, opcion(1, 1d), opcion());
        assertTrue(validar(v, reparto).containsKey("preguntas[0]"));
        reparto.setPuntosRepartir(5);
        assertTrue(validar(v, reparto).isEmpty());

        PreguntaDTO unicaConPuntos = pregunta(1, opcion(1, 1d));
        unicaConPuntos.setPuntosRepartir(5);
        assertTrue(validar(v, unicaConPuntos).containsKey("preguntas[0]"));

        assertTrue(validar(v, pregunta(1, FormatoItem.JERARQUIA, opcion(1, 1d))).containsKey("preguntas[0]"));

        PreguntaDTO multiple = pregunta(1, FormatoItem.MULTIPLE, opcion(1, 1d), opcion(), opcion());
        multiple.setMinSelecciones(2);
        multiple.setMaxSelecciones(1);
        assertTrue(validar(v, multiple).containsKey("preguntas[0]"));
        multiple.setMaxSelecciones(4);
        assertTrue(validar(v, multiple).containsKey("preguntas[0]"));
        multiple.setMaxSelecciones(3);
        assertTrue(validar(v, multiple).isEmpty());

        PreguntaDTO unicaConMax = pregunta(1, opcion(1, 1d));
        unicaConMax.setMaxSelecciones(1);
        assertTrue(validar(v, unicaConMax).containsKey("preguntas[0]"));
    }
}
