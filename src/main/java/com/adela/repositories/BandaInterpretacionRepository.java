package com.adela.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.BandaInterpretacion;
import com.adela.entities.Cuestionario;

public interface BandaInterpretacionRepository extends JpaRepository<BandaInterpretacion, Long> {

    List<BandaInterpretacion> findByEstiloCuestionario(Cuestionario cuestionario);
}
