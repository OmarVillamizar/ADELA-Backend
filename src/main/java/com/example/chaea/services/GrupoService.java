package com.example.chaea.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.chaea.dto.EstudianteCrearDTO;
import com.example.chaea.dto.GrupoDTO;
import com.example.chaea.dto.GrupoDetalleDTO;
import com.example.chaea.dto.GrupoResumidoDTO;
import com.example.chaea.entities.Estudiante;
import com.example.chaea.entities.Grupo;
import com.example.chaea.entities.Profesor;
import com.example.chaea.entities.ResultadoCuestionario;
import com.example.chaea.entities.UsuarioEstado;
import com.example.chaea.exceptions.AppException;
import com.example.chaea.exceptions.ErrorCode;
import com.example.chaea.repositories.EstudianteRepository;
import com.example.chaea.repositories.GrupoRepository;
import com.example.chaea.repositories.ResultadoCuestionarioRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Lógica de los grupos, extraída de GrupoController.
 *
 * Crear un grupo son tres escrituras (grupo, estudiantes, grupo otra vez) que en
 * el controlador corrían sin transacción: un fallo a mitad dejaba el grupo creado
 * y los estudiantes sin vincular. Esa falta de frontera transaccional es la causa
 * estructural de BUG-06, no un descuido puntual.
 *
 * Todos los métodos resuelven el grupo por findByProfesorAndId, así que un id
 * ajeno es indistinguible de uno inexistente y no revela qué grupos hay.
 */
@Service
public class GrupoService {

    @Autowired
    private GrupoRepository grupoRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private ResultadoCuestionarioService resultadoCuestionarioService;

    @Autowired
    private ResultadoCuestionarioRepository resultadoCuestionarioRepository;

    private Grupo delProfesor(int id, Profesor profesor) {
        return grupoRepository.findByProfesorAndId(profesor, id)
                .orElseThrow(() -> new EntityNotFoundException("Grupo no encontrado con el ID: " + id));
    }

    /**
     * Resuelve los correos a estudiantes, creando los que aún no existan. Los
     * nuevos quedan INCOMPLETA hasta que entren y completen su perfil.
     */
    private Set<Estudiante> resolverOCrear(Set<EstudianteCrearDTO> solicitados) {
        Set<Estudiante> resultado = new HashSet<>();
        for (EstudianteCrearDTO dto : solicitados) {
            Optional<Estudiante> existente = estudianteRepository.findById(dto.getEmail());
            if (existente.isPresent()) {
                resultado.add(existente.get());
            } else {
                Estudiante nuevo = new Estudiante();
                nuevo.setEmail(dto.getEmail());
                nuevo.setNombre(dto.getNombre());
                nuevo.setEstado(UsuarioEstado.INCOMPLETA);
                resultado.add(nuevo);
            }
        }
        return resultado;
    }

    /** Exige que todos los correos existan antes de tocar nada. */
    private Set<Estudiante> exigirExistentes(List<String> emails) {
        List<String> noEncontrados = new ArrayList<>();
        Set<Estudiante> estudiantes = new HashSet<>();
        for (String email : emails) {
            Optional<Estudiante> opt = estudianteRepository.findById(email);
            if (opt.isPresent()) {
                estudiantes.add(opt.get());
            } else {
                noEncontrados.add(email);
            }
        }
        if (!noEncontrados.isEmpty()) {
            throw new AppException(ErrorCode.ESTUDIANTES_NO_ENCONTRADOS,
                    "No existen estudiantes con los correos: " + noEncontrados);
        }
        return estudiantes;
    }

    @Transactional(readOnly = true)
    public List<GrupoResumidoDTO> listarDelProfesor(Profesor profesor) {
        return grupoRepository.resumirPorProfesor(profesor);
    }

    @Transactional(readOnly = true)
    public GrupoDetalleDTO consultarPorId(int id, Profesor profesor) {
        return GrupoDetalleDTO.from(delProfesor(id, profesor));
    }

