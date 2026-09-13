-- BUG-07: genero se guardaba como ordinal (smallint). Reordenar el enum Genero
-- reasignaba en silencio el genero de cada registro ya guardado.

-- Hibernate genera un CHECK sobre el rango del ordinal al mapear el enum asi.
-- Cambiar el tipo de la columna sin retirarlo antes falla con
-- "operator does not exist: character varying >= integer".
ALTER TABLE estudiante DROP CONSTRAINT IF EXISTS estudiante_genero_check;

-- La traduccion usa el orden actual de Genero: MASCULINO, FEMENINO, NO_DECIR.
ALTER TABLE estudiante
    ALTER COLUMN genero TYPE varchar(20)
    USING CASE genero
        WHEN 0 THEN 'MASCULINO'
        WHEN 1 THEN 'FEMENINO'
        WHEN 2 THEN 'NO_DECIR'
        ELSE NULL
    END;

-- Se restituye el CHECK, ahora sobre los nombres. Con ddl-auto: validate Hibernate
-- ya no lo crearia por su cuenta.
ALTER TABLE estudiante
    ADD CONSTRAINT estudiante_genero_check
    CHECK (genero IN ('MASCULINO', 'FEMENINO', 'NO_DECIR'));

-- BUG-10: la comprobacion en codigo evita crear asignaciones duplicadas, pero sin
-- esta restriccion dos peticiones simultaneas todavia podian colarlas.
-- NULLS NOT DISTINCT (Postgres 15+) cubre tambien la asignacion directa, que deja
-- grupo_id nulo.
ALTER TABLE resultado_cuestionario
    ADD CONSTRAINT uk_resultado_cuestionario_asignacion
    UNIQUE NULLS NOT DISTINCT (cuestionario_id, estudiante_email, grupo_id);
