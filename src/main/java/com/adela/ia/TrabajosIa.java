package com.adela.ia;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.ia.EstadoTrabajo.Estado;

import jakarta.annotation.PreDestroy;

/**
 * Los trabajos de Nelse, en memoria y con su propio pool: la generación tarda minutos y no
 * debe ocupar los hilos que atienden las peticiones ni compartir cola con otras tareas.
 * Nada se persiste; si el servidor se reinicia, los trabajos se pierden (decisión 2 del plan).
 *
 * Las reglas que protegen al proveedor y al bolsillo viven aquí, no en el controlador:
 * - cada trabajo es solo de quien lo creó; para cualquier otra persona no existe (404);
 * - una persona tiene a lo sumo un trabajo en curso, para que un solo administrador no llene
 *   el pool;
 * - cuota diaria de trabajos creados por persona (el ajuste de un resultado no cuenta), con el
 *   día calendario de Colombia;
 * - cola llena: 429, en vez de aceptar trabajos que nadie atenderá;
 * - un trabajo expira a los 30 min de su último cambio. Se purga al crear y al consultar, sin
 *   tareas programadas.
 */
@Component
public class TrabajosIa {

    static final Duration TTL = Duration.ofMinutes(30);
    static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private record Cuota(LocalDate dia, int usados) {
    }

    private final Map<UUID, Trabajo> trabajos = new ConcurrentHashMap<>();
    /** Solo se toca con el candado de la instancia. */
    private final Map<String, Cuota> cuotas = new HashMap<>();
    private final int cuotaDiaria;
    private final Executor ejecutor;
    private final Clock reloj;
    /** El pool propio, para cerrarlo con el contexto; null si el ejecutor lo puso quien construyó. */
    private final ThreadPoolTaskExecutor pool;

    @Autowired
    public TrabajosIa(@Value("${adela.ia.cuota-diaria}") int cuotaDiaria) {
        this.cuotaDiaria = cuotaDiaria;
        this.reloj = Clock.system(ZONA);
        // 2 hilos fijos; los 2 extra solo si la cola de 10 se llena. Pasado eso, 429.
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(10);
        pool.setThreadNamePrefix("nelse-");
        pool.initialize();
        this.pool = pool;
        this.ejecutor = pool;
    }

    /**
     * Con el ejecutor y el reloj inyectados: las pruebas pasan uno síncrono (o que rechace) y un
     * reloj que mueven a mano. Quien lo use es dueño del ejecutor; {@link #cerrar} no lo toca.
     */
    public TrabajosIa(int cuotaDiaria, Executor ejecutor, Clock reloj) {
        this.cuotaDiaria = cuotaDiaria;
        this.ejecutor = ejecutor;
        this.reloj = reloj;
        this.pool = null;
    }

    /** Al cerrar el contexto no queda ningún hilo de Nelse vivo ni llamada pendiente al proveedor. */
    @PreDestroy
    void cerrar() {
        if (pool != null) {
            pool.shutdown();
        }
    }

    /**
     * Crea un trabajo para esa persona y lo manda a correr. 429 si agotó la cuota, si ya tiene
     * uno en curso o si no hay cupo en la cola; en ese caso no se registra ni se cobra cuota.
     */
    public synchronized Trabajo iniciar(String email, Consumer<Trabajo> tarea) {
        purgar();
        if (cuotaRestante(email) <= 0) {
            throw new AppException(ErrorCode.DEMASIADAS_SOLICITUDES,
                    "Agotaste los trabajos de Nelse de hoy. Vuelve a intentarlo mañana.");
        }
        verificarSinTrabajoEnCurso(email, null);

        Trabajo trabajo = new Trabajo(email, reloj);
        trabajos.put(trabajo.id(), trabajo);
        try {
            lanzar(trabajo, tarea);
        } catch (AppException e) {
            trabajos.remove(trabajo.id());
            throw e;
        }
        cuotas.put(email, new Cuota(hoy(), usadosHoy(email) + 1));
        return trabajo;
    }

    /**
     * Corre el único refinamiento de un trabajo LISTO. No cuenta para la cuota, pero sí ocupa
     * el lugar del trabajo en curso. 409 si no está listo o ya se refinó.
     */
    public synchronized Trabajo refinar(String email, UUID id, Consumer<Trabajo> tarea) {
        Trabajo trabajo = obtener(email, id);
        EstadoTrabajo anterior = trabajo.comenzarRefinamiento();
        try {
            verificarSinTrabajoEnCurso(email, trabajo);
            lanzar(trabajo, tarea);
        } catch (AppException e) {
            trabajo.restaurar(anterior);
            throw e;
        }
        return trabajo;
    }

    /** El trabajo de esa persona; 404 si no existe, expiró o es de otra (no se revela cuál). */
    public Trabajo obtener(String email, UUID id) {
        purgar();
        Trabajo trabajo = id == null ? null : trabajos.get(id);
        if (trabajo == null || !trabajo.email().equals(email)) {
            throw new AppException(ErrorCode.RECURSO_NO_ENCONTRADO, "No se encontró el trabajo.");
        }
        return trabajo;
    }

    /** Cancela, descarta y olvida el trabajo: la consulta siguiente da 404. */
    public void cancelar(String email, UUID id) {
        Trabajo trabajo = obtener(email, id);
        trabajo.cancelar();
        trabajos.remove(trabajo.id());
    }

    public synchronized int cuotaRestante(String email) {
        return Math.max(0, cuotaDiaria - usadosHoy(email));
    }

    private void lanzar(Trabajo trabajo, Consumer<Trabajo> tarea) {
        FutureTask<Void> futuro = new FutureTask<>(() -> tarea.accept(trabajo), null);
        // Antes de ejecutar: cancelar debe poder interrumpirla desde el primer instante.
        trabajo.asignarTarea(futuro);
        try {
            ejecutor.execute(futuro);
        } catch (RejectedExecutionException e) {
            // TaskRejectedException de Spring es una RejectedExecutionException.
            throw new AppException(ErrorCode.DEMASIADAS_SOLICITUDES,
                    "Nelse está atendiendo a otras personas ahora mismo. Inténtalo de nuevo en unos minutos.");
        }
    }

    private void verificarSinTrabajoEnCurso(String email, Trabajo excepto) {
        boolean enCurso = trabajos.values().stream().anyMatch(t -> t != excepto && t.email().equals(email)
                && t.estado().estado() == Estado.EN_CURSO);
        if (enCurso) {
            throw new AppException(ErrorCode.DEMASIADAS_SOLICITUDES,
                    "Ya tienes un trabajo en curso. Espera a que termine o cancélalo.");
        }
    }

    private void purgar() {
        var limite = reloj.instant().minus(TTL);
        trabajos.values().removeIf(t -> {
            if (t.actualizado().isBefore(limite)) {
                t.cancelar();
                return true;
            }
            return false;
        });
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj.withZone(ZONA));
    }

    private int usadosHoy(String email) {
        Cuota cuota = cuotas.get(email);
        return cuota != null && cuota.dia().equals(hoy()) ? cuota.usados() : 0;
    }
}
