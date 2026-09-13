package com.adela.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.Cuestionario;
import com.adela.entities.Pregunta;

public interface PreguntaRepository extends JpaRepository<Pregunta, Long> {
    List<Pregunta> findByCuestionario(Cuestionario c);
}
