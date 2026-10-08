package com.adela.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adela.calificacion.AgregadoGrupo;
import com.adela.calificacion.AgregadoGrupo.Agregado;
import com.adela.calificacion.ClaveInstrumento;
import com.adela.calificacion.ResultadoInstrumento;
import com.adela.dto.CalificacionDTO;
import com.adela.dto.CapsulaActualizarDTO;
import com.adela.dto.CapsulaCrearDTO;
import com.adela.dto.CapsulaDTO;
import com.adela.dto.CapsulaPublicaDTO;
import com.adela.dto.CapsulaReporteDTO;
import com.adela.dto.CapsulaReporteDTO.ParticipanteDTO;
import com.adela.dto.CuestionarioParaResponderDTO;
import com.adela.dto.CuestionarioResumidoDTO;
import com.adela.dto.EstiloResultadoDTO;
import com.adela.dto.PreguntaResueltaDTO;
import com.adela.dto.RespuestaCapsulaDTO;
import com.adela.dto.ResultadoCapsulaDTO;
import com.adela.entities.Capsula;
import com.adela.entities.Cuestionario;
import com.adela.entities.ModoIdentificacion;
import com.adela.entities.Opcion;
import com.adela.entities.Profesor;
import com.adela.entities.RespuestaCapsula;
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

    /**
     * Corto para dictarlo o teclearlo, como el PIN de un juego en el aula: 31^6 ≈
     * 887 millones. Adivinarlo solo deja responder la cápsula, no ver datos, y el
     * profesor la cierra al terminar.
     */
    static final int LONGITUD_CODIGO_CAPSULA = 6;

    /** Más largo que el de la cápsula: este código da acceso a un resultado. */
    static final int LONGITUD_CODIGO_RESULTADO = 12;

    private static final int INTENTOS_CODIGO = 5;

    private final CapsulaRepository capsulaRepository;

    private final RespuestaCapsulaRepository respuestaCapsulaRepository;

    private final CuestionarioRepository cuestionarioRepository;

    private final EvaluacionRespuestas evaluacionRespuestas;

    private final CalificacionService calificacionService;

    private Capsula delProfesor(Long id, Profesor profesor) {
        return capsulaRepository.findByProfesorAndId(profesor, id)
                .orElseThrow(() -> new EntityNotFoundException("Cápsula no encontrada con el ID: " + id));
    }

    /**
     * Chocar con un código existente es tan improbable que basta con reintentar
     * unas veces; la columna unique respalda la comprobación.
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

    /**
     * existsByCodigo descarta los códigos ya usados, pero dos creaciones
     * simultáneas pueden sacar el mismo código libre a la vez; el unique de la
     * columna rechaza la segunda y aquí se reintenta con otro.
     *
     * Sin @Transactional a propósito: en Postgres una transacción que falla queda
     * abortada y no admite el reintento. Cada saveAndFlush va en la suya.
     */
    public CapsulaDTO crear(CapsulaCrearDTO dto, Profesor profesor) {
        Cuestionario cuestionario = cuestionarioRepository.findById(dto.cuestionarioId())
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + dto.cuestionarioId()));

        for (int intento = 1;; intento++) {
            Capsula capsula = new Capsula();
            capsula.setCodigo(codigoLibre(LONGITUD_CODIGO_CAPSULA, capsulaRepository::existsByCodigo));
            capsula.setNombre(dto.nombre().strip());
            capsula.setProfesor(profesor);
            capsula.setCuestionario(cuestionario);
            capsula.setModoIdentificacion(dto.modoIdentificacion());
            capsula.setCreadaEn(Instant.now());
            try {
                return CapsulaDTO.from(capsulaRepository.saveAndFlush(capsula), 0);
            } catch (DataIntegrityViolationException e) {
                // Solo el choque de código se reintenta; cualquier otra violación sube.
                if (intento >= INTENTOS_CODIGO || !capsulaRepository.existsByCodigo(capsula.getCodigo())) {
                    throw e;
                }
            }
        }
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

    /**
     * Misma agregación que el reporte de un grupo: cada respuesta se califica
     * con el motor y se resume por estilo y por perfil.
     */
    @Transactional(readOnly = true)
    public CapsulaReporteDTO reporte(Long id, Profesor profesor) {
        Capsula capsula = delProfesor(id, profesor);
        Cuestionario cuestionario = capsula.getCuestionario();
        List<RespuestaCapsula> respuestas = respuestaCapsulaRepository.findByCapsulaOrderByRespondidaEn(capsula);

        ClaveInstrumento clave = calificacionService.clave(cuestionario);
        boolean conNombre = capsula.getModoIdentificacion() == ModoIdentificacion.NOMBRE;
        List<ParticipanteDTO> participantes = conNombre ? new ArrayList<>() : null;
        List<ResultadoInstrumento> resultados = new ArrayList<>();

        for (RespuestaCapsula r : respuestas) {
            ResultadoInstrumento resultado = calificacionService.calificar(cuestionario, clave, r.getCantidades());
            resultados.add(resultado);
            if (conNombre) {
                participantes.add(new ParticipanteDTO(r.getNombre(), r.getRespondidaEn(),
                        resultado.perfilEtiqueta()));
            }
        }

        Agregado agregado = AgregadoGrupo.de(clave, resultados, AgregadoGrupo.N_MINIMO_LOCAL);
        return new CapsulaReporteDTO(CapsulaDTO.from(capsula, respuestas.size()), respuestas.size(),
                agregado.estilos().stream().map(EstiloResultadoDTO::de).toList(),
                CalificacionDTO.grupal(cuestionario, agregado), participantes);
    }

    /** Las respuestas caen con ella por ON DELETE CASCADE. */
    @Transactional
    public void eliminar(Long id, Profesor profesor) {
        capsulaRepository.delete(delProfesor(id, profesor));
    }

    // ---- Ruta pública: sin cuenta, solo con el código del enlace ----

    private Capsula porCodigo(String codigo) {
        String normalizado = CodigoAleatorio.normalizar(codigo);
        // Un código de otra longitud no puede existir: se descarta sin consultar.
        if (normalizado.length() != LONGITUD_CODIGO_CAPSULA) {
            throw new EntityNotFoundException("La cápsula no existe.");
        }
        return capsulaRepository.findByCodigo(normalizado)
                .orElseThrow(() -> new EntityNotFoundException("La cápsula no existe."));
    }

    private static void exigirAbierta(Capsula capsula) {
        if (!capsula.isAbierta()) {
            throw new AppException(ErrorCode.CAPSULA_CERRADA, "Esta cápsula ya no recibe respuestas.");
        }
    }

    @Transactional(readOnly = true)
    public CapsulaPublicaDTO paraResponder(String codigo) {
        Capsula capsula = porCodigo(codigo);
        exigirAbierta(capsula);
        return new CapsulaPublicaDTO(capsula.getCodigo(), capsula.getNombre(), capsula.getModoIdentificacion(),
                CuestionarioParaResponderDTO.from(capsula.getCuestionario()));
    }

    /**
     * Guarda una resolución. Reenviar el mismo intento devuelve el resultado ya
     * guardado. Si dos envíos del mismo intento llegan a la vez, el segundo choca
     * con UNIQUE (capsula_id, intento) y responde 409; al reintentar, encuentra el
     * primero aquí.
     */
    @Transactional
    public ResultadoCapsulaDTO responder(String codigo, RespuestaCapsulaDTO dto) {
        Capsula capsula = porCodigo(codigo);
        exigirAbierta(capsula);

        Optional<RespuestaCapsula> previa = respuestaCapsulaRepository.findByCapsulaAndIntento(capsula,
                dto.intento());
        if (previa.isPresent()) {
            return resultadoDe(previa.get());
        }

        String nombre = nombreValido(capsula.getModoIdentificacion(), dto.nombre());
        Cuestionario cuestionario = capsula.getCuestionario();
        Map<Opcion, Double> opciones = evaluacionRespuestas.validarSeleccion(cuestionario,
                dto.opcionesSeleccionadasId(), dto.cantidades());

        RespuestaCapsula respuesta = new RespuestaCapsula();
        respuesta.setCapsula(capsula);
        respuesta.setCodigo(codigoLibre(LONGITUD_CODIGO_RESULTADO, respuestaCapsulaRepository::existsByCodigo));
        respuesta.setIntento(dto.intento());
        respuesta.setNombre(nombre);
        respuesta.setRespondidaEn(Instant.now());
        opciones.forEach((opcion, cantidad) -> respuesta.getCantidades().put(opcion.getId(), cantidad));
        return resultadoDe(respuestaCapsulaRepository.save(respuesta));
    }

    /**
     * En modo ANONIMO el nombre se descarta aunque el cliente lo envíe: el
     * servidor decide qué dato personal se guarda, no el formulario.
     */
    static String nombreValido(ModoIdentificacion modo, String nombre) {
        if (modo == ModoIdentificacion.ANONIMO) {
            return null;
        }
        String limpio = nombre == null ? ""
                : nombre.replaceAll("\\s", " ").replaceAll("[\\p{Cntrl}\\p{Cf}]", "").replaceAll(" +", " ").strip();
        if (limpio.isEmpty() || limpio.length() > 60) {
            throw new AppException(ErrorCode.VALIDACION, "Revisa los campos marcados.",
                    Map.of("nombre", "Escribe un nombre de hasta 60 caracteres"));
        }
        return limpio;
    }

    /** Sigue disponible aunque la cápsula se cierre; desaparece si se elimina. */
    @Transactional(readOnly = true)
    public ResultadoCapsulaDTO resultado(String codigo) {
        String normalizado = CodigoAleatorio.normalizar(codigo);
        if (normalizado.length() != LONGITUD_CODIGO_RESULTADO) {
            throw new EntityNotFoundException("No hay un resultado con ese código.");
        }
        return respuestaCapsulaRepository.findByCodigo(normalizado).map(this::resultadoDe)
                .orElseThrow(() -> new EntityNotFoundException("No hay un resultado con ese código."));
    }

    private ResultadoCapsulaDTO resultadoDe(RespuestaCapsula respuesta) {
        Capsula capsula = respuesta.getCapsula();
        Cuestionario cuestionario = capsula.getCuestionario();

        List<PreguntaResueltaDTO> preguntas = EvaluacionRespuestas.preguntasResueltas(cuestionario,
                respuesta.getCantidades());
        EvaluacionRespuestas.Puntuacion puntuacion = evaluacionRespuestas.puntuar(cuestionario,
                respuesta.getCantidades());
        return new ResultadoCapsulaDTO(respuesta.getCodigo(), capsula.getNombre(),
                CuestionarioResumidoDTO.from(cuestionario), respuesta.getNombre(), respuesta.getRespondidaEn(),
                puntuacion.estilos(), puntuacion.calificacion(), preguntas);
    }
}
