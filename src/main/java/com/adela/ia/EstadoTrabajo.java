package com.adela.ia;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.adela.dto.CuestionarioDTO;

/**
 * Lo que devuelve {@code GET /api/ia/cuestionarios/trabajos/{id}}: una foto inmutable del
 * trabajo. El orquestador publica una foto nueva en cada cambio (el campo {@code volatile}
 * de {@link Trabajo}), así el hilo que atiende la consulta nunca ve un estado a medias.
 *
 * El frontend consulta cada 2 s: {@code version} sube con cada cambio, de modo que si no
 * cambió puede no volver a pintar. Nunca lleva el texto del documento, las instrucciones ni
 * la salida cruda del modelo.
 *
 * @param estado               EN_CURSO mientras trabaja; LISTO o FALLIDO al terminar
 * @param fase                 qué hace ahora; null cuando ya no está en curso
 * @param progreso             0 a 100, aproximado y que no baja dentro de una misma corrida
 *                             (un refinamiento empieza otra corrida y vuelve a 0)
 * @param refinado             ya se usó el único refinamiento del trabajo (o está en curso)
 * @param resultado            solo en LISTO
 * @param error                solo en FALLIDO
 * @param aviso                solo en LISTO: el refinamiento no se pudo aplicar y se conserva el
 *                             resultado anterior
 * @param preguntasAlProfesor las de {@code resultado.analisis}, a mano para el paso de ajuste;
 *                             solo en LISTO y solo si el modelo dejó alguna
 */
public record EstadoTrabajo(UUID id, Estado estado, Fase fase, int progreso, int version, boolean refinado,
        ResultadoNelse resultado, ErrorTrabajo error, String aviso, List<String> preguntasAlProfesor) {

    public enum Estado {
        EN_CURSO, LISTO, FALLIDO
    }

    public enum Fase {
        LEYENDO, GENERANDO, VALIDANDO, CORRIGIENDO, REFINANDO
    }

    /** Lo mismo que la validación de la importación, más lo que costó producirlo. */
    public record ResultadoNelse(CuestionarioDTO cuestionario, AnalisisIa analisis, List<Marca> marcas,
            Map<String, String> erroresDuros, Metricas metricas) {
    }

    /**
     * De la corrida que produjo el resultado (generación o refinamiento). tokens = entrada +
     * salida; usd con los precios de deepseek-flash; rondas = correcciones por patch.
     */
    public record Metricas(double segundos, int tokens, double usd, int rondas, String versionPrompt) {
    }

    /** codigo es el nombre de un ErrorCode (IA_NO_DISPONIBLE, DOCUMENTO_INVALIDO...). */
    public record ErrorTrabajo(String codigo, String mensaje) {
    }
}
