-- Esquema CUADRANTES: dos estilos como ejes cortados en cuatro esquinas con nombre.
-- El CHECK lo generó Hibernate con ddl-auto=update (enum por nombre) y no admite el valor nuevo.
ALTER TABLE cuestionario
  DROP CONSTRAINT cuestionario_esquema_interpretacion_check,
  ADD CONSTRAINT cuestionario_esquema_interpretacion_check CHECK (esquema_interpretacion IN
    ('NINGUNA','BAREMO','RELATIVO','RELATIVO_ESCALONADO','CUADRANTES'));

CREATE TABLE plano_cuadrantes (
  cuestionario_id   bigint PRIMARY KEY REFERENCES cuestionario(id) ON DELETE CASCADE,
  eje_x_id          bigint NOT NULL REFERENCES estilo(id) ON DELETE CASCADE,
  eje_y_id          bigint NOT NULL REFERENCES estilo(id) ON DELETE CASCADE,
  corte_x           double precision NOT NULL DEFAULT 0,
  corte_y           double precision NOT NULL DEFAULT 0,
  x_alto_y_alto     varchar(60) NOT NULL,
  x_bajo_y_alto     varchar(60) NOT NULL,
  x_bajo_y_bajo     varchar(60) NOT NULL,
  x_alto_y_bajo     varchar(60) NOT NULL,
  CHECK (eje_x_id <> eje_y_id)
);
