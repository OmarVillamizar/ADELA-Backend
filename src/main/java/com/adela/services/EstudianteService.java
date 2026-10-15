package com.adela.services;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adela.dto.EstudianteBusquedaDTO;
import com.adela.dto.EstudianteDTO;
import com.adela.entities.Estudiante;
import com.adela.entities.Profesor;
import com.adela.repositories.EstudianteRepository;
import com.adela.repositories.GrupoRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Consulta de estudiantes por profesores. Un profesor necesita buscar a quien
 * aún no está en sus grupos para agregarlo, pero no debe poder recorrer la
 * tabla ni leer datos personales de estudiantes ajenos.
 */
@Service
@RequiredArgsConstructor
public class EstudianteService {

    /** Por debajo de esto la búsqueda de un profesor no enumera la tabla letra a letra. */
    static final int MIN_BUSQUEDA_PROFESOR = 3;
    static final int TAM_MAX_PROFESOR = 20;
    /** Tope duro: el cliente no puede pedir la tabla entera subiendo `size`. */
    static final int TAM_MAX_ADMIN = 100;

    private final EstudianteRepository estudianteRepository;

    private final GrupoRepository grupoRepository;

    /**
     * Admin: datos completos, `q` libre. Profesor: solo correo y nombre, y una
     * búsqueda de menos de {@link #MIN_BUSQUEDA_PROFESOR} caracteres no devuelve nada.
     */
    @Transactional(readOnly = true)
    public List<?> buscar(String q, int size, Profesor quien) {
        String texto = q == null ? "" : q.trim();
        if (quien.esAdmin()) {
            return pagina(texto, size, TAM_MAX_ADMIN).stream().map(EstudianteDTO::from).toList();
        }
        if (texto.length() < MIN_BUSQUEDA_PROFESOR) {
            return List.of();
        }
        return pagina(texto, size, TAM_MAX_PROFESOR).stream().map(EstudianteBusquedaDTO::from).toList();
    }

    /** Un estudiante fuera de los grupos del profesor se responde igual que uno inexistente. */
    @Transactional(readOnly = true)
    public EstudianteDTO consultar(String email, Profesor quien) {
        if (!quien.esAdmin() && !grupoRepository.existsByProfesorAndEstudiantes_Email(quien, email)) {
            throw noEncontrado(email);
        }
        return estudianteRepository.findById(email).map(EstudianteDTO::from).orElseThrow(() -> noEncontrado(email));
    }

    private List<Estudiante> pagina(String texto, int size, int tope) {
        Pageable pagina = PageRequest.of(0, Math.min(Math.max(size, 1), tope), Sort.by("email"));
        return estudianteRepository.findByEmailContainingIgnoreCaseOrNombreContainingIgnoreCase(texto, texto, pagina)
                .getContent();
    }

    private static EntityNotFoundException noEncontrado(String email) {
        return new EntityNotFoundException("Estudiante no encontrado con el correo: " + email);
    }
}
