package com.adela.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.adela.entities.Opcion;
import com.adela.entities.ResultadoPregunta;

public interface ResultadoPreguntaRepository extends JpaRepository<ResultadoPregunta, Long> {
    @Transactional
    @Modifying
    @Query("DELETE FROM ResultadoPregunta rp WHERE rp.opcion = :opcion")
    void deleteByOpcion(Opcion opcion);
}
