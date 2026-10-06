package com.adela.entities;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sin relación JPA a Estudiante: solo se consulta por el correo del usuario
 * autenticado y cargar la entidad sería un join que nadie usa.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "insignia_estudiante")
public class InsigniaEstudiante {

    @EmbeddedId
    private Id id;

    @Column(name = "obtenida_en", nullable = false)
    private Instant obtenidaEn;

    @Column(nullable = false)
    private boolean celebrada;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "estudiante_email", length = 100)
        private String estudianteEmail;

        @Enumerated(EnumType.STRING)
        @Column(length = 40)
        private Insignia codigo;
    }
}
