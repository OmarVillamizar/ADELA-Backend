package com.adela.ia;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.ia.ClienteLlm.UsoLlm;
import com.adela.ia.EstadoTrabajo.ErrorTrabajo;
import com.adela.ia.EstadoTrabajo.Estado;
import com.adela.ia.EstadoTrabajo.Fase;
import com.adela.ia.EstadoTrabajo.Metricas;
import com.adela.ia.EstadoTrabajo.ResultadoNelse;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Un trabajo de Nelse en memoria: dueño, estado visible y lo que el orquestador necesita
 * para refinarlo. El estado es una foto inmutable ({@link EstadoTrabajo}) que se reemplaza
 * entera en cada cambio; el hilo que trabaja la publica y el que atiende el GET la lee sin
 * bloqueo.
 *
 * Los cambios de estado son {@code synchronized} y no hacen nada una vez cancelado: un
 * callback de progreso tardío no puede revivir un trabajo que la persona ya descartó.
 */
public final class Trabajo {

    /**
     * Lo que se conserva del resultado para el único refinamiento: la fuente con que se
     * validó, el mensaje de usuario original (para repetir el prefijo cacheado), el JSON
     * vigente y el resultado que se devuelve si el refinamiento falla.
     */
    record Contexto(FuenteNelse fuente, String mensajeUsuario, ObjectNode raiz, String formato,
            ResultadoNelse previo) {
    }

    /**
     * Cuentas de una corrida (generación o refinamiento). Solo la toca el hilo que la corre;
     * el siguiente hilo la ve por el {@code volatile} del estado que se publica en medio.
     */
    static final class Medicion {

        // Precios de deepseek-flash en USD por millón de tokens (los mismos de ia-eval/eval.py).
        private static final double USD_ENTRADA_SIN_CACHE = 0.15;
        private static final double USD_ENTRADA_CON_CACHE = 0.003;
        private static final double USD_SALIDA = 0.60;

        int promptTokens;
        int cacheHit;
        int cacheMiss;
        int completionTokens;
        int reasoningTokens;
        int rondas;
        int regeneraciones;
        private final long inicio = System.nanoTime();
        private long fin;

        void sumar(UsoLlm uso) {
            if (uso == null) {
                return;
            }
            promptTokens += uso.promptTokens();
            cacheHit += uso.promptCacheHit();
            // Si el proveedor no separa acierto y fallo de caché, toda la entrada cuenta como fallo.
            cacheMiss += uso.promptCacheHit() + uso.promptCacheMiss() > 0 ? uso.promptCacheMiss()
                    : uso.promptTokens();
            completionTokens += uso.completionTokens();
            reasoningTokens += uso.reasoningTokens();
        }

        void cerrar() {
            fin = System.nanoTime();
        }

        double segundos() {
            return Math.round(((fin == 0 ? System.nanoTime() : fin) - inicio) / 1e8) / 10.0;
        }

        double usd() {
            return (cacheMiss * USD_ENTRADA_SIN_CACHE + cacheHit * USD_ENTRADA_CON_CACHE
                    + completionTokens * USD_SALIDA) / 1e6;
        }

        Metricas metricas(String versionPrompt) {
            return new Metricas(segundos(), promptTokens + completionTokens, usd(), rondas, versionPrompt);
        }
    }

    private final UUID id = UUID.randomUUID();
    private final String email;
    private final Clock reloj;

    private volatile EstadoTrabajo estado;
    private volatile Instant actualizado;
    private volatile boolean cancelado;
    private volatile Future<?> tarea;
    private volatile Contexto contexto;
    private volatile Medicion medicion = new Medicion();
    private int version;

