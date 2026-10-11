package com.adela.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adela.dto.EstudianteCrearDTO;
import com.adela.dto.GrupoDTO;
import com.adela.dto.GrupoDetalleDTO;
import com.adela.dto.GrupoResumidoDTO;
import com.adela.entities.Estudiante;
import com.adela.entities.Grupo;
import com.adela.entities.Profesor;
import com.adela.entities.ResultadoCuestionario;
import com.adela.entities.UsuarioEstado;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.EstudianteRepository;
import com.adela.repositories.GrupoRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
@Service
public class GrupoService {

    private final GrupoRepository grupoRepository;

    private final EstudianteRepository estudianteRepository;

    private final ResultadoCuestionarioService resultadoCuestionarioService;

    private final ResultadoCuestionarioRepository resultadoCuestionarioRepository;

    private Grupo delProfesor(int id, Profesor profesor) {
        return grupoRepository.findByProfesorAndId(profesor, id)
                .orElseThrow(() -> new EntityNotFoundException("Grupo no encontrado con el ID: " + id));
    }

    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private static final int LARGO_MAXIMO = 100;

    /**
     * El correo es la clave del estudiante y Google lo entrega en minúsculas: un
     * " Ana@UFPS.edu.co" de una hoja de cálculo no encontraba a la estudiante y
     * creaba otra con ese correo, que nunca podría iniciar sesión.
     */
    private static String normalizarCorreo(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String nombreValido(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            throw new AppException(ErrorCode.VALIDACION, "El nombre del grupo es obligatorio.");
        }
        if (limpio.length() > LARGO_MAXIMO) {
            throw new AppException(ErrorCode.VALIDACION,
                    "El nombre del grupo admite como mucho " + LARGO_MAXIMO + " caracteres.");
        }
        return limpio;
    }

