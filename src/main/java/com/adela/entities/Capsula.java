package com.adela.entities;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Enlace público que un profesor comparte para que cualquiera resuelva un
 * cuestionario sin cuenta. El código es lo único que viaja en el enlace; el id
 * secuencial nunca sale hacia la ruta pública.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Capsula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 8)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profesor_email", referencedColumnName = "email", nullable = false)
    private Profesor profesor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuestionario_id", nullable = false)
    private Cuestionario cuestionario;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_identificacion", nullable = false, length = 10)
    private ModoIdentificacion modoIdentificacion;

    @Column(nullable = false)
    private boolean abierta = true;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Capsula))
            return false;
        Capsula otra = (Capsula) o;
        return getId() != null && Objects.equals(getId(), otra.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
