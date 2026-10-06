package com.adela.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.adela.entities.BandaInterpretacion;
import com.adela.entities.Cuestionario;

public interface BandaInterpretacionRepository extends JpaRepository<BandaInterpretacion, Long> {

    List<BandaInterpretacion> findByEstiloCuestionario(Cuestionario cuestionario);

    @Modifying
    @Query("DELETE FROM BandaInterpretacion b "
            + "WHERE b.estilo IN (SELECT e FROM Estilo e WHERE e.cuestionario = :cuestionario)")
    void borrarDeCuestionario(@Param("cuestionario") Cuestionario cuestionario);
}
