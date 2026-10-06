package com.adela.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adela.dto.InsigniaDTO;
import com.adela.entities.Insignia;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.InsigniaEstudianteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InsigniaService {

    private final InsigniaEstudianteRepository repositorio;

    @Transactional
    public void otorgar(String email, Insignia insignia) {
        repositorio.otorgar(email, insignia.name());
    }

    @Transactional(readOnly = true)
    public List<InsigniaDTO> obtenidas(String email) {
        return repositorio.findByIdEstudianteEmailOrderByObtenidaEn(email).stream().map(InsigniaDTO::from).toList();
    }

    /**
     * El código llega como texto de la URL: un valor fuera del catálogo es un 400
     * con mensaje, no el 500 que daría la conversión automática al enum.
     */
    @Transactional
    public void marcarCelebrada(String email, String codigo) {
        Insignia insignia;
        try {
            insignia = Insignia.valueOf(codigo);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDACION, "La insignia " + codigo + " no existe.");
        }
        if (repositorio.marcarCelebrada(email, insignia) == 0) {
            throw new AppException(ErrorCode.RECURSO_NO_ENCONTRADO, "Aún no has obtenido esta insignia.");
        }
    }
}
