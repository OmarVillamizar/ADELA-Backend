package com.adela.services;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Códigos públicos de cápsulas y resultados. Son lo único que identifica un
 * recurso en las rutas sin autenticación, así que salen de SecureRandom y no de
 * un contador: no se pueden adivinar ni recorrer.
 *
 * El alfabeto omite 0/O, 1/I/L para que el código se pueda dictar o copiar de
 * una pantalla proyectada sin ambigüedad.
 */
public final class CodigoAleatorio {

    static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CodigoAleatorio() {
    }

    public static String generar(int longitud) {
        StringBuilder sb = new StringBuilder(longitud);
        for (int i = 0; i < longitud; i++) {
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /**
     * Acepta el código como lo escriba una persona: minúsculas, guiones o
     * espacios ("k7qm-2xpa" equivale a "K7QM2XPA").
     */
    public static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
