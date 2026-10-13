package com.adela.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.adela.dto.CuestionarioResumidoDTO;
import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.Cuestionario;
import com.adela.entities.Profesor;

public interface CuestionarioRepository extends JpaRepository<Cuestionario, Long> {

    /**
     * Generales y propios, sin archivados. Igual que resumirPorProfesor:
     * getPreguntas().size() por cuestionario era una consulta extra por fila.
     */
    @Query("""
            SELECT new com.adela.dto.CuestionarioResumidoDTO(
                     c.id, c.nombre, c.siglas, c.version, c.autor, c.descripcion, COUNT(p),
                     CASE WHEN c.profesor IS NULL THEN true ELSE false END)
              FROM Cuestionario c
              LEFT JOIN c.preguntas p
             WHERE c.archivado = false
               AND (c.profesor IS NULL OR c.profesor = :profesor)
          GROUP BY c.id, c.nombre, c.siglas, c.version, c.autor, c.descripcion, c.profesor
          ORDER BY c.nombre
            """)
    List<CuestionarioResumidoDTO> resumirVisibles(@Param("profesor") Profesor profesor);

    /**
     * General o propio, aunque esté archivado: los reportes y el detalle de lo
     * que ya se aplicó deben seguir abriéndose.
     */
    @Query("""
            SELECT c FROM Cuestionario c
             WHERE c.id = :id
               AND (c.profesor IS NULL OR c.profesor = :profesor)
            """)
    Optional<Cuestionario> findVisible(@Param("id") Long id, @Param("profesor") Profesor profesor);

    /** Visible y no archivado: lo único que se puede aplicar a un grupo, un estudiante o una cápsula. */
    @Query("""
            SELECT c FROM Cuestionario c
             WHERE c.id = :id
               AND c.archivado = false
               AND (c.profesor IS NULL OR c.profesor = :profesor)
            """)
    Optional<Cuestionario> findAsignable(@Param("id") Long id, @Param("profesor") Profesor profesor);
}
