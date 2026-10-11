package com.adela.ia;

import java.util.List;
import java.util.Map;

import com.adela.dto.CuestionarioDTO;

/**
 * Lo que devuelve la validación sin guardar. erroresDuros impiden crear (misma
 * forma que el 400 de POST /api/cuestionarios); las marcas no. Si el JSON ni
 * siquiera se pudo leer, cuestionario es null y el error va bajo la clave "json".
 */
public record ResultadoImportacion(CuestionarioDTO cuestionario, AnalisisIa analisis, List<Marca> marcas,
        Map<String, String> erroresDuros) {
}
