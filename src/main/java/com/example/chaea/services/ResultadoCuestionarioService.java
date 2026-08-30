package com.example.chaea.services;

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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.chaea.dto.CategoriaResultadoDTO;
import com.example.chaea.dto.CuestionarioResumidoDTO;
import com.example.chaea.dto.EstudianteDTO;
import com.example.chaea.dto.GrupoResumidoDTO;
import com.example.chaea.dto.ListasCuestionariosDTO;
import com.example.chaea.dto.PreguntaResueltaDTO;
import com.example.chaea.dto.RespuestaCuestionarioDTO;
import com.example.chaea.dto.ResultCuestCompletoDTO;
import com.example.chaea.dto.ResultadoCuestionarioDTO;
import com.example.chaea.dto.ResultadoGrupoDTO;
import com.example.chaea.dto.ResultadoGrupoResumidoDTO;
import com.example.chaea.entities.Categoria;
import com.example.chaea.entities.Cuestionario;
import com.example.chaea.entities.Estudiante;
import com.example.chaea.entities.Grupo;
import com.example.chaea.entities.Opcion;
import com.example.chaea.entities.Pregunta;
import com.example.chaea.entities.Profesor;
import com.example.chaea.entities.ResultadoCuestionario;
import com.example.chaea.entities.ResultadoPregunta;
import com.example.chaea.repositories.CuestionarioRepository;
import com.example.chaea.repositories.EstudianteRepository;
import com.example.chaea.repositories.GrupoRepository;
import com.example.chaea.repositories.OpcionRepository;
import com.example.chaea.repositories.PreguntaRepository;
import com.example.chaea.repositories.ResultadoCuestionarioRepository;
import com.example.chaea.repositories.ResultadoPreguntaRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class ResultadoCuestionarioService {
    @Autowired
    private ResultadoCuestionarioRepository resultadoCuestionarioRepository;
    
    @Autowired
    private ResultadoPreguntaRepository resultadoPreguntaRepository;
    
    @Autowired
    private CuestionarioRepository cuestionarioRepository;
    
    @Autowired
    private PreguntaRepository preguntaRepository;
    
    @Autowired
    private OpcionRepository opcionRepository;
    
    @Autowired
    private GrupoRepository grupoRepository;
    
    @Autowired
    private EstudianteRepository estudianteRepository;
    
    @Transactional
    public ResultadoCuestionario responderCuestionario(RespuestaCuestionarioDTO info, Estudiante estudiante) {
        Long cuestionarioId = info.getCuestionarioId();
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + cuestionarioId));
        
        ResultadoCuestionario resC = resolverAsignacion(info, cuestionario, estudiante);
        
        if (resC.isBloqueado()) {
            throw new RuntimeException("Este cuestionario está bloqueado y no se puede responder.");
        }
        
        resC.setFechaResolucion(Date.valueOf(LocalDate.now()));
        resC = resultadoCuestionarioRepository.save(resC);
        List<ResultadoPregunta> resultadoPreguntas = new LinkedList<>();
        List<Pregunta> preguntas = preguntaRepository.findByCuestionario(cuestionario);
        Map<Long, Pregunta> answered = new TreeMap<>();
        Map<Long, Pregunta> unAnswered = new TreeMap<>();
        for (Pregunta pregunta : preguntas) {
            if (!pregunta.isOpcionMultiple()) {
                unAnswered.put(pregunta.getId(), pregunta);
            }
        }
        for (Long opcionId : info.getOpcionesSeleccionadasId()) {
            ResultadoPregunta rp = responderPregunta(opcionId, resC);
            Long preguntaId = rp.getOpcion().getPregunta().getId();
            if (answered.containsKey(preguntaId) && !rp.getOpcion().getPregunta().isOpcionMultiple()) {
                throw new RuntimeException("La pregunta " + answered.get(preguntaId).getOrden()
                        + " tuvo mas de una opcion seleccionada(" + rp.getOpcion().getOrden() + ").");
            }
            answered.put(preguntaId, rp.getOpcion().getPregunta());
            unAnswered.remove(preguntaId);
            resultadoPreguntas.add(rp);
        }
        
        if (!unAnswered.isEmpty()) {
            StringBuilder result = new StringBuilder();
            for (Pregunta value : unAnswered.values()) {
                result.append(value.getOrden());
                result.append(", ");
            } // Eliminar la última coma y espacio
            if (result.length() > 0) {
                result.setLength(result.length() - 2);
            }
            throw new RuntimeException("Las preguntas " + result + " no fueron respondidas");
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
                throw new RuntimeException(
                        "La asignacion " + resultadoId + " no corresponde al cuestionario " + cuestionario.getId());
            }
            if (resC.getFechaResolucion() != null) {
                throw new RuntimeException("La asignacion " + resultadoId + " ya fue respondida.");
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
            throw new RuntimeException("El cuestionario " + cuestionario.getId()
                    + " esta asignado en varios grupos; indica resultadoCuestionarioId para saber cual responder.");
        }
        return pendientes.get(0);
    }
    
    public List<Cuestionario> obtenerCuestionariosPorGrupo(Integer grupoId) {
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el grupo con id " + grupoId));
        
        // Aquí obtienes los resultados (asignaciones) por grupo y extraes los cuestionarios únicos
        List<ResultadoCuestionario> resultados = resultadoCuestionarioRepository.findByGrupo(grupo);
        
        // Usamos un Set para evitar duplicados
        Set<Cuestionario> cuestionarios = new TreeSet<>((c1, c2) -> c1.getId().compareTo(c2.getId()));
        for (ResultadoCuestionario rc : resultados) {
            cuestionarios.add(rc.getCuestionario());
        }
        
        return new LinkedList<>(cuestionarios);
    }
    
    public boolean existeAsignacion(Estudiante estudiante, Cuestionario cuestionario) {
        return !resultadoCuestionarioRepository
            .findByCuestionarioAndEstudianteAndFechaResolucionIsNull(cuestionario, estudiante)
            .isEmpty();
    }

    
    public ResultadoPregunta responderPregunta(Long opcionId, ResultadoCuestionario resC) {
        Opcion opcion = opcionRepository.findById(opcionId)
                .orElseThrow(() -> new EntityNotFoundException("No existe la opción " + opcionId));
        Pregunta pregunta = opcion.getPregunta();
        Cuestionario cuestionario = resC.getCuestionario();
        if (!pregunta.getCuestionario().getId().equals(cuestionario.getId())) {
            throw new RuntimeException("Inconsistencia: la opcion de id " + opcionId + " no pertenece al cuestionario "
                    + cuestionario.getId());
        }
        ResultadoPregunta rp = new ResultadoPregunta();
        rp.setCuestionario(resC);
        rp.setOpcion(opcion);
        
        return rp;
    }
    
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

    
    public void asignarCuestionarioAGrupo(Long cuestionarioId, Integer grupoId) {
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + cuestionarioId));
        
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el grupo con id " + grupoId));
        
        Set<Estudiante> estudiantes = grupo.getEstudiantes();
        List<ResultadoCuestionario> asignaciones = new LinkedList<>();
        for (Estudiante estudiante : estudiantes) {
            ResultadoCuestionario rc = new ResultadoCuestionario();
            rc.setCuestionario(cuestionario);
            rc.setEstudiante(estudiante);
            rc.setFechaAplicacion(Date.valueOf(LocalDate.now()));
            rc.setGrupo(grupo);
            asignaciones.add(rc);
        }
        resultadoCuestionarioRepository.saveAll(asignaciones);
    }
    
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
    
    public ResultCuestCompletoDTO obtenerResultadoCuestionario(Long cuestionarioResueltoId) {
        ResultadoCuestionario resC = resultadoCuestionarioRepository.findById(cuestionarioResueltoId).orElseThrow(
                () -> new EntityNotFoundException("El resultado de id " + cuestionarioResueltoId + " no existe"));
        return obtenerResultadoCuestionario(cuestionarioResueltoId, resC.getEstudiante());
    }
    
    public ResultCuestCompletoDTO obtenerResultadoCuestionario(Long cuestionarioResueltoId, Estudiante estudiante) {
        ResultCuestCompletoDTO res = new ResultCuestCompletoDTO();
        
        ResultadoCuestionario resC = resultadoCuestionarioRepository.findById(cuestionarioResueltoId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "El resultado de id " + cuestionarioResueltoId + " no pertenece al estudiante o no existe"));
        
        if (resC.getFechaResolucion() == null) {
            throw new EntityNotFoundException("El id " + cuestionarioResueltoId
                    + " corresponde a una aplicación de un cuestionario que no se ha completado");
        }
        
        Cuestionario c = resC.getCuestionario();
        res.setCuestionario(CuestionarioResumidoDTO.from(c));
        res.setEstudiante(EstudianteDTO.from(resC.getEstudiante()));
        res.setGrupo(GrupoResumidoDTO.from(resC.getGrupo()));
        res.setFechaAplicacion(resC.getFechaAplicacion());
        res.setFechaResolucion(resC.getFechaResolucion());
        res.setId(resC.getId());
        
        Map<Long, CategoriaResultadoDTO> mp = new TreeMap<>();
        Map<Long, PreguntaResueltaDTO> preg = new TreeMap<>();
        List<CategoriaResultadoDTO> categorias = new LinkedList<>();
        
        for (Categoria categoria : c.getCategorias()) {
            CategoriaResultadoDTO cr = new CategoriaResultadoDTO();
            cr.setNombre(categoria.getNombre());
            cr.setValor(0d);
            cr.setValorMaximo(categoria.getValorMaximo());
            cr.setValorMinimo(categoria.getValorMinimo());
            mp.put(categoria.getId(), cr);
            categorias.add(cr);
        }
        
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
            CategoriaResultadoDTO cr = mp.get(o.getCategoria().getId());
            cr.setValor(cr.getValor() + o.getValor());
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
        System.out.println(preg.values());
        res.setCategorias(categorias);
        res.setPreguntas(new LinkedList<>(preg.values()));
        
        return res;
    }
    
    public void toggleBloqueoCuestionario(Long cuestionarioId, Integer grupoId, Profesor profesor) {
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + cuestionarioId));
        
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el grupo con id " + grupoId));
        
        if (!grupo.getProfesor().getEmail().equalsIgnoreCase(profesor.getEmail())) {
            throw new EntityNotFoundException("El grupo no pertenece a este profesor.");
        }
        
        List<ResultadoCuestionario> rcs = resultadoCuestionarioRepository.findByGrupoAndCuestionario(grupo,
                cuestionario);
        
        for (ResultadoCuestionario rc : rcs) {
            rc.setBloqueado(!rc.isBloqueado());
        }
        
        resultadoCuestionarioRepository.saveAll(rcs);
    }
    
    public ResultadoGrupoDTO obtenerResultadosGrupoCuestionario(Long cuestionarioId, Integer grupoId,
            Profesor profesor) {
        
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + cuestionarioId));
        
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el grupo con id " + grupoId));
        
        if (!grupo.getProfesor().getEmail().equalsIgnoreCase(profesor.getEmail())) {
            throw new EntityNotFoundException("El grupo no pertenece a este profesor.");
        }
        
        List<ResultadoCuestionario> rcs = resultadoCuestionarioRepository.findByGrupoAndCuestionario(grupo,
                cuestionario);
        
        if (rcs.size() == 0) {
            throw new EntityNotFoundException("Este cuestionario no ha sido asignado a ningun estudiante.");
        }
        
        int cnt = 0;
        
        ResultadoGrupoDTO res = new ResultadoGrupoDTO();
        
        res.setCuestionario(CuestionarioResumidoDTO.from(cuestionario));
        res.setGrupo(GrupoResumidoDTO.from(grupo));
        
        Map<Long, CategoriaResultadoDTO> mp = new TreeMap<>();
        List<CategoriaResultadoDTO> categorias = new LinkedList<>();
        List<ResultadoCuestionarioDTO> estudiantesS = new LinkedList<>();
        List<ResultadoCuestionarioDTO> estudiantesUS = new LinkedList<>();
        
        res.setFechaAplicacion(rcs.get(0).getFechaAplicacion());
        
        for (Categoria categoria : cuestionario.getCategorias()) {
            CategoriaResultadoDTO cr = new CategoriaResultadoDTO();
            cr.setNombre(categoria.getNombre());
            cr.setValor(0d);
            cr.setValorMaximo(categoria.getValorMaximo());
            cr.setValorMinimo(categoria.getValorMinimo());
            mp.put(categoria.getId(), cr);
            categorias.add(cr);
        }
        
        for (ResultadoCuestionario rc : rcs) {
            if (rc.getFechaResolucion() != null) {
                cnt++;
                for (ResultadoPregunta rp : rc.getPreguntas()) {
                    Opcion o = rp.getOpcion();
                    Categoria c = o.getCategoria();
                    CategoriaResultadoDTO crdto = mp.get(c.getId());
                    crdto.setValor(crdto.getValor() + o.getValor());
                }
                estudiantesS.add(ResultadoCuestionarioDTO.from(rc));
            } else {
                estudiantesUS.add(ResultadoCuestionarioDTO.from(rc));
            }
        }
        
        for (CategoriaResultadoDTO rca : mp.values()) {
            rca.setValor(rca.getValor() / Double.valueOf(cnt));
        }
        
        res.setCategorias(categorias);
        res.setEstudiantesResuelto(estudiantesS);
        res.setEstudiantesNoResuelto(estudiantesUS);
        
        return res;
    }
    
    public List<ResultadoGrupoResumidoDTO> obtenerPorGrupo(Integer grupoId) {
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el grupo con id " + grupoId));
        
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
