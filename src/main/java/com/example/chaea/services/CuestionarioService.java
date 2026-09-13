package com.example.chaea.services;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.chaea.dto.CategoriaDTO;
import com.example.chaea.dto.CuestionarioDTO;
import com.example.chaea.dto.CuestionarioParaResponderDTO;
import com.example.chaea.dto.CuestionarioResumidoDTO;
import com.example.chaea.dto.PreguntaDTO;
import com.example.chaea.entities.Categoria;
import com.example.chaea.entities.Cuestionario;
import com.example.chaea.entities.Pregunta;
import com.example.chaea.repositories.CuestionarioRepository;
import com.example.chaea.repositories.ResultadoCuestionarioRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class CuestionarioService {
    
    private final CuestionarioRepository cuestionarioRepository;
    
    private final ResultadoCuestionarioRepository resultadoCuestionarioRepository;
    
    private final CategoriaService categoriaService;
    
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
        Cuestionario cuestionarioSave = new Cuestionario();
        cuestionarioSave.setNombre(cuestionarioDTO.getNombre());
        cuestionarioSave.setDescripcion(cuestionarioDTO.getDescripcion());
        cuestionarioSave.setAutor(cuestionarioDTO.getAutor());
        cuestionarioSave.setSiglas(cuestionarioDTO.getSiglas());
        cuestionarioSave.setVersion(cuestionarioDTO.getVersion());
        
        Cuestionario cuestionario = cuestionarioRepository.save(cuestionarioSave);
        
        Map<Integer, Categoria> idMap = new HashMap<>();
        
        Set<Categoria> categorias = new HashSet<>();
        Set<Pregunta> preguntas = new HashSet<>();
        
        for (CategoriaDTO categoriaDTO : cuestionarioDTO.getCategorias()) {
            int otherId = categoriaDTO.getId();
            Categoria categoria = categoriaService.crearCategoria(cuestionario, categoriaDTO);
            idMap.put(otherId, categoria);
            categorias.add(categoria);
        }
        
        for (PreguntaDTO preguntaDTO : cuestionarioDTO.getPreguntas()) {
            Pregunta pregunta = preguntaService.crearPregunta(cuestionario, idMap, preguntaDTO);
            preguntas.add(pregunta);
        }
        
        categoriaService.guardarCategorias(categorias);
        
        cuestionario.setCategorias(categorias);
        cuestionario.setPreguntas(preguntas);
        
        return cuestionarioRepository.save(cuestionario);
    }
    
    public void eliminarCuestionario(Long id) {
        Cuestionario cuestionario = cuestionarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cuestionario no encontrado con el ID: " + id));
        
        eliminarReferenciasAsociadas(cuestionario);
        
        for (Pregunta pregunta : cuestionario.getPreguntas()) {
            preguntaService.eliminarPregunta(pregunta);
        }
        cuestionario.getPreguntas().clear();
        
        for (Categoria categoria : cuestionario.getCategorias()) {
            categoriaService.eliminarCategoria(categoria);
        }
        cuestionario.getCategorias().clear();
        
        cuestionarioRepository.delete(cuestionario);
    }
    
    private void eliminarReferenciasAsociadas(Cuestionario cuestionario) {
        resultadoCuestionarioRepository.deleteByCuestionario(cuestionario);
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
