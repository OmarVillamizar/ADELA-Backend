package com.adela.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adela.calificacion.EscalaBanda;
import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.InterpretacionDTO.BandaDTO;
import com.adela.dto.InterpretacionDTO.EscalonDTO;
import com.adela.entities.BandaInterpretacion;
import com.adela.entities.Cuestionario;
import com.adela.entities.EscalonRelativo;
import com.adela.entities.Estilo;
import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.repositories.BandaInterpretacionRepository;
import com.adela.repositories.CuestionarioRepository;
import com.adela.repositories.EscalonRelativoRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Configuración de la interpretación de un cuestionario. Se califica al leer,
 * así que cambiarla reinterpreta los resultados existentes: el puntaje no
 * cambia, solo el nivel y el perfil que se derivan de él.
 */
@Service
@RequiredArgsConstructor
public class InterpretacionService {

    private final CuestionarioRepository cuestionarioRepository;

    private final BandaInterpretacionRepository bandaRepository;

    private final EscalonRelativoRepository escalonRepository;

    @Transactional(readOnly = true)
    public InterpretacionDTO obtener(Long cuestionarioId) {
        Cuestionario c = cuestionario(cuestionarioId);
        List<BandaDTO> bandas = bandaRepository.findByEstiloCuestionario(c).stream()
                .sorted(Comparator.comparing((BandaInterpretacion b) -> b.getEstilo().getId())
                        .thenComparing(b -> b.getEscala()).thenComparingInt(BandaInterpretacion::getOrden))
                .map(b -> new BandaDTO(b.getEstilo().getNombre(), b.getEscala(), b.getLimiteInferior(),
                        b.getLimiteSuperior(), b.getEtiqueta(), b.getOrden()))
                .toList();
        List<EscalonDTO> escalones = escalonRepository.findByCuestionario(c).stream()
                .sorted(Comparator.comparingDouble(EscalonRelativo::getTotalMin))
                .map(e -> new EscalonDTO(e.getTotalMin(), e.getTotalMax(), e.getDistancia())).toList();
        return new InterpretacionDTO(c.getEsquemaInterpretacion(), c.getDeltaRelativo(), c.isEsIpsativo(), bandas,
                escalones);
    }

    /** Reemplaza toda la configuración: lo que no llega se borra. */
    @Transactional
    public InterpretacionDTO guardar(Long cuestionarioId, InterpretacionDTO dto) {
        Cuestionario c = cuestionario(cuestionarioId);
        Map<String, Estilo> estilos = new HashMap<>();
        c.getEstilos().forEach(e -> estilos.putIfAbsent(e.getNombre(), e));
        List<BandaDTO> bandas = dto.bandas() == null ? List.of() : dto.bandas();
        List<EscalonDTO> escalones = dto.escalones() == null ? List.of() : dto.escalones();

        Map<String, String> errores = validar(dto, bandas, escalones, estilos);
        if (!errores.isEmpty()) {
            throw new AppException(ErrorCode.VALIDACION, "Revisa los campos marcados.", errores);
        }

        // Borrado masivo antes de insertar: con remove() Hibernate inserta primero
        // y las bandas repetidas chocarían con UNIQUE (estilo_id, escala, orden).
        bandaRepository.borrarDeCuestionario(c);
        escalonRepository.borrarDeCuestionario(c);

        c.setEsquemaInterpretacion(dto.esquema());
        c.setDeltaRelativo(dto.delta() == null ? 10 : dto.delta());
        // null conserva el valor: al crear, el ipsativo se deduce de los formatos.
        if (dto.esIpsativo() != null) {
            c.setEsIpsativo(dto.esIpsativo());
        }
        cuestionarioRepository.save(c);

        bandaRepository.saveAll(bandas.stream().map(b -> {
            BandaInterpretacion e = new BandaInterpretacion();
            e.setEstilo(estilos.get(b.estilo()));
            e.setEscala(b.escala());
            e.setLimiteInferior(b.limiteInferior());
            e.setLimiteSuperior(b.limiteSuperior());
            e.setEtiqueta(b.etiqueta().strip());
            e.setOrden(b.orden());
            return e;
        }).toList());
        escalonRepository.saveAll(escalones.stream().map(s -> {
            EscalonRelativo e = new EscalonRelativo();
            e.setCuestionario(c);
            e.setTotalMin(s.totalMin());
            e.setTotalMax(s.totalMax());
            e.setDistancia(s.distancia());
            return e;
        }).toList());

        return new InterpretacionDTO(c.getEsquemaInterpretacion(), c.getDeltaRelativo(), c.isEsIpsativo(), bandas,
                escalones);
    }

