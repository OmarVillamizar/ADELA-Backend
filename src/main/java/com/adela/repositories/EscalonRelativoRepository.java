package com.adela.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.Cuestionario;
import com.adela.entities.EscalonRelativo;

public interface EscalonRelativoRepository extends JpaRepository<EscalonRelativo, Long> {

    List<EscalonRelativo> findByCuestionario(Cuestionario cuestionario);
}
