-- Orientación del dibujo del plano: con true, el lado alto del eje se dibuja a la
-- izquierda (X) o abajo (Y). No cambia la esquina que recibe cada puntaje.
ALTER TABLE plano_cuadrantes
  ADD COLUMN invertir_x boolean NOT NULL DEFAULT false,
  ADD COLUMN invertir_y boolean NOT NULL DEFAULT false;
