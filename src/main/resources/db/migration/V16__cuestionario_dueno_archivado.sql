-- Dueño y archivado del cuestionario.
-- profesor_email NULL = cuestionario general de ADELA, sin dueño: lo gestiona
-- SUPERADMIN y no queda atado a ninguna cuenta. Con valor = personal de ese profesor.
-- Sin ON DELETE: borrar un profesor con cuestionarios propios debe fallar, no
-- volverlos generales (SET NULL) ni arrastrar resultados de estudiantes (CASCADE).
alter table cuestionario
    add column profesor_email varchar(100) references profesor(email),
    add column archivado      boolean not null default false;
create index cuestionario_profesor_idx on cuestionario(profesor_email);
