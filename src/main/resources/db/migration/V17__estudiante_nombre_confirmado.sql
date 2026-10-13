-- El estudiante puede escribir o corregir su nombre una sola vez, al guardar su
-- perfil; después queda fijo para no alterar los reportes. Default false: las
-- cuentas existentes conservan esa única corrección.
alter table estudiante
    add column nombre_confirmado boolean not null default false;