    /**
     * Resuelve los correos a estudiantes, creando los que aún no existan. Los
     * nuevos quedan INCOMPLETA hasta que entren y completen su perfil. Valida
     * todos antes de tocar nada, para que un correo mal escrito no deje media
     * lista aplicada.
     */
    private Set<Estudiante> resolverOCrear(Collection<EstudianteCrearDTO> solicitados) {
        Map<String, String> nombrePorCorreo = new LinkedHashMap<>();
        List<String> invalidos = new ArrayList<>();
        for (EstudianteCrearDTO dto : solicitados) {
            if (dto == null) {
                continue;
            }
            String email = normalizarCorreo(dto.getEmail());
            if (!CORREO.matcher(email).matches() || email.length() > LARGO_MAXIMO) {
                invalidos.add(dto.getEmail());
                continue;
            }
            String nombre = dto.getNombre() == null ? "" : dto.getNombre().trim();
            if (nombre.length() > LARGO_MAXIMO) {
                nombre = nombre.substring(0, LARGO_MAXIMO);
            }
            nombrePorCorreo.merge(email, nombre, (previo, nuevo) -> previo.isEmpty() ? nuevo : previo);
        }
        if (!invalidos.isEmpty()) {
            throw new AppException(ErrorCode.VALIDACION, "Correos no válidos: " + invalidos);
        }

        Set<Estudiante> resultado = new HashSet<>();
        nombrePorCorreo.forEach((email, nombre) -> {
            Optional<Estudiante> existente = estudianteRepository.findById(email);
            if (existente.isPresent()) {
                Estudiante estudiante = existente.get();
                // Un preinscrito sin nombre lo toma de la lista; el de alguien que
                // ya entró manda sobre lo que diga la hoja del profesor.
                if ((estudiante.getNombre() == null || estudiante.getNombre().isBlank()) && !nombre.isEmpty()) {
                    estudiante.setNombre(nombre);
                }
                resultado.add(estudiante);
            } else {
                Estudiante nuevo = new Estudiante();
                nuevo.setEmail(email);
                nuevo.setNombre(nombre.isEmpty() ? null : nombre);
                nuevo.setEstado(UsuarioEstado.INCOMPLETA);
                resultado.add(nuevo);
            }
        });
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

    /** El profesor distingue sus grupos por nombre; dos iguales serían indistinguibles. */
    private void exigirNombreLibre(String nombre, Profesor profesor, Integer excepto) {
        boolean ocupado = grupoRepository.findByProfesor(profesor).stream()
                .anyMatch(g -> g.getNombre().trim().equalsIgnoreCase(nombre)
                        && (excepto == null || g.getId() != excepto));
        if (ocupado) {
            throw new AppException(ErrorCode.GRUPO_DUPLICADO, "Ya tienes un grupo llamado \"" + nombre + "\".");
        }
    }

    /**
     * Un grupo puede nacer vacío: el profesor lo crea antes de tener la lista y
     * la carga después desde el detalle.
     */
    @Transactional
    public GrupoDetalleDTO crear(GrupoDTO datos, Profesor profesor) {
        String nombre = nombreValido(datos.getNombre());
        exigirNombreLibre(nombre, profesor, null);
        Set<Estudiante> estudiantes = resolverOCrear(
                datos.getEstudiantes() == null ? List.of() : datos.getEstudiantes());

        Grupo grupo = new Grupo();
        grupo.setNombre(nombre);
        grupo.setProfesor(profesor);
        grupo = grupoRepository.save(grupo);

        for (Estudiante estudiante : estudiantes) {
            estudiante.getGrupos().add(grupo);
        }
        estudianteRepository.saveAll(estudiantes);
        grupo.setEstudiantes(estudiantes);
        return GrupoDetalleDTO.from(grupoRepository.save(grupo));
    }

    /**
     * Reemplaza el grupo por lo recibido, que es lo que significa un PUT. Antes solo
     * añadía: quitar a alguien de la lista no lo sacaba del grupo, así que no había
     * forma de corregir una matrícula equivocada desde este endpoint. Y sin la
     * comprobación de nulo que sí hace crear, un cuerpo sin estudiantes reventaba
     * con NPE y salía como 500.
     */
    @Transactional
    public GrupoDetalleDTO actualizar(int id, GrupoDTO datos, Profesor profesor) {
        if (datos.getEstudiantes() == null) {
            throw new AppException(ErrorCode.VALIDACION, "Faltan campos requeridos: nombre y estudiantes.");
        }
        String nombre = nombreValido(datos.getNombre());

        Grupo grupo = delProfesor(id, profesor);
        exigirNombreLibre(nombre, profesor, id);
        grupo.setNombre(nombre);

        Set<Estudiante> deseados = resolverOCrear(datos.getEstudiantes());

        // Los que ya no aparecen salen del grupo y pierden sus asignaciones
        // pendientes; las resueltas se conservan, como en eliminarEstudiante.
        Set<Estudiante> aQuitar = new HashSet<>(grupo.getEstudiantes());
        aQuitar.removeAll(deseados);
        for (Estudiante estudiante : aQuitar) {
            desvincular(grupo, estudiante);
        }

        Set<Estudiante> aAgregar = new HashSet<>();
        for (Estudiante estudiante : deseados) {
            if (!grupo.getEstudiantes().contains(estudiante)) {
                estudiante.getGrupos().add(grupo);
                grupo.getEstudiantes().add(estudiante);
                aAgregar.add(estudiante);
            }
        }

        estudianteRepository.saveAll(aQuitar);
        estudianteRepository.saveAll(aAgregar);
        resultadoCuestionarioService.asignarCuestionariosAsignadosAlGrupoAEstudiantesNuevos(grupo, aAgregar);
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

    /**
     * Mismo criterio que crear: los correos que aún no existen se preinscriben.
     * Antes exigía que todos existieran, así que una lista de clase cargada desde
     * el detalle fallaba entera por un solo estudiante que no había entrado nunca.
     */
    @Transactional
    public GrupoDetalleDTO agregarEstudiantes(int id, List<EstudianteCrearDTO> solicitados, Profesor profesor) {
        Grupo grupo = delProfesor(id, profesor);
        // Validar antes de mutar: resolverOCrear rechaza la lista entera si un
        // correo no es válido, sin haber añadido a nadie.
        Set<Estudiante> estudiantes = resolverOCrear(solicitados);
        estudiantes.removeAll(grupo.getEstudiantes());

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
