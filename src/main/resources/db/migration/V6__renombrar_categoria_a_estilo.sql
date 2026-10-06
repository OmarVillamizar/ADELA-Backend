-- La entidad Categoria pasa a llamarse Estilo; la tabla ya era "estilo",
-- solo la FK de opcion conservaba el nombre viejo.
ALTER TABLE opcion RENAME COLUMN categoria_id TO estilo_id;