    private Cuestionario cuestionario(Long id) {
        return cuestionarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el cuestionario con id " + id));
    }

    static Map<String, String> validar(InterpretacionDTO dto, List<BandaDTO> bandas, List<EscalonDTO> escalones,
            Map<String, Estilo> estilos) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (dto.esquema() == null) {
            errores.put("esquema", "Elige un esquema de interpretación");
        }
        if (dto.delta() != null && (dto.delta() < 0 || dto.delta() > 100)) {
            errores.put("delta", "El delta debe estar entre 0 y 100");
        }

        for (int i = 0; i < bandas.size(); i++) {
            BandaDTO b = bandas.get(i);
            String campo = "bandas[" + i + "]";
            if (b.estilo() == null || !estilos.containsKey(b.estilo())) {
                errores.put(campo, "El estilo '" + b.estilo() + "' no existe en el cuestionario");
            } else if (b.escala() == null || b.orden() == null) {
                errores.put(campo, "Indica la escala y el orden");
            } else if (b.limiteInferior() == null || b.limiteSuperior() == null
                    || b.limiteInferior() > b.limiteSuperior()) {
                errores.put(campo, "El límite inferior debe ser menor o igual al superior");
            } else if (b.etiqueta() == null || b.etiqueta().isBlank() || b.etiqueta().strip().length() > 60) {
                errores.put(campo, "Escribe una etiqueta de hasta 60 caracteres");
            }
        }
        if (errores.isEmpty()) {
            validarSolapes(bandas, errores);
        }

        for (int i = 0; i < escalones.size(); i++) {
            EscalonDTO s = escalones.get(i);
            if (s.totalMin() == null || s.totalMax() == null || s.distancia() == null
                    || s.totalMin() > s.totalMax() || s.distancia() < 0) {
                errores.put("escalones[" + i + "]",
                        "Cada escalón necesita totalMin <= totalMax y una distancia no negativa");
            }
        }
        if (!errores.containsKey("escalones")) {
            List<EscalonDTO> ordenados = escalones.stream().filter(s -> s.totalMin() != null && s.totalMax() != null)
                    .sorted(Comparator.comparingDouble(EscalonDTO::totalMin)).toList();
            for (int i = 1; i < ordenados.size(); i++) {
                if (ordenados.get(i).totalMin() <= ordenados.get(i - 1).totalMax()) {
                    errores.put("escalones", "Los escalones se solapan");
                }
            }
        }
        if (dto.esquema() == EsquemaInterpretacion.RELATIVO_ESCALONADO && escalones.isEmpty()) {
            errores.put("escalones", "El esquema escalonado necesita la tabla de escalones");
        }
        return errores;
    }

    /**
     * Las bandas de un mismo estilo y escala no se solapan ni repiten orden. En
     * POMP, que es continua, dos bandas pueden compartir el límite (33,3 a 66,7
     * tras 0 a 33,3): sin eso quedaría un hueco sin nivel, y en el límite gana la
     * primera en orden. En BRUTO los baremos son enteros y van separados.
     */
    private static void validarSolapes(List<BandaDTO> bandas, Map<String, String> errores) {
        Map<String, List<BandaDTO>> grupos = new LinkedHashMap<>();
        bandas.forEach(b -> grupos.computeIfAbsent(b.estilo() + " (" + b.escala() + ")", k -> new ArrayList<>())
                .add(b));
        grupos.forEach((grupo, lista) -> {
            Set<Integer> ordenes = new HashSet<>();
            if (!lista.stream().allMatch(b -> ordenes.add(b.orden()))) {
                errores.put("bandas", "Las bandas de " + grupo + " repiten el orden");
                return;
            }
            List<BandaDTO> ordenadas = lista.stream().sorted(Comparator.comparingDouble(BandaDTO::limiteInferior))
                    .toList();
            for (int i = 1; i < ordenadas.size(); i++) {
                double inferior = ordenadas.get(i).limiteInferior();
                double anterior = ordenadas.get(i - 1).limiteSuperior();
                boolean seSolapa = ordenadas.get(i).escala() == EscalaBanda.POMP ? inferior < anterior
                        : inferior <= anterior;
                if (seSolapa) {
                    errores.put("bandas", "Las bandas de " + grupo + " se solapan");
                    return;
                }
            }
        });
    }
}
