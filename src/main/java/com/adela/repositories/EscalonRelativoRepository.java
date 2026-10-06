package com.adela.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.adela.entities.Cuestionario;
import com.adela.entities.EscalonRelativo;

public interface EscalonRelativoRepository extends JpaRepository<EscalonRelativo, Long> {

    List<EscalonRelativo> findByCuestionario(Cuestionario cuestionario);

    @Modifying
    @Query("DELETE FROM EscalonRelativo e WHERE e.cuestionario = :cuestionario")
    void borrarDeCuestionario(@Param("cuestionario") Cuestionario cuestionario);
}
