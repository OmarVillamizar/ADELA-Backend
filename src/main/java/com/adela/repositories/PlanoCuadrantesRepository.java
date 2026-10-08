package com.adela.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.PlanoCuadrantes;

/**
 * La clave primaria es el id del cuestionario, así que el plano de un
 * cuestionario c se busca con findById(c.getId()).
 */
public interface PlanoCuadrantesRepository extends JpaRepository<PlanoCuadrantes, Long> {
}
