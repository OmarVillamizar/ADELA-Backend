package com.adela.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.adela.entities.Estudiante;

import org.springframework.stereotype.Repository;

@Repository
public interface EstudianteRepository extends JpaRepository<Estudiante, String> {
    
    Optional<Estudiante> findByCodigo(String codigo);

    /**
     * Búsqueda paginada para el autocompletado. El listado completo se descargaba
     * entero en cada apertura de /grupos solo para filtrar en el navegador.
     */
    Page<Estudiante> findByEmailContainingIgnoreCaseOrNombreContainingIgnoreCase(String email, String nombre,
            Pageable pageable);
}