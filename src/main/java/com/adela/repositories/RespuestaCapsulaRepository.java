package com.adela.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.adela.dto.PuntajeRespuestaCapsulaDTO;
import com.adela.entities.Capsula;
import com.adela.entities.RespuestaCapsula;

public interface RespuestaCapsulaRepository extends JpaRepository<RespuestaCapsula, Long> {

    Optional<RespuestaCapsula> findByCodigo(String codigo);

    Optional<RespuestaCapsula> findByCapsulaAndIntento(Capsula capsula, UUID intento);

    boolean existsByCodigo(String codigo);

    long countByCapsula(Capsula capsula);

    /**
     * Suma por respuesta y categoría en una sola consulta: el reporte no recorre
     * las opciones de cada respuesta. Una respuesta sin opciones elegidas no
     * aparece aquí; el total de respuestas sale de countByCapsula.
     */
    @Query("""
            SELECT new com.adela.dto.PuntajeRespuestaCapsulaDTO(
                     r.id, r.nombre, r.respondidaEn, o.categoria.id, SUM(o.valor))
              FROM RespuestaCapsula r
              JOIN r.opciones o
             WHERE r.capsula = :capsula
          GROUP BY r.id, r.nombre, r.respondidaEn, o.categoria.id
          ORDER BY r.respondidaEn
            """)
    List<PuntajeRespuestaCapsulaDTO> puntajesPorCategoria(@Param("capsula") Capsula capsula);
}
