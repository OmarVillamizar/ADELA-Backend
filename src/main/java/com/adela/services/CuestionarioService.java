package com.adela.services;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adela.calificacion.FormatoItem;
import com.adela.calificacion.TipoEstilo;
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
        // Jerarquizar o repartir suma una constante por ítem: los puntajes de una
        // persona dependen entre sí y la media del grupo se lee con advertencia.
        cuestionarioSave.setEsIpsativo(cuestionarioDTO.getPreguntas().stream()
                .anyMatch(p -> p.getFormato() == FormatoItem.JERARQUIA || p.getFormato() == FormatoItem.REPARTO));

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
        // Los coeficientes apuntan a ids locales: se traducen cuando ya existen todos los estilos.
        for (EstiloDTO estiloDTO : cuestionarioDTO.getEstilos()) {
            if (estiloDTO.getCoeficientes() != null) {
                Map<Long, Double> coeficientes = new HashMap<>();
                estiloDTO.getCoeficientes()
                        .forEach(c -> coeficientes.put(idMap.get(c.estiloId()).getId(), c.coeficiente()));
                idMap.get(estiloDTO.getId()).setCoeficientes(coeficientes);
            }
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
        // id local -> tipo; los pesos y los coeficientes solo pueden apuntar a primarios.
        Map<Integer, TipoEstilo> tipos = new HashMap<>();
        if (dto.getEstilos() != null) {
            Set<String> nombres = new HashSet<>();
            for (int i = 0; i < dto.getEstilos().size(); i++) {
                EstiloDTO e = dto.getEstilos().get(i);
                String nombre = e.getNombre() == null ? "" : e.getNombre().trim();
                if (nombre.isEmpty()) {
                    errores.put("estilos[" + i + "]", "Hay un estilo sin nombre.");
                } else if (!nombres.add(nombre)) {
                    // Las bandas de interpretación nombran el estilo: el nombre debe ser único.
                    errores.put("estilos[" + i + "]", "El estilo '" + nombre + "' está repetido.");
                } else if (tipos.putIfAbsent(e.getId(), tipoDe(e)) != null) {
                    errores.put("estilos[" + i + "]", "El id " + e.getId() + " de '" + nombre + "' está repetido.");
                }
            }
            if (!tipos.isEmpty() && !tipos.containsValue(TipoEstilo.PRIMARIO)) {
                errores.put("estilos", "Debe haber al menos un estilo primario.");
            }
            for (int i = 0; i < dto.getEstilos().size(); i++) {
                String error = errorCoeficientes(dto.getEstilos().get(i), tipos);
                if (error != null) {
                    errores.putIfAbsent("estilos[" + i + "]", error);
                }
            }
        }

        for (int i = 0; i < dto.getPreguntas().size(); i++) {
            PreguntaDTO p = dto.getPreguntas().get(i);
            String error = errorPregunta(p, tipos);
            if (error != null) {
                errores.put("preguntas[" + i + "]", "La pregunta " + p.getOrden() + " " + error);
            }
        }
        return errores;
    }

    private static TipoEstilo tipoDe(EstiloDTO e) {
        return e.getTipo() != null ? e.getTipo() : TipoEstilo.PRIMARIO;
    }

    /** Un compuesto es una combinación de primarios; un primario no lleva coeficientes. */
    private static String errorCoeficientes(EstiloDTO e, Map<Integer, TipoEstilo> tipos) {
        boolean vacio = e.getCoeficientes() == null || e.getCoeficientes().isEmpty();
        if (tipoDe(e) == TipoEstilo.PRIMARIO) {
            return vacio ? null : "El estilo primario '" + e.getNombre() + "' no lleva coeficientes.";
        }
        if (vacio) {
            return "El compuesto '" + e.getNombre() + "' necesita al menos un coeficiente.";
        }
        Set<Integer> usados = new HashSet<>();
        for (EstiloDTO.CoeficienteDTO c : e.getCoeficientes()) {
            if (tipos.get(c.estiloId()) != TipoEstilo.PRIMARIO) {
                return "El compuesto '" + e.getNombre() + "' usa el estilo " + c.estiloId()
                        + ", que no es un primario de 'estilos'.";
            }
            if (c.coeficiente() == null || c.coeficiente() == 0 || !Double.isFinite(c.coeficiente())) {
                return "El compuesto '" + e.getNombre() + "' tiene un coeficiente vacío o en 0.";
            }
            if (!usados.add(c.estiloId())) {
                return "El compuesto '" + e.getNombre() + "' repite el estilo " + c.estiloId() + ".";
            }
        }
        return null;
    }

    /** Primer problema de la pregunta, o null. Un error por pregunta para no devolver uno por opción. */
    private static String errorPregunta(PreguntaDTO p, Map<Integer, TipoEstilo> tipos) {
        if (p.getOpciones() == null || p.getOpciones().isEmpty()) {
            return "no tiene opciones.";
        }
        FormatoItem formato = p.getFormato();
        if (formato == null) {
            return "no tiene formato (UNICA, MULTIPLE, JERARQUIA o REPARTO).";
        }
        int k = p.getOpciones().size();
        boolean conSeleccion = p.getMinSelecciones() != null || p.getMaxSelecciones() != null;
        if (formato == FormatoItem.MULTIPLE) {
            int min = p.getMinSelecciones() != null ? p.getMinSelecciones() : 0;
            Integer max = p.getMaxSelecciones();
            if (min < 0 || min > k) {
                return "pide un mínimo de " + min + " opciones y tiene " + k + ".";
            }
            if (max != null && (max < Math.max(min, 1) || max > k)) {
                return "permite un máximo de " + max + " opciones; debe estar entre " + Math.max(min, 1) + " y "
                        + k + ".";
            }
        } else if (conSeleccion) {
            return "solo puede tener mínimo y máximo de selecciones si es MULTIPLE.";
        }
        if (formato == FormatoItem.REPARTO) {
            if (p.getPuntosRepartir() == null || p.getPuntosRepartir() <= 0) {
                return "es de reparto y necesita puntos a repartir mayores que 0.";
            }
        } else if (p.getPuntosRepartir() != null) {
            return "solo puede tener puntos a repartir si es REPARTO.";
        }
        if (formato == FormatoItem.JERARQUIA && k < 2) {
            return "es de jerarquía y necesita al menos 2 opciones.";
        }
        for (OpcionDTO o : p.getOpciones()) {
            if (o.getPesos() == null) {
                return "tiene una opción sin 'pesos'. Si el JSON usa 'valor' y 'estiloId', pásalos a "
                        + "'pesos': [{\"estiloId\": ..., \"peso\": ...}].";
            }
            Set<Integer> usados = new HashSet<>();
            for (OpcionDTO.PesoDTO w : o.getPesos()) {
                if (!tipos.isEmpty() && tipos.get(w.estiloId()) != TipoEstilo.PRIMARIO) {
                    return "usa el estilo " + w.estiloId() + ", que no es un primario de 'estilos'.";
                }
                if (w.peso() == null || !Double.isFinite(w.peso())) {
                    return "tiene una opción con un peso vacío.";
                }
                if (!usados.add(w.estiloId())) {
                    return "tiene una opción que repite el estilo " + w.estiloId() + ".";
                }
            }
        }
        return null;
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
