package com.adela.entities;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Opción de la pregunta complementaria. texto y descripcion se muestran al
 * responder; resultado y resultadoDescripcion, en el reporte de quien la eligió.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OpcionComplementaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cuestionario_id", nullable = false)
    private PreguntaComplementaria pregunta;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false, length = 200)
    private String texto;

    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false, length = 100)
    private String resultado;

    @Column(name = "resultado_descripcion", length = 1000)
    private String resultadoDescripcion;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof OpcionComplementaria))
            return false;
        OpcionComplementaria otra = (OpcionComplementaria) o;
        return getId() != null && Objects.equals(getId(), otra.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
