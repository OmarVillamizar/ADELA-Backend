package com.adela.ia;

/**
 * Un JSON Patch que no se puede aplicar: operación desconocida o mal formada, ruta
 * inexistente, índice fuera de rango. El orquestador la trata igual que un patch vacío:
 * regenera el JSON completo. El motivo es corto y en español, y nunca copia valores del
 * patch, que vienen del modelo.
 */
public class PatchInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PatchInvalidoException(String motivo) {
        super(motivo);
    }
}
