package com.adela.entities;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Fila de la tabla de distancia de paso del esquema RELATIVO_ESCALONADO (VARK):
 * si la suma de puntajes está en [totalMin, totalMax], dos estilos seguidos con
 * diferencia de hasta distancia forman parte del mismo perfil.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class EscalonRelativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuestionario_id", nullable = false)
    private Cuestionario cuestionario;

    @Column(name = "total_min", nullable = false)
    private double totalMin;

    @Column(name = "total_max", nullable = false)
    private double totalMax;

    @Column(nullable = false)
    private double distancia;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof EscalonRelativo))
            return false;
        EscalonRelativo otro = (EscalonRelativo) o;
        return getId() != null && Objects.equals(getId(), otro.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
