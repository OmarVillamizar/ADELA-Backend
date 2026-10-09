package com.adela.services;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.adela.calificacion.Banda;
import com.adela.calificacion.ClaveInconsistenteException;
import com.adela.calificacion.ClaveInstrumento;
import com.adela.calificacion.ConfigInterpretacion;
import com.adela.calificacion.Escalon;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.EstiloClave;
import com.adela.calificacion.ItemClave;
import com.adela.calificacion.MotorCalificacion;
import com.adela.calificacion.OpcionClave;
import com.adela.calificacion.Plano;
import com.adela.calificacion.RespuestaItem;
import com.adela.calificacion.ResultadoInstrumento;
import com.adela.entities.Cuestionario;
import com.adela.entities.Estilo;
import com.adela.entities.Opcion;
import com.adela.entities.Pregunta;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.BandaInterpretacionRepository;
import com.adela.repositories.EscalonRelativoRepository;
import com.adela.repositories.PlanoCuadrantesRepository;

import lombok.RequiredArgsConstructor;

/**
 * Puente entre las entidades y el motor de calificación, que no conoce JPA.
 * Se califica al leer: los cuestionarios no se editan, así que la clave nunca
 * cambia y recalcular siempre da el mismo resultado.
 */
@Service
@RequiredArgsConstructor
public class CalificacionService {

    private static final Logger log = LoggerFactory.getLogger(CalificacionService.class);

    private final BandaInterpretacionRepository bandaRepository;

    private final EscalonRelativoRepository escalonRepository;

    private final PlanoCuadrantesRepository planoRepository;

    /** Requiere la sesión abierta: recorre preguntas, opciones y estilos. */
    public ClaveInstrumento clave(Cuestionario c) {
        List<Estilo> estilos = c.getEstilos().stream().sorted(Comparator.comparing(Estilo::getId)).toList();
        List<EstiloClave> estilosClave = IntStream.range(0, estilos.size())
                .mapToObj(i -> new EstiloClave(estilos.get(i).getId(), estilos.get(i).getNombre(),
                        estilos.get(i).getTipo(), i + 1, estilos.get(i).getCoeficientes()))
                .toList();

        List<Banda> bandas = bandaRepository.findByEstiloCuestionario(c).stream()
                .map(b -> new Banda(b.getEstilo().getId(), b.getEscala(), b.getLimiteInferior(),
                        b.getLimiteSuperior(), b.getEtiqueta(), b.getOrden()))
                .toList();
        List<Escalon> escalones = escalonRepository.findByCuestionario(c).stream()
                .map(e -> new Escalon(e.getTotalMin(), e.getTotalMax(), e.getDistancia())).toList();
        // La clave del plano es el id del cuestionario; solo cuenta con el esquema CUADRANTES.
        Plano plano = c.getEsquemaInterpretacion() != EsquemaInterpretacion.CUADRANTES ? null
                : planoRepository.findById(c.getId())
                        .map(p -> new Plano(p.getEjeX().getId(), p.getEjeY().getId(), p.getCorteX(), p.getCorteY(),
                                p.getXAltoYAlto(), p.getXBajoYAlto(), p.getXBajoYBajo(), p.getXAltoYBajo(),
                                p.isInvertirX(), p.isInvertirY()))
                        .orElse(null);
        ConfigInterpretacion config = new ConfigInterpretacion(c.getEsquemaInterpretacion(), c.getDeltaRelativo(),
                bandas, escalones, plano);

        return conClaveValida(c, () -> new ClaveInstrumento(c.getId(), false,
                c.getPreguntas().stream().sorted(Comparator.comparingInt(Pregunta::getOrden)).map(this::item)
                        .toList(),
                estilosClave, config));
    }

    /** Ítem de la clave; también sirve para validar una respuesta con las reglas del motor. */
    public ItemClave item(Pregunta p) {
        List<OpcionClave> opciones = p.getOpciones().stream().sorted(Comparator.comparingInt(Opcion::getOrden))
                .map(o -> new OpcionClave(o.getId(), o.getPesos())).toList();
        return new ItemClave(p.getId(), p.getFormato(), p.isObligatoria(), p.getMinSelecciones(),
                p.getMaxSelecciones(), p.getPuntosRepartir(), opciones);
    }

    /**
     * cantidadPorOpcion: id de la opción elegida -> cantidad (1 si se marcó, el
     * rango en jerarquía, los puntos en reparto). Se agrupa por ítem con la clave.
     */
    public ResultadoInstrumento calificar(Cuestionario c, ClaveInstrumento clave,
            Map<Long, Double> cantidadPorOpcion) {
        Map<Long, Long> itemDe = new HashMap<>();
        clave.items().forEach(it -> it.opciones().forEach(o -> itemDe.put(o.opcionId(), it.itemId())));
        Map<Long, Map<Long, Double>> porItem = new HashMap<>();
        cantidadPorOpcion.forEach((opcion, cantidad) -> porItem
                .computeIfAbsent(itemDe.get(opcion), k -> new HashMap<>()).put(opcion, cantidad));
        Map<Long, RespuestaItem> respuestas = new HashMap<>();
        porItem.forEach((item, cantidades) -> respuestas.put(item, new RespuestaItem(item, cantidades)));
        return conClaveValida(c, () -> MotorCalificacion.calificar(clave, respuestas));
    }

    /**
     * Una clave contradictoria es un error de configuración del cuestionario, no
     * de quien responde: se registra para el administrador y se responde con un
     * código propio en lugar de un 500 genérico.
     */
    private static <T> T conClaveValida(Cuestionario c, Supplier<T> paso) {
        try {
            return paso.get();
        } catch (ClaveInconsistenteException e) {
            log.warn("Clave inconsistente en el cuestionario {}: {}", c.getId(), e.getMessage());
            throw new AppException(ErrorCode.CLAVE_INCONSISTENTE,
                    "La configuración de calificación del cuestionario es inconsistente. Avisa al administrador.");
        }
    }
}
