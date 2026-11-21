-- Completa V19: respuesta_capsula_opcion.respuesta_id apunta a respuesta_capsula.id,
-- así que por la convención <tabla_referida>_<columna_referida> es respuesta_capsula_id.
-- RENAME COLUMN conserva datos, PK (respuesta_capsula_id, opcion_id) y FK.

ALTER TABLE respuesta_capsula_opcion RENAME COLUMN respuesta_id TO respuesta_capsula_id;

-- La FK se creó inline en V5 con nombre automático; se busca por columna
-- (igual que en V19) y se le da un nombre legible.
DO $$
DECLARE
    fk text;
BEGIN
    SELECT c.conname INTO fk
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
    WHERE c.contype = 'f'
      AND c.conrelid = 'respuesta_capsula_opcion'::regclass
      AND a.attname = 'respuesta_capsula_id'
      AND array_length(c.conkey, 1) = 1;

    IF fk IS NULL THEN
        RAISE EXCEPTION 'Sin FK en respuesta_capsula_opcion.respuesta_capsula_id';
    END IF;
    EXECUTE format('ALTER TABLE respuesta_capsula_opcion RENAME CONSTRAINT %I TO %I',
                   fk, 'respuesta_capsula_opcion_respuesta_fk');
END $$;
