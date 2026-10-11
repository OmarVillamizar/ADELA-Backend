package com.adela.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;
import com.adela.ia.EstadoTrabajo.Estado;
import com.adela.ia.EstadoTrabajo.Fase;

/**
 * Las reglas de los trabajos en memoria: dueño, cuota por día, un trabajo en curso por
 * persona, cola llena, expiración y cancelación. Reloj mutable y ejecutores de prueba.
 */
class TrabajosIaTest {

    private static final String ANA = "ana@ejemplo.test";
    private static final String BEA = "bea@ejemplo.test";

    /** Un reloj que la prueba mueve a mano. */
    private static final class RelojMutable extends Clock {
        private Instant ahora;

        RelojMutable(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            RelojMutable padre = this;
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return zona;
                }

                @Override
                public Clock withZone(ZoneId otra) {
                    return padre.withZone(otra);
                }

                @Override
                public Instant instant() {
                    return padre.instant();
                }
            };
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    // 12:00 en Bogotá (UTC-5).
    private final RelojMutable reloj = new RelojMutable(Instant.parse("2026-10-11T17:00:00Z"));
    private final Queue<Runnable> pendientes = new ArrayDeque<>();

    private TrabajosIa sincronico(int cuota) {
        return new TrabajosIa(cuota, Runnable::run, reloj);
    }

    private static final Consumer<Trabajo> TERMINA = t -> t.listo(null, null);
    private static final Consumer<Trabajo> SE_QUEDA_EN_CURSO = t -> {
    };

    private static AppException falla(Runnable accion) {
        return assertThrows(AppException.class, accion::run);
    }

    // ---------------------------------------------------------------- Dueño

