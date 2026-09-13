package com.adela.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.adela.dto.GrupoResumidoDTO;
import org.springframework.stereotype.Repository;
import com.adela.entities.Grupo;
import com.adela.entities.Profesor;

@Repository
public interface GrupoRepository extends JpaRepository<Grupo, Integer> {
    List<Grupo> findByNombre(String nombre);
    
    List<Grupo> findByProfesor(Profesor profesor);
    
    Optional<Grupo> findByProfesorAndId(Profesor profesor, int id);

    /**
     * Cuenta los estudiantes en la propia consulta. Recorrer los grupos llamando a
     * getEstudiantes().size() disparaba una consulta por grupo sobre una relación
     * LAZY: 50 grupos eran 51 viajes a base de datos solo para contar.
     */
    @Query("""
            SELECT new com.adela.dto.GrupoResumidoDTO(
                     g.id, g.nombre, p.nombre, p.email, COUNT(e))
              FROM Grupo g
              JOIN g.profesor p
              LEFT JOIN g.estudiantes e
             WHERE g.profesor = :profesor
          GROUP BY g.id, g.nombre, p.nombre, p.email
          ORDER BY g.nombre
            """)
    List<GrupoResumidoDTO> resumirPorProfesor(@Param("profesor") Profesor profesor);
}
