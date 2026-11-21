-- Alinea cuatro FK con la convención <tabla_referida>_<columna_referida>
-- que sigue el resto del esquema (p. ej. capsula.profesor_email).
--   matricula.curso_id               -> grupo_id        (no existe tabla curso)
--   matricula.estudiante_id          -> estudiante_email (guarda un correo, no un id)
--   grupo.profesor_id                -> profesor_email   (igual que cuestionario y capsula)
--   resultado_pregunta.cuestionario_id -> resultado_cuestionario_id
--       (apunta al resultado, no al cuestionario: un JOIN fiado del nombre
--        daba datos equivocados sin error, ambos son bigint)
-- RENAME COLUMN conserva datos, PK, índices y FK; solo cambia el nombre.

ALTER TABLE matricula          RENAME COLUMN curso_id        TO grupo_id;
ALTER TABLE matricula          RENAME COLUMN estudiante_id   TO estudiante_email;
ALTER TABLE grupo              RENAME COLUMN profesor_id     TO profesor_email;
ALTER TABLE resultado_pregunta RENAME COLUMN cuestionario_id TO resultado_cuestionario_id;

-- Las FK de esas columnas tienen nombres hash de Hibernate (fk16g9x09...).
-- Se renombran a nombres legibles buscándolas por columna, no por nombre,
-- para no depender del hash que haya generado cada base.
DO $$
DECLARE
    r record;
    fk text;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('matricula',          'grupo_id',                  'matricula_grupo_fk'),
        ('matricula',          'estudiante_email',          'matricula_estudiante_fk'),
        ('grupo',              'profesor_email',            'grupo_profesor_fk'),
        ('resultado_pregunta', 'resultado_cuestionario_id', 'resultado_pregunta_resultado_fk')
    ) AS v(tabla, columna, nuevo)
    LOOP
        SELECT c.conname INTO fk
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
        WHERE c.contype = 'f'
          AND c.conrelid = r.tabla::regclass
          AND a.attname = r.columna
          AND array_length(c.conkey, 1) = 1;

        IF fk IS NULL THEN
            RAISE EXCEPTION 'Sin FK en %.%', r.tabla, r.columna;
        END IF;
        EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I', r.tabla, fk, r.nuevo);
    END LOOP;
END $$;
