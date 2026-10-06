package com.adela.entities;

import java.util.Objects;

import com.adela.calificacion.EscalaBanda;

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
 * Nivel de un estilo según el baremo del manual: si el puntaje (directo o %
 * del máximo, según la escala) cae en [limiteInferior, limiteSuperior], el
 * estudiante tiene esa etiqueta. Se borra con su estilo (ON DELETE CASCADE).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class BandaInterpretacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estilo_id", nullable = false)
    private Estilo estilo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EscalaBanda escala;

    @Column(name = "limite_inferior", nullable = false)
    private double limiteInferior;

    @Column(name = "limite_superior", nullable = false)
    private double limiteSuperior;

    @Column(nullable = false, length = 60)
    private String etiqueta;

    @Column(nullable = false)
    private int orden;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BandaInterpretacion))
            return false;
        BandaInterpretacion otra = (BandaInterpretacion) o;
        return getId() != null && Objects.equals(getId(), otra.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
