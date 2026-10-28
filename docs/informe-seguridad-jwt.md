# Informe de seguridad — ToDo Security (Laboratorio 6)

## 1. Objetivo

Implementar autenticación y autorización mediante JSON Web Tokens (JWT) en la aplicación ToDo, protegiendo el acceso a las tareas y a los recursos administrativos.

## 2. Autenticación

### Registro

El endpoint `POST /api/v1/auth/register` permite crear una cuenta. El backend normaliza el correo electrónico, verifica que no esté registrado y almacena la contraseña usando BCrypt. El rol asignado al registrarse es `USER`; el cliente no debe poder asignarse privilegios administrativos mediante los datos enviados.

### Inicio de sesión

El endpoint `POST /api/v1/auth/login` valida las credenciales. Cuando son correctas, devuelve un JWT firmado. Cuando son incorrectas, la API rechaza la autenticación.

### Token JWT

El token contiene la identidad del usuario en `sub`, el emisor `iss`, la fecha de emisión `iat` y la expiración `exp`. Se firma con HS256 y tiene una vigencia de 30 minutos. El backend debe validar la firma, el emisor y la expiración antes de aceptar el token.

## 3. Autorización

| Recurso | Acceso esperado |
|---|---|
| `POST /api/v1/auth/register` | Público |
| `POST /api/v1/auth/login` | Público |
| `GET /api/v1/users/me` | Usuario autenticado |
| `/api/v1/tasks` | Usuario autenticado y limitado a sus propias tareas |
| `GET /api/v1/admin/summary` | Solo administrador |

Una petición protegida sin credenciales válidas debe recibir `401 Unauthorized`. Un usuario autenticado sin permisos suficientes debe recibir `403 Forbidden`. La consulta de una tarea ajena debe evitar revelar su existencia y responder como recurso no encontrado, según la lógica del servicio.

## 4. Persistencia y propiedad de las tareas

La tabla de usuarios almacena la identidad, el correo, el hash de contraseña y el rol. Las tareas se relacionan con su propietario mediante `owner_id`.

La migración de seguridad debe conservar las tareas existentes y asociarlas a un usuario heredado cuando no tengan un propietario real. Ese usuario heredado debe tratarse como una cuenta técnica de migración, no como un usuario normal de la aplicación.

## 5. Seguridad del frontend

El frontend obtiene el JWT al iniciar sesión y lo envía en el encabezado `Authorization: Bearer`. El token se mantiene en memoria y no debe guardarse en `localStorage`. Al cerrar sesión se elimina el token del estado de la aplicación.

Una respuesta `401` debe provocar que se invalide la sesión del cliente y se solicite iniciar sesión de nuevo. La interfaz puede ocultar opciones administrativas a los usuarios sin rol `ADMIN`, pero la autorización real debe imponerse siempre en el backend.

## 6. Verificaciones realizadas

Durante las pruebas manuales se verificaron los siguientes escenarios:

| Escenario | Resultado observado |
|---|---|
| Registro de usuario | `201 Created` |
| Inicio de sesión válido | Token recibido |
| Consulta de `/api/v1/users/me` con token | `200 OK` |
| Consulta de tareas sin token | `401 Unauthorized` |
| Usuario `USER` consulta el resumen administrativo | `403 Forbidden` |

Además, se ejecutaron las pruebas automatizadas del proyecto:

- Backend: `mvn clean verify` — compilación y pruebas exitosas.
- Frontend: `npm test` — 13 pruebas exitosas.
- Frontend: `npm run build` — compilación exitosa.

## 7. Limitaciones y recomendaciones

- La clave de firma JWT debe mantenerse en variables de entorno y nunca publicarse en el repositorio.
- En producción debe utilizarse HTTPS.
- Las credenciales de PostgreSQL deben gestionarse de forma segura.
- No se implementaron renovación de tokens, recuperación de contraseña ni autenticación multifactor.
- La seguridad de la propiedad de las tareas debe probarse también con dos usuarios distintos.
- Las pruebas automatizadas y las verificaciones manuales documentan los escenarios comprobados; no constituyen una auditoría de seguridad completa.

## 8. Conclusión

La solución integra autenticación JWT, contraseñas cifradas mediante BCrypt, autorización basada en roles y una separación entre frontend, API y persistencia. Los resultados de las pruebas documentadas respaldan el funcionamiento de los escenarios verificados, con las limitaciones indicadas.
