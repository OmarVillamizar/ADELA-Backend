-- Insignias ganadas por cada estudiante. El catálogo vive en el enum Insignia:
-- aquí solo se guarda qué se ganó y si la animación ya se mostró, para que no
-- se repita al recargar ni en otro dispositivo.
CREATE TABLE insignia_estudiante (
    estudiante_email varchar(100) NOT NULL REFERENCES estudiante(email) ON DELETE CASCADE,
    codigo           varchar(40)  NOT NULL,
    obtenida_en      timestamptz  NOT NULL,
    celebrada        boolean      NOT NULL DEFAULT false,
    PRIMARY KEY (estudiante_email, codigo)
);
