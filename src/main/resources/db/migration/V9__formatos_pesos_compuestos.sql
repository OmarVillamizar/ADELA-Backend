-- Fase 6 del motor de calificación: formatos JERARQUIA y REPARTO, pesos N:M
-- (una opción puede sumar a varios estilos) y estilos compuestos (AC-CE en
-- Kolb, polos del ILS). opcion_peso pasa a ser la única fuente de pesos.

ALTER TABLE pregunta
  ADD COLUMN formato         varchar(15) NOT NULL DEFAULT 'UNICA',
  ADD COLUMN min_selecciones integer     NOT NULL DEFAULT 0,
  ADD COLUMN max_selecciones integer,
  ADD COLUMN puntos_repartir integer;
UPDATE pregunta SET formato = 'MULTIPLE' WHERE opcion_multiple;
ALTER TABLE pregunta
  DROP COLUMN opcion_multiple,
  ADD CONSTRAINT ck_pregunta_formato CHECK (formato IN ('UNICA','MULTIPLE','JERARQUIA','REPARTO')),
  ADD CONSTRAINT ck_pregunta_selecciones CHECK (min_selecciones >= 0
       AND (max_selecciones IS NULL OR max_selecciones >= min_selecciones)),
  ADD CONSTRAINT ck_pregunta_reparto CHECK (formato <> 'REPARTO' OR puntos_repartir > 0);

ALTER TABLE estilo
  ADD COLUMN tipo varchar(10) NOT NULL DEFAULT 'PRIMARIO',
  ADD CONSTRAINT ck_estilo_tipo CHECK (tipo IN ('PRIMARIO','COMPUESTO'));

CREATE TABLE opcion_peso (
  opcion_id bigint           NOT NULL REFERENCES opcion(id) ON DELETE CASCADE,
  estilo_id bigint           NOT NULL REFERENCES estilo(id) ON DELETE CASCADE,
  peso      double precision NOT NULL,
  PRIMARY KEY (opcion_id, estilo_id)
);
CREATE INDEX opcion_peso_estilo_idx ON opcion_peso(estilo_id);
-- Conserva los cuestionarios de desarrollo (CHAEA/VARK de las pruebas E2E).
INSERT INTO opcion_peso (opcion_id, estilo_id, peso) SELECT id, estilo_id, valor FROM opcion;
ALTER TABLE opcion DROP COLUMN estilo_id, DROP COLUMN valor;

CREATE TABLE estilo_compuesto_coef (
  compuesto_id bigint           NOT NULL REFERENCES estilo(id) ON DELETE CASCADE,
  primario_id  bigint           NOT NULL REFERENCES estilo(id) ON DELETE CASCADE,
  coeficiente  double precision NOT NULL,
  PRIMARY KEY (compuesto_id, primario_id),
  CHECK (compuesto_id <> primario_id)
);

-- Cantidad por opción elegida: el rango (JERARQUIA) o los puntos (REPARTO); 1 en UNICA y MULTIPLE.
ALTER TABLE resultado_pregunta       ADD COLUMN cantidad double precision NOT NULL DEFAULT 1;
ALTER TABLE respuesta_capsula_opcion ADD COLUMN cantidad double precision NOT NULL DEFAULT 1;
