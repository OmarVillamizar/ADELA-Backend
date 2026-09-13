package com.adela.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    
}
