package com.adela.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.entities.Estilo;
import com.adela.entities.Cuestionario;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;

/**
 * El DTO tiene que cumplir dos cosas a la vez, y ya se rompieron por separado:
 * no filtrar el baremo (SEC-05) y seguir trayendo lo que la vista de
 * cuestionarios necesita para pintarse.
 */
class CuestionarioParaResponderDTOTest {

    private static Cuestionario cuestionarioDePrueba() {
        Cuestionario c = new Cuestionario();
        c.setId(1L);
        c.setNombre("VARK");

        Estilo visual = new Estilo();
        visual.setNombre("Visual");
        visual.setValorMinimo(0d);
        visual.setValorMaximo(16d);

        Estilo auditivo = new Estilo();
        auditivo.setNombre("Auditivo");

        Opcion opcion = new Opcion();
        opcion.setId(10L);
        opcion.setRespuesta("Mirar un mapa");
        opcion.setOrden(1);
        opcion.setValor(3d);
        opcion.setEstilo(visual);

        Pregunta pregunta = new Pregunta();
        pregunta.setId(5L);
        pregunta.setPregunta("¿Cómo llegas a un sitio nuevo?");
        pregunta.setOrden(1);
        pregunta.setOpciones(Set.of(opcion));

        c.setPreguntas(Set.of(pregunta));
        c.setEstilos(Set.of(visual, auditivo));
        return c;
    }

    @Test
    @DisplayName("Expone los nombres de los estilos: la vista los lista")
    void exponeLosNombresDeLosEstilos() {
        List<String> nombres = CuestionarioParaResponderDTO.from(cuestionarioDePrueba()).estilos().stream()
                .map(CuestionarioParaResponderDTO.EstiloResponderDTO::nombre).toList();

        assertEquals(List.of("Auditivo", "Visual"), nombres);
    }

    @Test
    @DisplayName("SEC-05: una opción no lleva su valor ni su estilo")
    void laOpcionNoLlevaElBaremo() {
        var opcion = CuestionarioParaResponderDTO.from(cuestionarioDePrueba()).preguntas().get(0).opciones().get(0);

        // El record solo declara id, respuesta y orden. Si alguien añade el valor o
        // el estilo, el estudiante puede calcular su perfil antes de responder.
        assertEquals(3, opcion.getClass().getRecordComponents().length);
        List<String> campos = List.of(opcion.getClass().getRecordComponents()).stream()
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertTrue(campos.containsAll(List.of("id", "respuesta", "orden")), "faltan los campos que sí debe llevar");
        assertTrue(!campos.contains("valor") && !campos.contains("estilo"), "el DTO expone el baremo: " + campos);
    }
}
