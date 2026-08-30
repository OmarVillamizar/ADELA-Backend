package com.example.chaea.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.example.chaea.entities.Cuestionario;
import com.example.chaea.entities.Estudiante;
import com.example.chaea.entities.Grupo;
import com.example.chaea.entities.ResultadoCuestionario;

public interface ResultadoCuestionarioRepository extends JpaRepository<ResultadoCuestionario, Long> {
    
	// Método para encontrar las asignaciones pendientes de un Cuestionario para un
    // Estudiante. Devuelve una lista porque el mismo cuestionario puede estar
    // asignado al estudiante en varios grupos a la vez.
    List<ResultadoCuestionario> findByCuestionarioAndEstudianteAndFechaResolucionIsNull(Cuestionario cuestionario,
            Estudiante estudiante);
    Optional<ResultadoCuestionario> findByCuestionarioAndEstudianteAndGrupo(
    	    Cuestionario cuestionario, 
    	    Estudiante estudiante, 
    	    Grupo grupo
    	);
    // Método para encontrar una lista de ResultadoCuestionario por Estudiante y
    // fechaResolucion null
    List<ResultadoCuestionario> findByEstudianteAndFechaResolucionIsNull(Estudiante estudiante);
    
    List<ResultadoCuestionario> findByEstudianteAndBloqueadoFalse(Estudiante estudiante);
    
    List<ResultadoCuestionario> findByGrupo(Grupo grupo);
    
    List<ResultadoCuestionario> findByGrupoAndCuestionario(Grupo grupo, Cuestionario cuestionario);
    
 // En ResultadoCuestionarioRepository
    List<ResultadoCuestionario> findByEstudianteAndGrupo(Estudiante estudiante, Grupo grupo);
    
    @Transactional
    @Modifying
    @Query("DELETE FROM ResultadoCuestionario rc WHERE rc.cuestionario = :cuestionario")
    void deleteByCuestionario(Cuestionario cuestionario);
}
