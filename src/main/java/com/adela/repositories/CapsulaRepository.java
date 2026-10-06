package com.adela.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.adela.dto.CapsulaDTO;
import com.adela.entities.Capsula;
import com.adela.entities.Cuestionario;
import com.adela.entities.Profesor;

public interface CapsulaRepository extends JpaRepository<Capsula, Long> {

    Optional<Capsula> findByProfesorAndId(Profesor profesor, Long id);

    Optional<Capsula> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    @Query("""
            SELECT new com.adela.dto.CapsulaDTO(
                     c.id, c.codigo, c.nombre, q.id, q.nombre, q.siglas,
                     c.modoIdentificacion, c.abierta, c.creadaEn, COUNT(r))
              FROM Capsula c
              JOIN c.cuestionario q
              LEFT JOIN RespuestaCapsula r ON r.capsula = c
             WHERE c.profesor = :profesor
          GROUP BY c.id, c.codigo, c.nombre, q.id, q.nombre, q.siglas,
                   c.modoIdentificacion, c.abierta, c.creadaEn
          ORDER BY c.creadaEn DESC
            """)
    List<CapsulaDTO> resumirPorProfesor(@Param("profesor") Profesor profesor);

    /**
     * Borrado masivo: las respuestas y sus opciones caen por ON DELETE CASCADE en
     * la base de datos, sin cargarlas en memoria.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM Capsula c WHERE c.cuestionario = :cuestionario")
    void deleteByCuestionario(@Param("cuestionario") Cuestionario cuestionario);
}
