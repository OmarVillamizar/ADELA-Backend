package com.adela.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import com.adela.dto.CuestionarioResumidoDTO;
import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.Cuestionario;

public interface CuestionarioRepository extends JpaRepository<Cuestionario, Long> {

    /**
     * Igual que resumirPorProfesor: getPreguntas().size() por cuestionario era una
     * consulta extra por fila del listado.
     */
    @Query("""
            SELECT new com.adela.dto.CuestionarioResumidoDTO(
                     c.id, c.nombre, c.siglas, c.version, c.autor, c.descripcion, COUNT(p))
              FROM Cuestionario c
              LEFT JOIN c.preguntas p
          GROUP BY c.id, c.nombre, c.siglas, c.version, c.autor, c.descripcion
          ORDER BY c.nombre
            """)
    List<CuestionarioResumidoDTO> resumirTodos();
}
