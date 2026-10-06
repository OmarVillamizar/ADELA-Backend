-- Los rangos por estilo se acumulaban al crear cada pregunta y en seleccion
-- unica ignoraban que las demas opciones apuntan a otro estilo. Ahora el motor
-- calcula el rango teorico al calificar, asi que las columnas sobran.
alter table estilo
    drop column valor_minimo,
    drop column valor_maximo;