    @Transactional
    public GrupoDetalleDTO crear(GrupoDTO datos, Profesor profesor) {
        if (datos.getNombre() == null || datos.getEstudiantes() == null) {
            throw new AppException(ErrorCode.VALIDACION, "Faltan campos requeridos: nombre y estudiantes.");
        }

        Grupo grupo = new Grupo();
        grupo.setNombre(datos.getNombre());
        grupo.setProfesor(profesor);
        grupo = grupoRepository.save(grupo);

        Set<Estudiante> estudiantes = resolverOCrear(datos.getEstudiantes());
        for (Estudiante estudiante : estudiantes) {
            estudiante.getGrupos().add(grupo);
        }
        estudianteRepository.saveAll(estudiantes);
        grupo.setEstudiantes(estudiantes);
        return GrupoDetalleDTO.from(grupoRepository.save(grupo));
    }

    @Transactional
    public GrupoDetalleDTO actualizar(int id, GrupoDTO datos, Profesor profesor) {
        Grupo grupo = delProfesor(id, profesor);
        grupo.setNombre(datos.getNombre());

        Set<Estudiante> aAgregar = new HashSet<>();
        for (Estudiante estudiante : resolverOCrear(datos.getEstudiantes())) {
            if (!grupo.getEstudiantes().contains(estudiante)) {
                estudiante.getGrupos().add(grupo);
                aAgregar.add(estudiante);
            }
        }
        grupo.getEstudiantes().addAll(aAgregar);
        estudianteRepository.saveAll(aAgregar);
        return GrupoDetalleDTO.from(grupoRepository.save(grupo));
    }

    @Transactional
    public void eliminar(int id, Profesor profesor) {
        Grupo grupo = delProfesor(id, profesor);
        for (Estudiante estudiante : grupo.getEstudiantes()) {
            estudiante.getGrupos().remove(grupo);
        }
        estudianteRepository.saveAll(grupo.getEstudiantes());
        grupoRepository.delete(grupo);
    }

    @Transactional
    public GrupoDetalleDTO agregarEstudiantes(int id, List<String> emails, Profesor profesor) {
        Grupo grupo = delProfesor(id, profesor);
        // Validar antes de mutar: la versión anterior añadía al grupo dentro del
        // mismo bucle que recogía los correos inválidos, así que con la transacción
        // abierta habría persistido las altas y devuelto 400 igualmente.
        Set<Estudiante> estudiantes = exigirExistentes(emails);

        for (Estudiante estudiante : estudiantes) {
            grupo.getEstudiantes().add(estudiante);
            estudiante.getGrupos().add(grupo);
        }
        estudianteRepository.saveAll(estudiantes);
        resultadoCuestionarioService.asignarCuestionariosAsignadosAlGrupoAEstudiantesNuevos(grupo, estudiantes);
        return GrupoDetalleDTO.from(grupoRepository.save(grupo));
    }

    @Transactional
    public GrupoDetalleDTO eliminarEstudiante(int id, String email, Profesor profesor) {
        Grupo grupo = delProfesor(id, profesor);
        Estudiante estudiante = estudianteRepository.findById(email)
                .orElseThrow(() -> new EntityNotFoundException("Estudiante no encontrado con el correo: " + email));

        if (!grupo.getEstudiantes().contains(estudiante)) {
            throw new AppException(ErrorCode.ESTUDIANTE_NO_EN_GRUPO, "El estudiante no pertenece a este grupo.");
        }

        desvincular(grupo, estudiante);
        estudianteRepository.save(estudiante);
        return GrupoDetalleDTO.from(grupoRepository.save(grupo));
    }

    @Transactional
    public GrupoDetalleDTO eliminarEstudiantes(int id, List<String> emails, Profesor profesor) {
        Grupo grupo = delProfesor(id, profesor);
        Set<Estudiante> estudiantes = exigirExistentes(emails);

        for (Estudiante estudiante : estudiantes) {
            desvincular(grupo, estudiante);
        }
        estudianteRepository.saveAll(estudiantes);
        return GrupoDetalleDTO.from(grupoRepository.save(grupo));
    }

    /**
     * Retira al estudiante del grupo y borra sus asignaciones pendientes. Las ya
     * resueltas se conservan: son el resultado de un cuestionario que respondió.
     */
    private void desvincular(Grupo grupo, Estudiante estudiante) {
        List<ResultadoCuestionario> pendientes = resultadoCuestionarioRepository
                .findByEstudianteAndGrupo(estudiante, grupo).stream().filter(rc -> rc.getFechaResolucion() == null)
                .toList();
        if (!pendientes.isEmpty()) {
            resultadoCuestionarioRepository.deleteAll(pendientes);
        }
        grupo.getEstudiantes().remove(estudiante);
        estudiante.getGrupos().remove(grupo);
    }
}
