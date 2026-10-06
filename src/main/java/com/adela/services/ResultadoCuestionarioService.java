package com.adela.services;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adela.dto.EstiloResultadoDTO;
import com.adela.dto.CuestionarioResumidoDTO;
import com.adela.dto.EstudianteDTO;
import com.adela.dto.GrupoResumidoDTO;
import com.adela.dto.ListasCuestionariosDTO;
import com.adela.dto.PreguntaResueltaDTO;
import com.adela.dto.RespuestaCuestionarioDTO;
import com.adela.dto.ResultCuestCompletoDTO;
import com.adela.dto.ResultadoCuestionarioDTO;
import com.adela.dto.ResultadoGrupoDTO;
import com.adela.dto.ResultadoGrupoResumidoDTO;
import com.adela.entities.Estilo;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estudiante;
import com.adela.entities.Grupo;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.entities.Profesor;
import com.adela.entities.ResultadoCuestionario;
import com.adela.entities.ResultadoPregunta;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.EstudianteRepository;
import com.adela.repositories.GrupoRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;
import com.adela.repositories.ResultadoPreguntaRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
/**
 * Las lecturas van marcadas @Transactional(readOnly = true): navegan relaciones
 * LAZY y hasta ahora solo funcionaban porque open-in-view mantenia la sesion
 * abierta durante el render, reteniendo la conexion mas alla del servicio.
 */
@RequiredArgsConstructor
public class ResultadoCuestionarioService {
    private final ResultadoCuestionarioRepository resultadoCuestionarioRepository;
    
    private final ResultadoPreguntaRepository resultadoPreguntaRepository;
    
    private final CuestionarioRepository cuestionarioRepository;
    
    private final GrupoRepository grupoRepository;
    
    private final EstudianteRepository estudianteRepository;
    
    private final EvaluacionRespuestas evaluacionRespuestas;
    
