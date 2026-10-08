package com.adela.calificacion;

/**
 * Regla con la que se eligen los estilos dominantes y el perfil. Las bandas de
 * nivel no dependen del esquema: se aplican siempre que el estilo tenga bandas.
 *
 * BAREMO no marca dominantes (CHAEA, ACRA); RELATIVO toma los que quedan a
 * menos de delta del mayor POMP (Herrmann, PNL); RELATIVO_ESCALONADO aplica la
 * distancia de paso de Fleming sobre el puntaje directo (VARK); CUADRANTES
 * cruza dos ejes y asigna la esquina donde cae el estudiante (Kolb).
 */
public enum EsquemaInterpretacion {
    NINGUNA, BAREMO, RELATIVO, RELATIVO_ESCALONADO, CUADRANTES
}
