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

    List<RespuestaCapsula> findByCapsulaOrderByRespondidaEn(Capsula capsula);

    /**
     * Suma por respuesta y categoría en una sola consulta: el reporte no recorre
     * las opciones de cada respuesta. Una respuesta sin opciones elegidas no
     * aparece aquí; el reporte parte de findByCapsulaOrderByRespondidaEn.
     */
    @Query("""
            SELECT new com.adela.dto.PuntajeRespuestaCapsulaDTO(r.id, o.categoria.id, SUM(o.valor))
              FROM RespuestaCapsula r
              JOIN r.opciones o
             WHERE r.capsula = :capsula
          GROUP BY r.id, o.categoria.id
            """)
    List<PuntajeRespuestaCapsulaDTO> puntajesPorCategoria(@Param("capsula") Capsula capsula);
}
