# Blackout Esports — Usuarios

Microservicio independiente con Java 21, Spring Boot 3.5.0, PostgreSQL y CRUD REST.
Puerto local: **8083**. Base de datos: **users_db**, puerto **5435**.

## Responsabilidad

Guarda perfiles de fans: identidad Cognito (`cognitoSub`), nombre visible y equipo favorito.
El identificador de Cognito es único e inmutable. No guarda contraseñas, no emite tokens y no crea cuentas en Cognito.
El staff crea, actualiza y elimina los perfiles de la aplicación; Cognito administra la cuenta de acceso por separado.
Eliminar un perfil no elimina la cuenta Cognito. No existe autorregistro ni escritura para fans en este servicio.

## Permisos y autenticación

| Operación | Entra ID con Admin/Staff | Cognito |
|---|---|---|
| GET /api/users y GET /api/users/{id} | Permitido | Denegado (datos de otros fans) |
| POST, PUT y DELETE /api/users | Permitido | Denegado |
| GET /api/users/me | No aplica | Solo su perfil, identificado por `sub` |

Todas las rutas de datos requieren un token. Solo la documentación Swagger es pública.
La API comprueba firma RSA, emisor permitido, vencimiento y sujeto del JWT.
Entra requiere la audiencia de esta API, el scope configurado y el rol exacto `Admin` o `Staff`.
Un token de Cognito requiere `token_use=access` y el `client_id` configurado; aunque contenga un claim `roles: [Admin]`, solo obtiene permisos de fan.
Un ID token de Entra sin el scope de API, un ID token de Cognito y los access tokens de Microsoft Graph se rechazan.
Sin configuración de los proveedores, las operaciones protegidas quedan inaccesibles; no hay un modo de producción que omita la seguridad.

## Ejecutar con Docker

Requisitos: Docker con Compose.

1. Copiar `.env.example` a `.env` en la raíz y definir `DB_PASSWORD`.
2. Completar los valores de Entra y Cognito de tu proyecto:
   - `ENTRA_ISSUER=https://login.microsoftonline.com/<tenant-id>/v2.0`
   - `ENTRA_AUDIENCE=<audience de la API registrada en Entra>`
   - `ENTRA_SCOPE=access_as_user`
   - `COGNITO_ISSUER=https://cognito-idp.<region>.amazonaws.com/<user-pool-id>`
   - `COGNITO_CLIENT_ID=<app-client-id>`
3. Ejecutar `docker compose up --build`.
4. Abrir [Swagger](http://localhost:8083/swagger-ui.html) y usar **Authorize** con el access token, sin escribir el prefijo Bearer.

Compose carga `.env`. Para ejecutar directamente con Maven hay que exportar esas variables en la terminal; Spring Boot no carga automáticamente ese archivo.

## Ejecutar con Java

Con Java 21, Maven y PostgreSQL disponibles:
```text
cd blackout-users
mvn spring-boot:run
```

## Contrato CRUD

- `GET /api/users`: lista ordenada por ID.
- `GET /api/users/{id}`: detalle.
- `POST /api/users`: devuelve 201 y cabecera Location.
- `PUT /api/users/{id}`: reemplaza los campos del registro.
- `DELETE /api/users/{id}`: devuelve 204.
- `GET /api/users/me`: consulta del perfil propio de Cognito; 404 si el staff todavía no lo registró.

Ejemplo de cuerpo para POST y PUT:
```json
{
  "cognitoSub": "identificador-sub-del-fan-en-cognito",
  "displayName": "Fan de Blackout",
  "favoriteTeam": "Blackout Esports"
}
```

Errores: 400 por validación, 401 por token ausente/inválido, 403 por falta de permisos, 404 por registro inexistente y 409 por conflicto de datos.

## Pruebas

```text
cd blackout-users
mvn verify
```

Las pruebas usan H2 en modo PostgreSQL y JWT firmados con claves RSA generadas solo para pruebas.
Cubren CRUD, persistencia, validación, CORS, documentación y restricciones de permisos. También prueban firma falsa, emisor desconocido, expiración, audience/scope incorrectos y tokens Cognito de tipo ID o de otro cliente.
El perfil `test` no permite omitir autenticación en la aplicación; el validador de claves de prueba existe únicamente en `src/test`.
GitHub Actions ejecuta la misma verificación con Java 21.

## Ramas e integración pendiente

- `main`: base inicial.
- `develop`: base de integración.
- `feature-users`: implementación CRUD.

La conexión de los frontends y API Gateway/BFF se realizará por separado. El gateway deberá reenviar el access token, y este servicio seguirá validándolo.
Faltan las credenciales/configuración real de los proveedores y la prueba completa con tokens reales.
Para un despliegue productivo, sustituir `ddl-auto: update` por migraciones versionadas, limitar el acceso de red y configurar los orígenes CORS del despliegue.
No se han creado recursos de Azure ni AWS.

Referencias: [JWT en Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) y [verificación de tokens Cognito](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html).
