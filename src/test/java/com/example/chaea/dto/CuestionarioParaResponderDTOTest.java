package com.example.chaea.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.chaea.entities.Categoria;
import com.example.chaea.entities.Cuestionario;
import com.example.chaea.entities.Opcion;
import com.example.chaea.entities.Pregunta;

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

        Categoria visual = new Categoria();
        visual.setNombre("Visual");
        visual.setValorMinimo(0d);
        visual.setValorMaximo(16d);

        Categoria auditivo = new Categoria();
        auditivo.setNombre("Auditivo");

        Opcion opcion = new Opcion();
        opcion.setId(10L);
        opcion.setRespuesta("Mirar un mapa");
        opcion.setOrden(1);
        opcion.setValor(3d);
        opcion.setCategoria(visual);

        Pregunta pregunta = new Pregunta();
        pregunta.setId(5L);
        pregunta.setPregunta("¿Cómo llegas a un sitio nuevo?");
        pregunta.setOrden(1);
        pregunta.setOpciones(Set.of(opcion));

        c.setPreguntas(Set.of(pregunta));
        c.setCategorias(Set.of(visual, auditivo));
        return c;
    }

    @Test
    @DisplayName("Expone los nombres de las categorías: la vista los lista como estilos")
    void exponeLosNombresDeLasCategorias() {
        List<String> nombres = CuestionarioParaResponderDTO.from(cuestionarioDePrueba()).categorias().stream()
                .map(CuestionarioParaResponderDTO.CategoriaResponderDTO::nombre).toList();

        assertEquals(List.of("Auditivo", "Visual"), nombres);
    }

    @Test
    @DisplayName("SEC-05: una opción no lleva su valor ni su categoría")
    void laOpcionNoLlevaElBaremo() {
        var opcion = CuestionarioParaResponderDTO.from(cuestionarioDePrueba()).preguntas().get(0).opciones().get(0);

        // El record solo declara id, respuesta y orden. Si alguien añade el valor o
        // la categoría, el estudiante puede calcular su perfil antes de responder.
        assertEquals(3, opcion.getClass().getRecordComponents().length);
        List<String> campos = List.of(opcion.getClass().getRecordComponents()).stream()
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertTrue(campos.containsAll(List.of("id", "respuesta", "orden")), "faltan los campos que sí debe llevar");
        assertTrue(!campos.contains("valor") && !campos.contains("categoria"), "el DTO expone el baremo: " + campos);
    }
}
