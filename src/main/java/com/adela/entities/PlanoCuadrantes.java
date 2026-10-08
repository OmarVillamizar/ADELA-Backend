package com.adela.entities;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plano del esquema CUADRANTES: dos estilos como ejes, un corte por eje y el
 * nombre de cada esquina. Hay uno por cuestionario, así que la clave primaria
 * es el id del cuestionario (asignado, no generado).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PlanoCuadrantes {

    @Id
    @Column(name = "cuestionario_id")
    private Long cuestionarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eje_x_id", nullable = false)
    private Estilo ejeX;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eje_y_id", nullable = false)
    private Estilo ejeY;

    @Column(name = "corte_x", nullable = false)
    private double corteX;

    @Column(name = "corte_y", nullable = false)
    private double corteY;

    @Column(name = "x_alto_y_alto", nullable = false, length = 60)
    private String xAltoYAlto;

    @Column(name = "x_bajo_y_alto", nullable = false, length = 60)
    private String xBajoYAlto;

    @Column(name = "x_bajo_y_bajo", nullable = false, length = 60)
    private String xBajoYBajo;

    @Column(name = "x_alto_y_bajo", nullable = false, length = 60)
    private String xAltoYBajo;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PlanoCuadrantes))
            return false;
        PlanoCuadrantes otro = (PlanoCuadrantes) o;
        return getCuestionarioId() != null && Objects.equals(getCuestionarioId(), otro.getCuestionarioId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCuestionarioId());
    }
}
