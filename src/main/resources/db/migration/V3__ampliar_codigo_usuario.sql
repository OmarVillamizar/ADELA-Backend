-- El codigo pasa de 8 a 12 caracteres: es el identificador de la UFPS y sus
-- formatos no caben siempre en 8. Ampliar un varchar no reescribe la tabla ni
-- toca el unique existente.
ALTER TABLE usuario ALTER COLUMN codigo TYPE varchar(12);
