package com.adela.services;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adela.dto.RespuestaCuestionarioDTO;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estudiante;
import com.adela.entities.Genero;
import com.adela.entities.Grupo;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.entities.PreguntaComplementaria;
import com.adela.entities.Profesor;
import com.adela.entities.ResultadoCuestionario;
import com.adela.entities.UsuarioEstado;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.EstudianteRepository;
import com.adela.repositories.GrupoRepository;
import com.adela.repositories.ResultadoCuestionarioRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Solo desarrollo: puebla un grupo con estudiantes ficticios que ya respondieron
 * un cuestionario, para ver los reportes con datos sin contestar a mano.
 *
 * Cada estudiante recibe una afinidad aleatoria por estilo y elige las opciones
 * con probabilidad creciente según lo que suman a sus estilos afines. Con azar
 * uniforme todos los perfiles saldrían planos y cerca de la media; así salen
 * perfiles marcados y variados, como en un grupo real.
 *
 * Las respuestas entran por responderCuestionario, el mismo camino que las de
 * un estudiante, así que pasan la misma validación.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "adela.dev.simulacion", havingValue = "true")
public class SimulacionService {

    public static final int MAX_ESTUDIANTES = 200;

    /** Cuánto pesa la afinidad frente al azar: 0 = uniforme. */
    private static final double INTENSIDAD = 1.5;

    /**
     * En opción múltiple, probabilidad de marcar una más tras cada marca. Con
     * azar uniforme entre 1 y k se marcaban ~2,5 de 4 y la suma inflada volvía
     * multimodal casi a todos; una persona suele marcar una, a veces dos.
     */
    private static final double OTRA_MARCA = 0.35;

    /** Parte de los simulados a los que les toca y responden la pregunta complementaria. */
    private static final double RESPONDE_COMPLEMENTARIA = 0.85;

    private static final String[] NOMBRES = { "Ana", "Luis", "María", "Carlos", "Laura", "Andrés", "Sofía",
            "Juan", "Valentina", "Diego", "Camila", "Santiago", "Daniela", "Mateo", "Paula", "Sebastián" };

    private static final String[] APELLIDOS = { "Gómez", "Rodríguez", "Martínez", "López", "García", "Pérez",
            "Sánchez", "Ramírez", "Torres", "Flórez", "Rojas", "Vargas", "Moreno", "Jaimes", "Ortiz", "Suárez" };

    private final GrupoRepository grupoRepository;

    private final CuestionarioRepository cuestionarioRepository;

    private final EstudianteRepository estudianteRepository;

    private final ResultadoCuestionarioRepository resultadoCuestionarioRepository;

    private final ResultadoCuestionarioService resultadoCuestionarioService;

    private final EvaluacionRespuestas evaluacionRespuestas;

    private final Random azar = new Random();

