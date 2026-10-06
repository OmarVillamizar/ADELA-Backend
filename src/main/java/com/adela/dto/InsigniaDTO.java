package com.adela.dto;

import java.time.Instant;

import com.adela.entities.Insignia;
import com.adela.entities.InsigniaEstudiante;

/** Solo las ganadas: nombre, texto e imagen de cada insignia los pone el cliente. */
public record InsigniaDTO(Insignia codigo, Instant obtenidaEn, boolean celebrada) {

    public static InsigniaDTO from(InsigniaEstudiante i) {
        return new InsigniaDTO(i.getId().getCodigo(), i.getObtenidaEn(), i.isCelebrada());
    }
}
