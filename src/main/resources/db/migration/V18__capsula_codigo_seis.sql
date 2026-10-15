-- El código de cápsula es de 6 caracteres (CapsulaService.LONGITUD_CODIGO_CAPSULA);
-- la columna se creó con holgura para 8 que nunca se usó.
ALTER TABLE capsula ALTER COLUMN codigo TYPE varchar(6);