    @Transactional
    public int simular(int grupoId, Long cuestionarioId, int cantidad, Profesor profesor) {
        if (cantidad < 1 || cantidad > MAX_ESTUDIANTES) {
            throw new AppException(ErrorCode.VALIDACION,
                    "La cantidad debe estar entre 1 y " + MAX_ESTUDIANTES + ".");
        }
        Grupo grupo = grupoRepository.findByProfesorAndId(profesor, grupoId)
                .orElseThrow(() -> new EntityNotFoundException("Grupo no encontrado con el ID: " + grupoId));
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + cuestionarioId));

        // Misma fecha de aplicación que el resto del grupo: el reporte toma la primera.
        Date fechaAplicacion = resultadoCuestionarioRepository.findByGrupoAndCuestionario(grupo, cuestionario)
                .stream().map(ResultadoCuestionario::getFechaAplicacion).findFirst()
                .orElse(Date.valueOf(LocalDate.now()));

        List<Pregunta> preguntas = cuestionario.getPreguntas().stream()
                .sorted(Comparator.comparingInt(Pregunta::getOrden)).toList();

        for (int i = 0; i < cantidad; i++) {
            Estudiante estudiante = estudianteRepository.save(estudianteFicticio(grupo));

            ResultadoCuestionario rc = new ResultadoCuestionario();
            rc.setCuestionario(cuestionario);
            rc.setEstudiante(estudiante);
            rc.setGrupo(grupo);
            rc.setFechaAplicacion(fechaAplicacion);
            rc = resultadoCuestionarioRepository.save(rc);

            RespuestaCuestionarioDTO respuesta = responder(preguntas);
            respuesta.setCuestionarioId(cuestionarioId);
            respuesta.setResultadoCuestionarioId(rc.getId());
            ResultadoCuestionario resuelto = resultadoCuestionarioService.responderCuestionario(respuesta,
                    estudiante);
            responderComplementaria(cuestionario, respuesta, resuelto);
        }
        return cantidad;
    }

    /**
     * Si le toca la pregunta complementaria, la mayoría la responde al azar y el
     * resto la deja sin declarar, como pasa con quien no vuelve a su resultado.
     */
    private void responderComplementaria(Cuestionario cuestionario, RespuestaCuestionarioDTO respuesta,
            ResultadoCuestionario resuelto) {
        PreguntaComplementaria p = cuestionario.getPreguntaComplementaria();
        if (p == null || azar.nextDouble() >= RESPONDE_COMPLEMENTARIA) {
            return;
        }
        Map<Long, Double> cantidades = new HashMap<>(respuesta.getCantidades());
        respuesta.getOpcionesSeleccionadasId().forEach(id -> cantidades.put(id, 1.0));
        if (EvaluacionRespuestas.complementaria(cuestionario,
                evaluacionRespuestas.puntuar(cuestionario, cantidades).estilos()) != null) {
            resuelto.setOpcionComplementaria(p.getOpciones().get(azar.nextInt(p.getOpciones().size())));
        }
    }

    private Estudiante estudianteFicticio(Grupo grupo) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Estudiante e = new Estudiante();
        e.setEmail("sim-" + sufijo + "@adela.dev");
        e.setNombre(NOMBRES[azar.nextInt(NOMBRES.length)] + " " + APELLIDOS[azar.nextInt(APELLIDOS.length)]
                + " (sim)");
        e.setEstado(UsuarioEstado.ACTIVA);
        e.setGenero(Genero.values()[azar.nextInt(Genero.values().length)]);
        e.setFecha_nacimiento(Date.valueOf(LocalDate.now().minusYears(17 + azar.nextInt(9))
                .minusDays(azar.nextInt(365))));
        e.getGrupos().add(grupo);
        return e;
    }

    /** Una resolución completa y válida, sesgada por una afinidad propia. */
    private RespuestaCuestionarioDTO responder(List<Pregunta> preguntas) {
        Map<Long, Double> afinidad = new HashMap<>();
        List<Long> marcadas = new ArrayList<>();
        Map<Long, Double> cantidades = new LinkedHashMap<>();

        for (Pregunta p : preguntas) {
            List<Opcion> opciones = p.getOpciones().stream()
                    .sorted(Comparator.comparingInt(Opcion::getOrden)).toList();
            Map<Opcion, Double> peso = new HashMap<>();
            for (Opcion o : opciones) {
                double s = 0;
                for (Map.Entry<Long, Double> w : o.getPesos().entrySet()) {
                    s += w.getValue() * afinidad.computeIfAbsent(w.getKey(), k -> azar.nextGaussian());
                }
                peso.put(o, Math.exp(INTENSIDAD * s));
            }
            int k = opciones.size();

            switch (p.getFormato()) {
                case UNICA -> marcadas.add(sinReemplazo(opciones, peso, 1).get(0).getId());
                case MULTIPLE -> {
                    int max = p.getMaxSelecciones() == null ? k : Math.min(p.getMaxSelecciones(), k);
                    int t = Math.max(1, p.getMinSelecciones());
                    while (t < max && azar.nextDouble() < OTRA_MARCA) {
                        t++;
                    }
                    sinReemplazo(opciones, peso, t).forEach(o -> marcadas.add(o.getId()));
                }
                case JERARQUIA -> {
                    // La primera elegida es la que más la describe: recibe el rango más alto.
                    List<Opcion> orden = sinReemplazo(opciones, peso, k);
                    for (int r = 0; r < k; r++) {
                        cantidades.put(orden.get(r).getId(), (double) (k - r));
                    }
                }
                case REPARTO -> {
                    opciones.forEach(o -> cantidades.put(o.getId(), 0.0));
                    for (int punto = 0; punto < p.getPuntosRepartir(); punto++) {
                        cantidades.merge(sinReemplazo(opciones, peso, 1).get(0).getId(), 1.0, Double::sum);
                    }
                }
            }
        }

        RespuestaCuestionarioDTO dto = new RespuestaCuestionarioDTO();
        dto.setOpcionesSeleccionadasId(marcadas);
        dto.setCantidades(cantidades);
        return dto;
    }

    /** n opciones distintas, cada una con probabilidad proporcional a su peso. */
    private List<Opcion> sinReemplazo(List<Opcion> opciones, Map<Opcion, Double> peso, int n) {
        List<Opcion> quedan = new ArrayList<>(opciones);
        List<Opcion> elegidas = new ArrayList<>();
        while (elegidas.size() < n) {
            double total = quedan.stream().mapToDouble(peso::get).sum();
            double x = azar.nextDouble() * total;
            int i = 0;
            while (i < quedan.size() - 1 && (x -= peso.get(quedan.get(i))) > 0) {
                i++;
            }
            elegidas.add(quedan.remove(i));
        }
        return elegidas;
    }
}
