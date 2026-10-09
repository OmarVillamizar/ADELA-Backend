package com.adela.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pregunta extra de un cuestionario que se hace cuando el perfil destaca todos
 * los estilos: el puntaje ya no distingue a la persona y ella misma declara
 * cómo los usa (p. ej. según la situación o combinándolos). Es autodeclarada:
 * no cambia puntajes ni perfil. Una por cuestionario; la clave es su id.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PreguntaComplementaria {

    @Id
    @Column(name = "cuestionario_id")
    private Long cuestionarioId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "cuestionario_id")
    private Cuestionario cuestionario;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(length = 500)
    private String introduccion;

    @Column(nullable = false, length = 500)
    private String enunciado;

    @Column(length = 300)
    private String nota;

    @OneToMany(mappedBy = "pregunta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden")
    private List<OpcionComplementaria> opciones = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PreguntaComplementaria))
            return false;
        PreguntaComplementaria otra = (PreguntaComplementaria) o;
        return getCuestionarioId() != null && Objects.equals(getCuestionarioId(), otra.getCuestionarioId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCuestionarioId());
    }
}
