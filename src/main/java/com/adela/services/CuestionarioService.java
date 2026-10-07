package com.adela.services;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adela.dto.EstiloDTO;
import com.adela.dto.CuestionarioDTO;
import com.adela.dto.CuestionarioParaResponderDTO;
import com.adela.dto.CuestionarioResumidoDTO;
import com.adela.dto.OpcionDTO;
import com.adela.dto.PreguntaDTO;
import com.adela.entities.Estilo;
import com.adela.entities.Cuestionario;
import com.adela.entities.Pregunta;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.CapsulaRepository;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class CuestionarioService {
    
    private final CuestionarioRepository cuestionarioRepository;
    
    private final ResultadoCuestionarioRepository resultadoCuestionarioRepository;
    
    private final CapsulaRepository capsulaRepository;
    
    private final EstiloService estiloService;
    
    private final PreguntaService preguntaService;
    
    public Cuestionario crearCuestionario(String nombre, String descripcion, String autor, String version,
            String siglas) {
        Cuestionario cuestionario = new Cuestionario();
        cuestionario.setNombre(nombre);
        cuestionario.setDescripcion(descripcion);
        cuestionario.setAutor(autor);
        cuestionario.setVersion(version);
        cuestionario.setSiglas(siglas);
        
        return cuestionarioRepository.save(cuestionario);
    }
    
    /**
     * Devuelve el resumen, no la entidad: el conteo de preguntas se calcula dentro
     * de la transacción, donde la colección todavía se puede recorrer.
     */
    @Transactional
    public CuestionarioResumidoDTO crearCuestionarioDTO(CuestionarioDTO cuestionarioDTO) {
        return CuestionarioResumidoDTO.from(crearCuestionario(cuestionarioDTO));
    }

    @Transactional
    public Cuestionario crearCuestionario(CuestionarioDTO cuestionarioDTO) {
        Map<String, String> errores = validarEstructura(cuestionarioDTO);
        if (!errores.isEmpty()) {
            throw new AppException(ErrorCode.VALIDACION, "El cuestionario tiene campos inválidos.", errores);
        }

        Cuestionario cuestionarioSave = new Cuestionario();
        cuestionarioSave.setNombre(cuestionarioDTO.getNombre());
        cuestionarioSave.setDescripcion(cuestionarioDTO.getDescripcion());
        cuestionarioSave.setAutor(cuestionarioDTO.getAutor());
        cuestionarioSave.setSiglas(cuestionarioDTO.getSiglas());
        cuestionarioSave.setVersion(cuestionarioDTO.getVersion());
        
        Cuestionario cuestionario = cuestionarioRepository.save(cuestionarioSave);
        
        Map<Integer, Estilo> idMap = new HashMap<>();
        
        Set<Estilo> estilos = new HashSet<>();
        Set<Pregunta> preguntas = new HashSet<>();
        
        for (EstiloDTO estiloDTO : cuestionarioDTO.getEstilos()) {
            int otherId = estiloDTO.getId();
            Estilo estilo = estiloService.crearEstilo(cuestionario, estiloDTO);
            idMap.put(otherId, estilo);
            estilos.add(estilo);
        }
        
        for (PreguntaDTO preguntaDTO : cuestionarioDTO.getPreguntas()) {
            Pregunta pregunta = preguntaService.crearPregunta(cuestionario, idMap, preguntaDTO);
            preguntas.add(pregunta);
        }
        
        estiloService.guardarEstilos(estilos);
        
        cuestionario.setEstilos(estilos);
        cuestionario.setPreguntas(preguntas);
        
        return cuestionarioRepository.save(cuestionario);
    }
    
    /**
     * Revisa la forma del JSON antes de guardar nada. Sin esto, un JSON con las
     * claves anteriores al renombre (categorias, categoriaId) terminaba en un
     * NullPointerException y un 500 sin pista de qué corregir. Se informa un
     * error por pregunta para no devolver uno por cada opción.
     */
    static Map<String, String> validarEstructura(CuestionarioDTO dto) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (dto.getEstilos() == null || dto.getEstilos().isEmpty()) {
            errores.put("estilos",
                    "Falta la lista de estilos. Si el JSON usa 'categorias', renómbrala a 'estilos'.");
        }
        if (dto.getPreguntas() == null || dto.getPreguntas().isEmpty()) {
            errores.put("preguntas", "Agrega al menos una pregunta.");
            return errores;
        }
        Set<Integer> ids = new HashSet<>();
        if (dto.getEstilos() != null) {
            dto.getEstilos().forEach(e -> ids.add(e.getId()));
        }

        for (int i = 0; i < dto.getPreguntas().size(); i++) {
            PreguntaDTO p = dto.getPreguntas().get(i);
            String campo = "preguntas[" + i + "]";
            if (p.getOpciones() == null || p.getOpciones().isEmpty()) {
                errores.put(campo, "La pregunta " + p.getOrden() + " no tiene opciones.");
                continue;
            }
            for (OpcionDTO o : p.getOpciones()) {
                if (o.getValor() == null) {
                    errores.put(campo, "La pregunta " + p.getOrden() + " tiene una opción sin valor.");
                    break;
                }
                if (!ids.isEmpty() && !ids.contains(o.getEstiloId())) {
                    errores.put(campo, "La pregunta " + p.getOrden() + " usa el estilo " + o.getEstiloId()
                            + ", que no está en 'estilos'. Si el JSON usa 'categoriaId', renómbralo a 'estiloId'.");
                    break;
                }
            }
        }
        return errores;
    }

    public void eliminarCuestionario(Long id) {
        Cuestionario cuestionario = cuestionarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cuestionario no encontrado con el ID: " + id));
        
        eliminarReferenciasAsociadas(cuestionario);
        
        for (Pregunta pregunta : cuestionario.getPreguntas()) {
            preguntaService.eliminarPregunta(pregunta);
        }
        cuestionario.getPreguntas().clear();
        
        for (Estilo estilo : cuestionario.getEstilos()) {
            estiloService.eliminarEstilo(estilo);
        }
        cuestionario.getEstilos().clear();
        
        cuestionarioRepository.delete(cuestionario);
    }
    
    private void eliminarReferenciasAsociadas(Cuestionario cuestionario) {
        resultadoCuestionarioRepository.deleteByCuestionario(cuestionario);
        capsulaRepository.deleteByCuestionario(cuestionario);
    }
    
    /**
     * Arma el DTO dentro de la transacción. Hacerlo en el controlador recorría
     * preguntas y opciones con la sesión ya cerrada.
     */
    @Transactional(readOnly = true)
    public CuestionarioParaResponderDTO obtenerParaResponder(Long id) {
        return CuestionarioParaResponderDTO.from(getCuestionarioPorId(id));
    }

    @Transactional(readOnly = true)
    public Cuestionario getCuestionarioPorId(Long id) {
        return cuestionarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cuestionario no encontrado con el ID: " + id));
    }
    
    public List<CuestionarioResumidoDTO> getCuestionarios() {
        return cuestionarioRepository.resumirTodos();
    }
    
}
