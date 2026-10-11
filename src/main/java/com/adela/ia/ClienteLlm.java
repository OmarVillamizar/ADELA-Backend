package com.adela.ia;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import com.adela.exceptions.AppException;

/**
 * Una llamada al modelo de lenguaje y nada más: no sabe de cuestionarios, de
 * prompts ni de reintentos. Es una interfaz para que el orquestador de Nelse
 * se pruebe con un Mockito en lugar de un proveedor real.
 *
 * Los errores salen como {@link AppException}: IA_DESHABILITADA si no hay
 * llave o la IA está apagada, IA_NO_DISPONIBLE si el proveedor no responde
 * (red, tiempo, HTTP distinto de 2xx) e IA_RESPUESTA_INVALIDA si responde pero
 * sin contenido utilizable.
 */
public interface ClienteLlm {

    /**
     * Envía la petición y bloquea hasta que el modelo termina. progreso puede ser
     * null; se invoca mientras llegan fragmentos, con la frecuencia acotada.
     */
    RespuestaLlm completar(PeticionLlm peticion, Consumer<ProgresoLlm> progreso);

    /** esfuerzo: "low" o "high" (reasoning_effort). json pide que la salida sea un objeto JSON. */
    record PeticionLlm(List<MensajeLlm> mensajes, String esfuerzo, int maxTokens, boolean json) {
    }

    /** rol: "system", "user" o "assistant". Solo texto. */
    record MensajeLlm(String rol, String contenido) {
    }

    /**
     * finishReason llega tal cual lo dice el proveedor ("stop", "length"...): decidir qué
     * hacer con una salida truncada le toca a quien llama. duracion es de pared, con el
     * stream completo.
     */
    record RespuestaLlm(String contenido, String finishReason, UsoLlm uso, Duration duracion) {
    }

    /**
     * Tokens de la llamada. promptCacheHit y promptCacheMiss solo los informa DeepSeek;
     * lo que el proveedor no diga queda en 0.
     */
    record UsoLlm(int promptTokens, int completionTokens, int promptCacheHit, int promptCacheMiss,
            int reasoningTokens) {
    }

    /** Caracteres acumulados hasta ahora; el razonamiento se cuenta pero no se guarda. */
    record ProgresoLlm(int caracteresContenido, int caracteresRazonamiento) {
    }
}
