-- Preferencia autodeclarada (SELECTIVO o INTEGRATIVO) de quien obtuvo un
-- perfil con todas las modalidades. Null: no aplica o aún no la declaró.
ALTER TABLE resultado_cuestionario ADD COLUMN preferencia_multimodal VARCHAR(15);
