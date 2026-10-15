package com.adela.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.adela.entities.Grupo;
import com.adela.entities.Profesor;

/** PERF-02: resumir un grupo dentro de un resultado no carga sus estudiantes. */
class GrupoResumidoDTOTest {

    @Test
    @DisplayName("from(Grupo) no toca la colección de estudiantes")
    void noCargaEstudiantes() {
        Profesor profesor = new Profesor();
        profesor.setEmail("profe@ufps.edu.co");
        Grupo grupo = mock(Grupo.class);
        when(grupo.getId()).thenReturn(7);
        when(grupo.getNombre()).thenReturn("Física");
        when(grupo.getProfesor()).thenReturn(profesor);

        GrupoResumidoDTO dto = GrupoResumidoDTO.from(grupo);

        assertEquals(7, dto.getId());
        assertNull(dto.getNumEstudiantes());
        verify(grupo, never()).getEstudiantes();
    }

    @Test
    @DisplayName("La proyección de la lista de grupos sí trae el conteo")
    void proyeccionConConteo() {
        assertEquals(12, new GrupoResumidoDTO(1, "Física", "Profe", "p@ufps.edu.co", 12L).getNumEstudiantes());
    }
}
