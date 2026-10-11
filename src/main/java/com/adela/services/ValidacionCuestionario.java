package com.adela.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.adela.calificacion.EsquemaInterpretacion;
import com.adela.calificacion.TipoEstilo;
import com.adela.dto.CuestionarioDTO;
import com.adela.dto.EstiloDTO;
import com.adela.dto.InterpretacionDTO;
import com.adela.dto.InterpretacionDTO.BandaDTO;
import com.adela.dto.InterpretacionDTO.EscalonDTO;
import com.adela.entities.Estilo;

/**
 * Fachada pública de los validadores de creación, para quien necesita saber si
 * un cuestionario se aceptaría sin guardarlo (la importación con IA).
 */
public final class ValidacionCuestionario {

    private ValidacionCuestionario() {
    }

    /**
     * Valida sin guardar: los mismos validadores que el POST. Los errores
     * llevan las claves de siempre (estilos[0], bandas[2], plano.ejeX, esquema).
     *
     * lecturaAsistente es la convención del asistente que el frontend usará para
     * rellenar los números después. Si llega, el esquema se valida sin exigir lo
     * que la convención va a llenar: DOMINANCIA (NIVEL_SUPERIOR pide bandas) se
     * valida como BAREMO, y ESCALONADO (RELATIVO_ESCALONADO pide escalones) como
     * RELATIVO. Cualquier otro valor deja el esquema tal cual.
     */
    public static Map<String, String> validarSinGuardar(CuestionarioDTO dto, String lecturaAsistente) {
        Map<String, String> errores = CuestionarioService.validarEstructura(dto);
        InterpretacionDTO interpretacion = dto.getInterpretacion();
        if (interpretacion == null) {
            return errores;
        }

        // Estilos transitorios: la validación solo lee el nombre y el tipo.
        Map<String, Estilo> estilos = new HashMap<>();
        if (dto.getEstilos() != null) {
            for (EstiloDTO e : dto.getEstilos()) {
                Estilo transitorio = new Estilo();
                transitorio.setNombre(e.getNombre());
                transitorio.setTipo(e.getTipo() != null ? e.getTipo() : TipoEstilo.PRIMARIO);
                estilos.putIfAbsent(e.getNombre(), transitorio);
            }
        }
        List<BandaDTO> bandas = interpretacion.bandas() == null ? List.of() : interpretacion.bandas();
        List<EscalonDTO> escalones = interpretacion.escalones() == null ? List.of() : interpretacion.escalones();

        InterpretacionDTO aValidar = neutralizar(interpretacion, lecturaAsistente);
        InterpretacionService.validar(aValidar, bandas, escalones, estilos).forEach(errores::putIfAbsent);
        return errores;
    }

    private static InterpretacionDTO neutralizar(InterpretacionDTO i, String lecturaAsistente) {
        EsquemaInterpretacion esquema = i.esquema();
        if ("DOMINANCIA".equals(lecturaAsistente) && esquema == EsquemaInterpretacion.NIVEL_SUPERIOR) {
            esquema = EsquemaInterpretacion.BAREMO;
        } else if ("ESCALONADO".equals(lecturaAsistente) && esquema == EsquemaInterpretacion.RELATIVO_ESCALONADO) {
            esquema = EsquemaInterpretacion.RELATIVO;
        } else {
            return i;
        }
        return new InterpretacionDTO(esquema, i.delta(), i.esIpsativo(), i.bandas(), i.escalones(), i.plano(),
                i.estilos(), i.complementaria());
    }
}
