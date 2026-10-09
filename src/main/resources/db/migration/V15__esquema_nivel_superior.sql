-- Esquema NIVEL_SUPERIOR: dominantes los estilos en su banda más alta (dominancia primaria de Herrmann).
ALTER TABLE cuestionario
  DROP CONSTRAINT cuestionario_esquema_interpretacion_check,
  ADD CONSTRAINT cuestionario_esquema_interpretacion_check CHECK (esquema_interpretacion IN
    ('NINGUNA','BAREMO','RELATIVO','RELATIVO_ESCALONADO','CUADRANTES','NIVEL_SUPERIOR'));