    Trabajo(String email, Clock reloj) {
        this.email = email;
        this.reloj = reloj;
        this.estado = new EstadoTrabajo(id, Estado.EN_CURSO, Fase.LEYENDO, 0, 0, false, null, null, null, null);
        this.actualizado = reloj.instant();
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public EstadoTrabajo estado() {
        return estado;
    }

    Instant actualizado() {
        return actualizado;
    }

    boolean cancelado() {
        return cancelado;
    }

    Contexto contexto() {
        return contexto;
    }

    void contexto(Contexto contexto) {
        this.contexto = contexto;
    }

    Medicion medicion() {
        return medicion;
    }

    /** Una corrida nueva (el refinamiento) mide por separado de la anterior. */
    Medicion nuevaMedicion() {
        medicion = new Medicion();
        return medicion;
    }

    void asignarTarea(Future<?> tarea) {
        this.tarea = tarea;
    }

    /**
     * Marca el trabajo como cancelado e interrumpe al hilo si ya está corriendo. El trabajador
     * revisa la marca entre pasos y descarta lo que haya producido.
     */
    synchronized void cancelar() {
        cancelado = true;
        Future<?> f = tarea;
        if (f != null) {
            f.cancel(true);
        }
    }

    // ---------------------------------------------------------------- Cambios de estado

    /** Cambia de fase; el progreso nunca baja, solo sube hasta {@code progresoMinimo}. */
    synchronized void fase(Fase fase, int progresoMinimo) {
        EstadoTrabajo e = estado;
        if (cancelado || e.estado() != Estado.EN_CURSO) {
            return;
        }
        publicar(Estado.EN_CURSO, fase, Math.max(e.progreso(), progresoMinimo), e.refinado(), null, null, null);
    }

    /** Avance dentro de la fase; no publica si no sube, para que {@code version} no cambie en vano. */
    synchronized void progreso(int progreso) {
        EstadoTrabajo e = estado;
        if (cancelado || e.estado() != Estado.EN_CURSO || progreso <= e.progreso()) {
            return;
        }
        publicar(Estado.EN_CURSO, e.fase(), Math.min(progreso, 99), e.refinado(), null, null, null);
    }

    synchronized void listo(ResultadoNelse resultado, String aviso) {
        if (!cancelado) {
            publicar(Estado.LISTO, null, 100, estado.refinado(), resultado, null, aviso);
        }
    }

    synchronized void fallido(ErrorCode codigo, String mensaje) {
        if (!cancelado) {
            publicar(Estado.FALLIDO, null, estado.progreso(), estado.refinado(), null,
                    new ErrorTrabajo(codigo.name(), mensaje), null);
        }
    }

    /**
     * Pasa de LISTO a refinando, una sola vez por trabajo. Devuelve la foto anterior para
     * poder restaurarla si después no hay cupo para correrlo. CONFLICTO_DATOS (409) si aún
     * no está listo o ya se refinó.
     */
    synchronized EstadoTrabajo comenzarRefinamiento() {
        EstadoTrabajo anterior = estado;
        if (anterior.estado() != Estado.LISTO) {
            throw new AppException(ErrorCode.CONFLICTO_DATOS, "El trabajo todavía no está listo para ajustarse.");
        }
        if (anterior.refinado()) {
            throw new AppException(ErrorCode.CONFLICTO_DATOS, "Este trabajo ya se ajustó una vez.");
        }
        publicar(Estado.EN_CURSO, Fase.REFINANDO, 0, true, null, null, null);
        return anterior;
    }

    synchronized void restaurar(EstadoTrabajo anterior) {
        estado = new EstadoTrabajo(id, anterior.estado(), anterior.fase(), anterior.progreso(), ++version,
                anterior.refinado(), anterior.resultado(), anterior.error(), anterior.aviso(),
                anterior.preguntasAlProfesor());
        actualizado = reloj.instant();
    }

    private void publicar(Estado nuevo, Fase fase, int progreso, boolean refinado, ResultadoNelse resultado,
            ErrorTrabajo error, String aviso) {
        estado = new EstadoTrabajo(id, nuevo, fase, progreso, ++version, refinado, resultado, error, aviso,
                preguntas(resultado));
        actualizado = reloj.instant();
    }

    private static List<String> preguntas(ResultadoNelse resultado) {
        if (resultado == null || resultado.analisis() == null || resultado.analisis().preguntasAlProfesor() == null
                || resultado.analisis().preguntasAlProfesor().isEmpty()) {
            return null;
        }
        return resultado.analisis().preguntasAlProfesor();
    }
}
