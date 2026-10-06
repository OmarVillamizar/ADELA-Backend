package com.adela.entities;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una resolución de una cápsula. Guarda solo las opciones elegidas: el puntaje
 * se calcula al leer, igual que en las asignaciones de grupo.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RespuestaCapsula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capsula_id", nullable = false)
    private Capsula capsula;

    @Column(nullable = false, unique = true, length = 12)
    private String codigo;

    @Column(nullable = false)
    private UUID intento;

    @Column(length = 60)
    private String nombre;

    @Column(name = "respondida_en", nullable = false)
    private Instant respondidaEn;

    @ManyToMany
    @JoinTable(name = "respuesta_capsula_opcion",
            joinColumns = @JoinColumn(name = "respuesta_id"),
            inverseJoinColumns = @JoinColumn(name = "opcion_id"))
    private Set<Opcion> opciones = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RespuestaCapsula))
            return false;
        RespuestaCapsula otra = (RespuestaCapsula) o;
        return getId() != null && Objects.equals(getId(), otra.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
