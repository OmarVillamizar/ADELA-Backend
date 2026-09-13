# ADELA — Backend

REST API para automatización de perfiles de aprendizaje (CHAEA / VARK).

Spring Boot 3.3.4 · Java 17 · PostgreSQL 16 · OAuth2 Google + JWT propio.

## Arranque local

1. **Base de datos.** Desde `ADELA-Database`:

   ```bash
   docker compose up -d
   ```

   Levanta PostgreSQL 16 en `127.0.0.1:55432` (el 5432 y el 5433 suelen estar
   ocupados por una instalación nativa en Windows).

2. **Variables de entorno.** Copia `.env.template` a `.env` y rellénalo. El
   proyecto usa `spring-dotenv`, así que lo lee al arrancar sin exportar nada.

3. **Arrancar:**

   ```bash
   ./mvnw spring-boot:run
   ```

La primera vez Flyway adopta el esquema existente como baseline y aplica las
migraciones pendientes.

## Variables de entorno

| Variable | Ejemplo | Para qué |
|---|---|---|
| `PORT` | `8091` | Puerto del servidor |
| `DATABASE_URL` | `jdbc:postgresql://localhost:55432/adela` | Cadena JDBC |
| `DATABASE_USERNAME` | `adela` | Usuario de la base |
| `DATABASE_PASSWORD` | — | Contraseña de la base |
| `DATABASE_DRIVER_NAME` | `org.postgresql.Driver` | Driver JDBC |
| `HIBERNATE_DIALECT` | `org.hibernate.dialect.PostgreSQLDialect` | Dialecto |
| `GOOGLE_CLIENT_ID` | — | Cliente OAuth2 de Google Cloud |
| `GOOGLE_CLIENT_SECRET` | — | Secreto del mismo cliente |
| `JWT_SECRET_KEY` | — | Clave HMAC **en Base64**, mínimo 32 bytes decodificados |
| `ALLOWED_ORIGINS` | `http://localhost:3000` | Orígenes permitidos, separados por coma |
| `SWAGGER_ENABLED` | `false` | `true` publica `/docs`; déjalo en `false` fuera de desarrollo |

`ALLOWED_ORIGINS` no es solo CORS: **también decide a qué direcciones puede
redirigir el login**. Si falta el origen del frontend, `/auth/login/success/**`
responde 400 en lugar de redirigir y nadie puede entrar.

## Google Cloud

En *APIs & Services → Credentials*, sobre el cliente OAuth 2.0:

- **Authorized JavaScript origins:** el origen del frontend, p. ej. `http://localhost:3000`.
- **Authorized redirect URIs:** `http://localhost:8091/login/oauth2/code/google`
  (el puerto es el del backend, no el del frontend).

## Esquema de base de datos

Flyway es la fuente de verdad; Hibernate está en `ddl-auto: validate` y no altera
nada. **Todo cambio en una entidad necesita su migración** en
`src/main/resources/db/migration/V<n>__<nombre>.sql`, o la aplicación no arranca.
Nunca se edita una migración ya aplicada: Flyway las verifica por checksum.

## Pruebas

```bash
./mvnw test
```

Unitarias, sin contexto de Spring: no necesitan base de datos ni credenciales.
Cubren los controles de propiedad de recursos y el contrato de error.

## Documentación de la API

Con `SWAGGER_ENABLED=true`: <http://localhost:8091/docs/swagger-ui.html>

En el diálogo *Authorize* pega **solo el token**, sin el prefijo `Bearer`:
Swagger lo antepone por su cuenta y duplicarlo da `TOKEN_MALFORMADO`.

## Errores de la API

Todas las respuestas de error comparten forma:

```json
{
  "timestamp": "2026-09-13T20:03:44Z",
  "status": 400,
  "code": "VALIDACION",
  "message": "Revisa los campos marcados.",
  "fields": { "codigo": "El código admite un máximo de 8 caracteres" },
  "traceId": "6f73fb93",
  "path": "/api/estudiantes"
}
```

`code` es estable y apto para ramificar en el cliente; `fields` solo aparece en
errores de validación. El `traceId` sale también en el log del servidor: es lo
que hay que pedir a un usuario que reporta un fallo.
