package com.adela.services;

import java.util.Locale;
import java.util.Map;

import com.adela.exceptions.AppException;
import com.adela.exceptions.ErrorCode;

/**
 * El código es el identificador de la UFPS y solo tiene sentido para sus
 * cuentas. Antes se exigía a todos: los usuarios externos inventaban uno para
 * poder seguir y chocaban con CODIGO_DUPLICADO.
 *
 * Se decide por el correo de Google y no por lo que diga el cliente: Google lo
 * entrega verificado y no deja crear cuentas personales sobre un dominio de
 * Workspace, así que un correo @ufps.edu.co lo emitió la universidad.
 */
public final class CodigoInstitucional {

    private static final String DOMINIO = "@ufps.edu.co";

    private CodigoInstitucional() {
    }

    public static boolean requiere(String email) {
        return email != null && email.toLowerCase(Locale.ROOT).endsWith(DOMINIO);
    }

    /**
     * Devuelve el código a guardar. Fuera de la UFPS siempre es null, aunque el
     * cliente mande algo: null y no "", porque la columna es unique y Postgres
     * admite varios NULL pero no varias cadenas vacías.
     */
    public static String aGuardar(String email, String codigo) {
        if (!requiere(email)) {
            return null;
        }
        if (codigo == null || codigo.isBlank()) {
            throw new AppException(ErrorCode.VALIDACION,
                    "El código es obligatorio para las cuentas " + DOMINIO + ".",
                    Map.of("codigo", "Es obligatorio"));
        }
        return codigo.trim();
    }
}
