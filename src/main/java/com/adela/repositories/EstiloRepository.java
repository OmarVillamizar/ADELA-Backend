package com.adela.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.Estilo;

public interface EstiloRepository extends JpaRepository<Estilo, Long> {
    
}
