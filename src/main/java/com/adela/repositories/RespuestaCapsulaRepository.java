package com.adela.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.Capsula;
import com.adela.entities.RespuestaCapsula;

public interface RespuestaCapsulaRepository extends JpaRepository<RespuestaCapsula, Long> {

    Optional<RespuestaCapsula> findByCodigo(String codigo);

    Optional<RespuestaCapsula> findByCapsulaAndIntento(Capsula capsula, UUID intento);

    boolean existsByCodigo(String codigo);

    long countByCapsula(Capsula capsula);

    /**
     * Trae las opciones elegidas en la misma consulta: el reporte califica cada
     * respuesta y sin esto haría una consulta más por respuesta.
     */
    @EntityGraph(attributePaths = "opciones")
    List<RespuestaCapsula> findByCapsulaOrderByRespondidaEn(Capsula capsula);
}