    @Test
    @DisplayName("Un trabajo ajeno no existe: consultar, cancelar y refinar dan 404, igual que uno inexistente")
    void otroDuenoEs404() {
        TrabajosIa trabajos = sincronico(30);
        Trabajo t = trabajos.iniciar(ANA, TERMINA);

        assertEquals(t.id(), trabajos.obtener(ANA, t.id()).id());
        for (Runnable accion : List.<Runnable>of(() -> trabajos.obtener(BEA, t.id()),
                () -> trabajos.cancelar(BEA, t.id()), () -> trabajos.refinar(BEA, t.id(), SE_QUEDA_EN_CURSO),
                () -> trabajos.obtener(ANA, java.util.UUID.randomUUID()))) {
            AppException e = falla(accion);
            assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO, e.getCode());
            assertEquals("No se encontró el trabajo.", e.getMessage());
        }
        // Y el intento ajeno no lo tocó.
        assertEquals(Estado.LISTO, trabajos.obtener(ANA, t.id()).estado().estado());
    }

    // ---------------------------------------------------------------- Cuota

    @Test
    @DisplayName("La cuota diaria se agota con 429 y se repone al cambiar el día en Colombia, no en UTC")
    void cuotaDiaria() {
        TrabajosIa trabajos = sincronico(2);
        trabajos.iniciar(ANA, TERMINA);
        assertEquals(1, trabajos.cuotaRestante(ANA));
        trabajos.iniciar(ANA, TERMINA);
        assertEquals(0, trabajos.cuotaRestante(ANA));

        AppException e = falla(() -> trabajos.iniciar(ANA, TERMINA));
        assertEquals(ErrorCode.DEMASIADAS_SOLICITUDES, e.getCode());
        assertTrue(e.getMessage().contains("hoy"));
        // Otra persona tiene la suya.
        assertEquals(2, trabajos.cuotaRestante(BEA));

        // 12:00 Bogotá + 11 h = 23:00 del mismo día (ya es el día siguiente en UTC): sigue agotada.
        reloj.avanzar(Duration.ofHours(11));
        assertEquals(0, trabajos.cuotaRestante(ANA));
        // Pasada la medianoche de Bogotá, se repone.
        reloj.avanzar(Duration.ofHours(1));
        assertEquals(2, trabajos.cuotaRestante(ANA));
        trabajos.iniciar(ANA, TERMINA);
    }

    @Test
    @DisplayName("El refinamiento no cuenta para la cuota")
    void refinarNoCobra() {
        TrabajosIa trabajos = sincronico(1);
        Trabajo t = trabajos.iniciar(ANA, TERMINA);

        trabajos.refinar(ANA, t.id(), TERMINA);

        assertEquals(0, trabajos.cuotaRestante(ANA));
        assertTrue(t.estado().refinado());
    }

    @Test
    @DisplayName("Un trabajo rechazado por cola llena no se registra ni gasta cuota")
    void colaLlenaNoCobra() {
        TrabajosIa trabajos = new TrabajosIa(5, r -> {
            throw new TaskRejectedException("cola llena");
        }, reloj);

        AppException e = falla(() -> trabajos.iniciar(ANA, SE_QUEDA_EN_CURSO));

        assertEquals(ErrorCode.DEMASIADAS_SOLICITUDES, e.getCode());
        assertTrue(e.getMessage().contains("otras personas"));
        assertEquals(5, trabajos.cuotaRestante(ANA));
        // Como no quedó registrado, el segundo intento también falla por la cola, no por "ya tienes uno en curso".
        TrabajosIa otro = new TrabajosIa(5, r -> {
            throw new RejectedExecutionException();
        }, reloj);
        falla(() -> otro.iniciar(ANA, SE_QUEDA_EN_CURSO));
        assertTrue(falla(() -> otro.iniciar(ANA, SE_QUEDA_EN_CURSO)).getMessage().contains("otras personas"));
    }

    // ---------------------------------------------------------------- Un trabajo en curso

    @Test
    @DisplayName("Una persona solo puede tener un trabajo en curso; otra sí, y al terminar puede crear otro")
    void unTrabajoEnCurso() {
        TrabajosIa trabajos = sincronico(30);
        Trabajo primero = trabajos.iniciar(ANA, SE_QUEDA_EN_CURSO);

        AppException e = falla(() -> trabajos.iniciar(ANA, TERMINA));
        assertEquals(ErrorCode.DEMASIADAS_SOLICITUDES, e.getCode());
        assertTrue(e.getMessage().contains("trabajo en curso"));
        assertEquals(29, trabajos.cuotaRestante(ANA));

        trabajos.iniciar(BEA, TERMINA);

        primero.fallido(ErrorCode.IA_NO_DISPONIBLE, "caído");
        trabajos.iniciar(ANA, TERMINA);
    }

    @Test
    @DisplayName("Cancelar libera el cupo del trabajo en curso")
    void cancelarLibera() {
        TrabajosIa trabajos = sincronico(30);
        Trabajo t = trabajos.iniciar(ANA, SE_QUEDA_EN_CURSO);

        trabajos.cancelar(ANA, t.id());

        trabajos.iniciar(ANA, TERMINA);
    }

    @Test
    @DisplayName("Refinar con otro trabajo en curso da 429 y deja el trabajo como estaba")
    void refinarConOtroEnCurso() {
        TrabajosIa trabajos = sincronico(30);
        Trabajo listo = trabajos.iniciar(ANA, TERMINA);
        trabajos.iniciar(ANA, SE_QUEDA_EN_CURSO);

        AppException e = falla(() -> trabajos.refinar(ANA, listo.id(), TERMINA));

        assertEquals(ErrorCode.DEMASIADAS_SOLICITUDES, e.getCode());
        assertEquals(Estado.LISTO, listo.estado().estado());
        assertFalse(listo.estado().refinado());
    }

    // ---------------------------------------------------------------- Pool real

    @Test
    @DisplayName("Con el pool real (2 hilos, máx. 4, cola 10) se aceptan 14 trabajos y el 15.º recibe 429")
    void colaLlenaConPoolReal() throws Exception {
        TrabajosIa trabajos = new TrabajosIa(100);
        CountDownLatch suelta = new CountDownLatch(1);
        List<String> hilos = new CopyOnWriteArrayList<>();
        try {
            List<Trabajo> aceptados = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                aceptados.add(trabajos.iniciar("persona" + i + "@ejemplo.test", t -> {
                    hilos.add(Thread.currentThread().getName());
                    try {
                        suelta.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            AppException e = falla(() -> trabajos.iniciar("persona15@ejemplo.test", SE_QUEDA_EN_CURSO));

            assertEquals(ErrorCode.DEMASIADAS_SOLICITUDES, e.getCode());
            assertEquals(100, trabajos.cuotaRestante("persona15@ejemplo.test"));
            assertEquals(14, aceptados.size());
        } finally {
            suelta.countDown();
            trabajos.cerrar();
        }
        assertTrue(hilos.stream().allMatch(h -> h.startsWith("nelse-")), hilos.toString());
    }

    // ---------------------------------------------------------------- Expiración

    @Test
    @DisplayName("Un trabajo expira a los 30 min de su último cambio, no de su creación")
    void expira() {
        TrabajosIa trabajos = sincronico(30);
        Trabajo t = trabajos.iniciar(ANA, SE_QUEDA_EN_CURSO);

        reloj.avanzar(Duration.ofMinutes(20));
        t.fase(Fase.GENERANDO, 10);
        reloj.avanzar(Duration.ofMinutes(20));
        // 40 min desde que se creó, 20 desde el último cambio.
        assertEquals(t.id(), trabajos.obtener(ANA, t.id()).id());

        reloj.avanzar(Duration.ofMinutes(11));
        AppException e = falla(() -> trabajos.obtener(ANA, t.id()));
        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO, e.getCode());
    }

    @Test
    @DisplayName("Al crear otro trabajo también se purgan los vencidos, y se cancela el que seguía corriendo")
    void purgaAlCrear() {
        TrabajosIa trabajos = sincronico(30);
        Trabajo viejo = trabajos.iniciar(ANA, SE_QUEDA_EN_CURSO);

        reloj.avanzar(Duration.ofMinutes(31));
        // El vencido no cuenta como "en curso": se purga antes de comprobarlo.
        trabajos.iniciar(ANA, TERMINA);

        assertTrue(viejo.cancelado());
        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO, falla(() -> trabajos.obtener(ANA, viejo.id())).getCode());
    }

    // ---------------------------------------------------------------- Cancelación

    @Test
    @DisplayName("Cancelar marca el trabajo, evita que arranque si seguía en la cola y lo olvida (la consulta da 404)")
    void cancelar() {
        TrabajosIa trabajos = new TrabajosIa(30, pendientes::add, reloj);
        boolean[] corrio = { false };
        Trabajo t = trabajos.iniciar(ANA, x -> corrio[0] = true);

        trabajos.cancelar(ANA, t.id());
        pendientes.poll().run();

        assertTrue(t.cancelado());
        assertFalse(corrio[0]);
        assertEquals(ErrorCode.RECURSO_NO_ENCONTRADO, falla(() -> trabajos.obtener(ANA, t.id())).getCode());
    }

    @Test
    @DisplayName("Un trabajo cancelado ignora los cambios posteriores del trabajador")
    void canceladoNoSeRevive() {
        TrabajosIa trabajos = sincronico(30);
        Trabajo t = trabajos.iniciar(ANA, SE_QUEDA_EN_CURSO);
        int version = t.estado().version();

        trabajos.cancelar(ANA, t.id());
        t.fase(Fase.VALIDANDO, 75);
        t.progreso(90);
        t.listo(null, null);

        assertEquals(version, t.estado().version());
        assertEquals(Estado.EN_CURSO, t.estado().estado());
    }
}
