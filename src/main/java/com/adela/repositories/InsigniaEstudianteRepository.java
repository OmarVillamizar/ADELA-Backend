package com.adela.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.adela.entities.Insignia;
import com.adela.entities.InsigniaEstudiante;

public interface InsigniaEstudianteRepository extends JpaRepository<InsigniaEstudiante, InsigniaEstudiante.Id> {

    List<InsigniaEstudiante> findByIdEstudianteEmailOrderByObtenidaEn(String estudianteEmail);

    /**
     * Una sola sentencia e idempotente: repetir la acción no duplica ni falla, y
     * dos peticiones simultáneas no chocan en la clave primaria como lo haría un
     * "consultar y luego insertar".
     */
    @Modifying
    @Query(value = "INSERT INTO insignia_estudiante (estudiante_email, codigo, obtenida_en, celebrada) "
            + "VALUES (:email, :codigo, now(), false) ON CONFLICT DO NOTHING", nativeQuery = true)
    int otorgar(String email, String codigo);

    @Modifying
    @Query("UPDATE InsigniaEstudiante i SET i.celebrada = true "
            + "WHERE i.id.estudianteEmail = :email AND i.id.codigo = :codigo")
    int marcarCelebrada(String email, Insignia codigo);
}
