package com.adela.services;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adela.dto.CapsulaActualizarDTO;
import com.adela.dto.CapsulaCrearDTO;
import com.adela.dto.CapsulaDTO;
import com.adela.entities.Capsula;
import com.adela.entities.Cuestionario;
import com.adela.entities.Profesor;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.CapsulaRepository;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.RespuestaCapsulaRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Cápsulas: un cuestionario compartido por enlace para que lo respondan
 * personas sin cuenta.
 *
 * Del lado del profesor todo se resuelve por findByProfesorAndId, así que una
 * cápsula ajena es indistinguible de una inexistente, igual que en grupos.
 */
@RequiredArgsConstructor
@Service
public class CapsulaService {

    static final int LONGITUD_CODIGO_CAPSULA = 8;

    private static final int INTENTOS_CODIGO = 5;

    private final CapsulaRepository capsulaRepository;

    private final RespuestaCapsulaRepository respuestaCapsulaRepository;

    private final CuestionarioRepository cuestionarioRepository;

    private Capsula delProfesor(Long id, Profesor profesor) {
        return capsulaRepository.findByProfesorAndId(profesor, id)
                .orElseThrow(() -> new EntityNotFoundException("Cápsula no encontrada con el ID: " + id));
    }

    /**
     * La columna es unique y respalda la comprobación, pero chocar con un código
     * existente es tan improbable (31^8) que basta con reintentar unas veces.
     */
    static String codigoLibre(int longitud, Predicate<String> enUso) {
        for (int i = 0; i < INTENTOS_CODIGO; i++) {
            String codigo = CodigoAleatorio.generar(longitud);
            if (!enUso.test(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No se encontró un código libre tras " + INTENTOS_CODIGO + " intentos");
    }

    @Transactional
    public CapsulaDTO crear(CapsulaCrearDTO dto, Profesor profesor) {
        Cuestionario cuestionario = cuestionarioRepository.findById(dto.cuestionarioId())
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + dto.cuestionarioId()));

        Capsula capsula = new Capsula();
        capsula.setCodigo(codigoLibre(LONGITUD_CODIGO_CAPSULA, capsulaRepository::existsByCodigo));
        capsula.setNombre(dto.nombre().strip());
        capsula.setProfesor(profesor);
        capsula.setCuestionario(cuestionario);
        capsula.setModoIdentificacion(dto.modoIdentificacion());
        capsula.setCreadaEn(Instant.now());
        return CapsulaDTO.from(capsulaRepository.save(capsula), 0);
    }

    @Transactional(readOnly = true)
    public List<CapsulaDTO> listar(Profesor profesor) {
        return capsulaRepository.resumirPorProfesor(profesor);
    }

    @Transactional(readOnly = true)
    public CapsulaDTO consultar(Long id, Profesor profesor) {
        Capsula capsula = delProfesor(id, profesor);
        return CapsulaDTO.from(capsula, respuestaCapsulaRepository.countByCapsula(capsula));
    }

    @Transactional
    public CapsulaDTO actualizar(Long id, CapsulaActualizarDTO dto, Profesor profesor) {
        Capsula capsula = delProfesor(id, profesor);
        if (dto.nombre() != null) {
            if (dto.nombre().isBlank()) {
                throw new AppException(ErrorCode.VALIDACION, "Revisa los campos marcados.",
                        Map.of("nombre", "El nombre es obligatorio"));
            }
            capsula.setNombre(dto.nombre().strip());
        }
        if (dto.abierta() != null) {
            capsula.setAbierta(dto.abierta());
        }
        return CapsulaDTO.from(capsula, respuestaCapsulaRepository.countByCapsula(capsula));
    }

    /** Las respuestas caen con ella por ON DELETE CASCADE. */
    @Transactional
    public void eliminar(Long id, Profesor profesor) {
        capsulaRepository.delete(delProfesor(id, profesor));
    }
}
