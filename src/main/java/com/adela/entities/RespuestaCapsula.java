package com.adela.entities;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
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

    /** Id de la opción elegida -> cantidad (1, el rango en jerarquía o los puntos en reparto). */
    @ElementCollection
    @CollectionTable(name = "respuesta_capsula_opcion", joinColumns = @JoinColumn(name = "respuesta_id"))
    @MapKeyColumn(name = "opcion_id")
    @Column(name = "cantidad", nullable = false)
    private Map<Long, Double> cantidades = new HashMap<>();

    /** Respuesta a la pregunta complementaria, como en los grupos: una vez y fija. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opcion_complementaria_id")
    private OpcionComplementaria opcionComplementaria;

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
