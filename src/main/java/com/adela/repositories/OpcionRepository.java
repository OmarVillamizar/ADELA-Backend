package com.adela.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.Opcion;

public interface OpcionRepository extends JpaRepository<Opcion, Long> {
    
}
