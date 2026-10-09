-- La pregunta de preferencia multimodal pasa a ser opcional por cuestionario
-- (apagada por defecto) y se extiende a las resoluciones de cápsulas.
ALTER TABLE cuestionario ADD COLUMN pregunta_preferencia BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE respuesta_capsula ADD COLUMN preferencia_multimodal VARCHAR(15);