    @Transactional
    public ResultadoCuestionario responderCuestionario(RespuestaCuestionarioDTO info, Estudiante estudiante) {
        Long cuestionarioId = info.getCuestionarioId();
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + cuestionarioId));
        
        ResultadoCuestionario resC = resolverAsignacion(info, cuestionario, estudiante);
        
        if (resC.isBloqueado()) {
            throw new AppException(ErrorCode.CUESTIONARIO_BLOQUEADO,
                    "Este cuestionario está bloqueado y no se puede responder.");
        }
        
        List<Opcion> opciones = evaluacionRespuestas.validarSeleccion(cuestionario, info.getOpcionesSeleccionadasId());
        
        resC.setFechaResolucion(Date.valueOf(LocalDate.now()));
        resC = resultadoCuestionarioRepository.save(resC);
        List<ResultadoPregunta> resultadoPreguntas = new LinkedList<>();
        for (Opcion opcion : opciones) {
            ResultadoPregunta rp = new ResultadoPregunta();
            rp.setCuestionario(resC);
            rp.setOpcion(opcion);
            resultadoPreguntas.add(rp);
        }
        
        resultadoPreguntaRepository.saveAll(resultadoPreguntas);
        return resultadoCuestionarioRepository.save(resC);
    }
    
    /**
     * Determina que asignacion (ResultadoCuestionario) se esta respondiendo. Un
     * estudiante puede tener el mismo cuestionario asignado en varios grupos, por lo
     * que el par (cuestionario, estudiante) no identifica una sola asignacion.
     */
    private ResultadoCuestionario resolverAsignacion(RespuestaCuestionarioDTO info, Cuestionario cuestionario,
            Estudiante estudiante) {
        Long resultadoId = info.getResultadoCuestionarioId();
        
        if (resultadoId != null) {
            ResultadoCuestionario resC = resultadoCuestionarioRepository.findById(resultadoId)
                    .orElseThrow(() -> new EntityNotFoundException("No existe la asignacion con id " + resultadoId));
            if (!resC.getEstudiante().getEmail().equalsIgnoreCase(estudiante.getEmail())) {
                throw new EntityNotFoundException(
                        "La asignacion " + resultadoId + " no pertenece al estudiante " + estudiante.getEmail());
            }
            if (!resC.getCuestionario().getId().equals(cuestionario.getId())) {
                throw new AppException(ErrorCode.ASIGNACION_NO_CORRESPONDE,
                        "La asignación " + resultadoId + " no corresponde al cuestionario " + cuestionario.getId()
                                + ".");
            }
            if (resC.getFechaResolucion() != null) {
                throw new AppException(ErrorCode.ASIGNACION_YA_RESPONDIDA,
                        "Este cuestionario ya fue respondido.");
            }
            return resC;
        }
        
        List<ResultadoCuestionario> pendientes = resultadoCuestionarioRepository
                .findByCuestionarioAndEstudianteAndFechaResolucionIsNull(cuestionario, estudiante);
        
        if (pendientes.isEmpty()) {
            throw new EntityNotFoundException("Al estudiante " + estudiante.getEmail()
                    + " no se le fue asignado el cuestionario " + cuestionario.getId());
        }
        if (pendientes.size() > 1) {
            throw new AppException(ErrorCode.ASIGNACION_AMBIGUA, "El cuestionario " + cuestionario.getId()
                    + " está asignado en varios grupos; indica resultadoCuestionarioId para saber cuál responder.");
        }
        return pendientes.get(0);
    }
    
    @Transactional(readOnly = true)
    public boolean existeAsignacion(Estudiante estudiante, Cuestionario cuestionario) {
        return !resultadoCuestionarioRepository
            .findByCuestionarioAndEstudianteAndFechaResolucionIsNull(cuestionario, estudiante)
            .isEmpty();
    }

    
    @Transactional
    public void asignarCuestionariosAsignadosAlGrupoAEstudiantesNuevos(Grupo grupo, Set<Estudiante> nuevosEstudiantes) {
        // Obtener todos los cuestionarios ya asignados al grupo
        List<ResultadoCuestionario> asignacionesExistentes = resultadoCuestionarioRepository.findByGrupo(grupo);
        
        // Si el grupo no tiene cuestionarios asignados, no hay nada que hacer
        if (asignacionesExistentes.isEmpty()) {
            return;
        }
        
        // Extraer los cuestionarios únicos del grupo con sus fechas de aplicación
        Map<Cuestionario, Date> cuestionariosConFecha = asignacionesExistentes.stream()
            .collect(Collectors.toMap(
                ResultadoCuestionario::getCuestionario,
                ResultadoCuestionario::getFechaAplicacion,
                (existing, replacement) -> existing // En caso de duplicados, mantener el primero
            ));
        
        for (Estudiante estudiante : nuevosEstudiantes) {
            for (Map.Entry<Cuestionario, Date> entry : cuestionariosConFecha.entrySet()) {
                Cuestionario cuestionario = entry.getKey();
                Date fechaAplicacion = entry.getValue();
                
                // Verificar si ya existe una asignación para este estudiante, cuestionario y grupo específico
                Optional<ResultadoCuestionario> existente = resultadoCuestionarioRepository
                    .findByCuestionarioAndEstudianteAndGrupo(cuestionario, estudiante, grupo);
                    
                if (existente.isEmpty()) {
                    // Crear nueva asignación solo si no existe para este grupo específico
                    ResultadoCuestionario nuevo = new ResultadoCuestionario();
                    nuevo.setEstudiante(estudiante);
                    nuevo.setCuestionario(cuestionario);
                    nuevo.setGrupo(grupo);
                    nuevo.setFechaResolucion(null); // Sin resolver inicialmente
                    nuevo.setBloqueado(false);
                    nuevo.setFechaAplicacion(fechaAplicacion);
                    
                    resultadoCuestionarioRepository.save(nuevo);
                }
            }
        }
    }

    
    /**
     * Asigna el cuestionario a todos los estudiantes del grupo, saltando a quienes
     * ya lo tienen asignado en ese mismo grupo.
     *
     * Sin esa comprobación, dos clics en el botón de asignar dejaban al estudiante
     * con dos asignaciones pendientes idénticas, que es justo la condición que hacía
     * fallar la entrega de respuestas con un 400 (ver docs/fixedbugs.md). Los otros
     * dos caminos de asignación ya comprobaban duplicados; este no.
     */
    @Transactional
    public void asignarCuestionarioAGrupo(Long cuestionarioId, Integer grupoId) {
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + cuestionarioId));

        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el grupo con id " + grupoId));

        Set<Estudiante> estudiantes = grupo.getEstudiantes();
        List<ResultadoCuestionario> asignaciones = new LinkedList<>();
        for (Estudiante estudiante : estudiantes) {
            boolean yaAsignado = resultadoCuestionarioRepository
                    .findByCuestionarioAndEstudianteAndGrupo(cuestionario, estudiante, grupo).isPresent();
            if (yaAsignado) {
                continue;
            }
            ResultadoCuestionario rc = new ResultadoCuestionario();
            rc.setCuestionario(cuestionario);
            rc.setEstudiante(estudiante);
            rc.setFechaAplicacion(Date.valueOf(LocalDate.now()));
            rc.setGrupo(grupo);
            asignaciones.add(rc);
        }
        resultadoCuestionarioRepository.saveAll(asignaciones);
    }
    
    @Transactional
    public void asignarCuestionarioAEstudiante(Long cuestionarioId, String estudianteEmail) {
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + cuestionarioId));
        
        Estudiante estudiante = estudianteRepository.findById(estudianteEmail)
                .orElseThrow(() -> new EntityNotFoundException("No existe el estudiante con id " + estudianteEmail));
        if (resultadoCuestionarioRepository
                .findByCuestionarioAndEstudianteAndFechaResolucionIsNull(cuestionario, estudiante).isEmpty()) {
            ResultadoCuestionario rc = new ResultadoCuestionario();
            rc.setCuestionario(cuestionario);
            rc.setEstudiante(estudiante);
            rc.setFechaAplicacion(Date.valueOf(LocalDate.now()));
            resultadoCuestionarioRepository.save(rc);
        }
    }
    
    @Transactional(readOnly = true)
    public ListasCuestionariosDTO obtenerCuestionarios(Estudiante estudiante) {
        List<ResultadoCuestionario> info = resultadoCuestionarioRepository
                .findByEstudianteAndBloqueadoFalse(estudiante);
        
        List<ResultadoCuestionarioDTO> pendientes = new LinkedList<>();
        List<ResultadoCuestionarioDTO> resueltos = new LinkedList<>();
        
        for (ResultadoCuestionario rc : info) {
            ResultadoCuestionarioDTO rcdto = new ResultadoCuestionarioDTO();
            
            Cuestionario c = rc.getCuestionario();
            
            CuestionarioResumidoDTO cdto = CuestionarioResumidoDTO.from(c);
            
            rcdto.setCuestionario(cdto);
            rcdto.setEstudiante(EstudianteDTO.from(rc.getEstudiante()));
            rcdto.setGrupo(GrupoResumidoDTO.from(rc.getGrupo()));
            rcdto.setFechaAplicacion(rc.getFechaAplicacion());
            rcdto.setFechaResolucion(rc.getFechaResolucion());
            rcdto.setId(rc.getId());
            if (rc.getFechaResolucion() == null) {
                pendientes.add(rcdto);
            } else {
                resueltos.add(rcdto);
            }
        }
        
        ListasCuestionariosDTO lcdto = new ListasCuestionariosDTO();
        
        lcdto.setPendientes(pendientes);
        lcdto.setResueltos(resueltos);
        
        return lcdto;
    }
    
    /**
     * Comprueba que el grupo exista y pertenezca al profesor indicado. Una
     * asignación sin grupo no tiene profesor propietario, así que se deniega.
     */
    private void verificarPropiedad(Grupo grupo, Profesor profesor) {
        if (grupo == null) {
            throw new EntityNotFoundException("La asignación no está vinculada a ningún grupo.");
        }
        if (!grupo.getProfesor().getEmail().equalsIgnoreCase(profesor.getEmail())) {
            throw new EntityNotFoundException("El grupo no pertenece a este profesor.");
        }
    }

    @Transactional(readOnly = true)
    public ResultCuestCompletoDTO obtenerResultadoCuestionario(Long cuestionarioResueltoId, Profesor profesor) {
        ResultadoCuestionario resC = resultadoCuestionarioRepository.findById(cuestionarioResueltoId).orElseThrow(
                () -> new EntityNotFoundException("El resultado de id " + cuestionarioResueltoId + " no existe"));
        verificarPropiedad(resC.getGrupo(), profesor);
        return construirResultado(resC);
    }

    @Transactional(readOnly = true)
    public ResultCuestCompletoDTO obtenerResultadoCuestionario(Long cuestionarioResueltoId, Estudiante estudiante) {
        ResultadoCuestionario resC = resultadoCuestionarioRepository.findById(cuestionarioResueltoId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "El resultado de id " + cuestionarioResueltoId + " no existe"));

        if (!resC.getEstudiante().getEmail().equalsIgnoreCase(estudiante.getEmail())) {
            throw new EntityNotFoundException("El resultado de id " + cuestionarioResueltoId
                    + " no pertenece al estudiante " + estudiante.getEmail());
        }

        return construirResultado(resC);
    }

    private ResultCuestCompletoDTO construirResultado(ResultadoCuestionario resC) {
        ResultCuestCompletoDTO res = new ResultCuestCompletoDTO();

        if (resC.getFechaResolucion() == null) {
            throw new AppException(ErrorCode.CUESTIONARIO_SIN_RESOLVER,
                    "Este cuestionario todavía no ha sido respondido.");
        }
        
        Cuestionario c = resC.getCuestionario();
        res.setCuestionario(CuestionarioResumidoDTO.from(c));
        res.setEstudiante(EstudianteDTO.from(resC.getEstudiante()));
        res.setGrupo(GrupoResumidoDTO.from(resC.getGrupo()));
        res.setFechaAplicacion(resC.getFechaAplicacion());
        res.setFechaResolucion(resC.getFechaResolucion());
        res.setId(resC.getId());
        
        Map<Long, PreguntaResueltaDTO> preg = new TreeMap<>();
        List<Opcion> elegidas = new LinkedList<>();
        
        for (ResultadoPregunta rep : resC.getPreguntas()) {
            Opcion o = rep.getOpcion();
            Pregunta p = o.getPregunta();
            PreguntaResueltaDTO pr = new PreguntaResueltaDTO();
            if (preg.containsKey(p.getId())) {
                pr = preg.get(p.getId());
            } else {
                pr.setPregunta(p.getPregunta());
                pr.setRespuestas(new LinkedList<String>());
                pr.setOrden(p.getOrden());
            }
            pr.getRespuestas().add(o.getRespuesta());
            elegidas.add(o);
            preg.put(p.getId(), pr);
        }
        
        for (Pregunta p : c.getPreguntas()) {
            if (!preg.containsKey(p.getId())) {
                PreguntaResueltaDTO pr = new PreguntaResueltaDTO();
                pr.setOrden(p.getOrden());
                pr.setPregunta(p.getPregunta());
                pr.setRespuestas(new LinkedList<>());
                preg.put(p.getId(), pr);
            }
        }
        res.setEstilos(evaluacionRespuestas.puntuar(c, elegidas));
        res.setPreguntas(new LinkedList<>(preg.values()));
        
        return res;
    }
    
    @Transactional
    public void toggleBloqueoCuestionario(Long cuestionarioId, Integer grupoId, Profesor profesor) {
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + cuestionarioId));
        
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el grupo con id " + grupoId));
        
        verificarPropiedad(grupo, profesor);
        
        List<ResultadoCuestionario> rcs = resultadoCuestionarioRepository.findByGrupoAndCuestionario(grupo,
                cuestionario);
        
        for (ResultadoCuestionario rc : rcs) {
            rc.setBloqueado(!rc.isBloqueado());
        }
        
        resultadoCuestionarioRepository.saveAll(rcs);
    }
    
    @Transactional(readOnly = true)
    public ResultadoGrupoDTO obtenerResultadosGrupoCuestionario(Long cuestionarioId, Integer grupoId,
            Profesor profesor) {
        
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + cuestionarioId));
        
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el grupo con id " + grupoId));
        
        verificarPropiedad(grupo, profesor);
        
        List<ResultadoCuestionario> rcs = resultadoCuestionarioRepository.findByGrupoAndCuestionario(grupo,
                cuestionario);
        
        if (rcs.size() == 0) {
            throw new EntityNotFoundException("Este cuestionario no ha sido asignado a ningun estudiante.");
        }
        
        int cnt = 0;
        
        ResultadoGrupoDTO res = new ResultadoGrupoDTO();
        
        res.setCuestionario(CuestionarioResumidoDTO.from(cuestionario));
        res.setGrupo(GrupoResumidoDTO.from(grupo));
        
        Map<Long, EstiloResultadoDTO> mp = new TreeMap<>();
        List<EstiloResultadoDTO> estilos = new LinkedList<>();
        List<ResultadoCuestionarioDTO> estudiantesS = new LinkedList<>();
        List<ResultadoCuestionarioDTO> estudiantesUS = new LinkedList<>();
        
        res.setFechaAplicacion(rcs.get(0).getFechaAplicacion());
        
        for (Estilo estilo : cuestionario.getEstilos()) {
            EstiloResultadoDTO cr = new EstiloResultadoDTO();
            cr.setNombre(estilo.getNombre());
            cr.setValor(0d);
            cr.setValorMaximo(estilo.getValorMaximo());
            cr.setValorMinimo(estilo.getValorMinimo());
            mp.put(estilo.getId(), cr);
            estilos.add(cr);
        }
        
        for (ResultadoCuestionario rc : rcs) {
            if (rc.getFechaResolucion() != null) {
                cnt++;
                for (ResultadoPregunta rp : rc.getPreguntas()) {
                    Opcion o = rp.getOpcion();
                    Estilo c = o.getEstilo();
                    EstiloResultadoDTO crdto = mp.get(c.getId());
                    crdto.setValor(crdto.getValor() + o.getValor());
                }
                estudiantesS.add(ResultadoCuestionarioDTO.from(rc));
            } else {
                estudiantesUS.add(ResultadoCuestionarioDTO.from(rc));
            }
        }
        
        // Sin resultados resueltos no hay promedio que calcular: dividir por cero
        // produce NaN, que Jackson no serializa y convierte la respuesta en un 500.
        if (cnt > 0) {
            for (EstiloResultadoDTO rca : mp.values()) {
                rca.setValor(rca.getValor() / cnt);
            }
        }
        
        res.setEstilos(estilos);
        res.setEstudiantesResuelto(estudiantesS);
        res.setEstudiantesNoResuelto(estudiantesUS);
        
        return res;
    }
    
    @Transactional(readOnly = true)
    public List<ResultadoGrupoResumidoDTO> obtenerPorGrupo(Integer grupoId, Profesor profesor) {
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el grupo con id " + grupoId));

        verificarPropiedad(grupo, profesor);

        List<ResultadoCuestionario> cuestos = resultadoCuestionarioRepository.findByGrupo(grupo);
        
        List<ResultadoGrupoResumidoDTO> res = new LinkedList<>();
        
        Set<ResultadoCuestionario> dif = new TreeSet<>(new Comparator<ResultadoCuestionario>() {
            @Override
            public int compare(ResultadoCuestionario a, ResultadoCuestionario b) {
                return a.getCuestionario().getId().compareTo(b.getCuestionario().getId());
            }
        });
        
        for (ResultadoCuestionario rc : cuestos) {
            dif.add(rc);
        }
        
        for (ResultadoCuestionario rc : dif) {
            res.add(ResultadoGrupoResumidoDTO.from(rc));
        }
        
        return res;
    }
    
}
