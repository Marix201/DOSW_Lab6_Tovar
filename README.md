# ToDo App — Laboratorio 6 DOSW

Aplicación full stack de gestión de tareas personales con autenticación y autorización mediante JWT. Desarrollada para el Laboratorio 6 de DOSW con Spring Boot 4, Java 21, PostgreSQL 17, React y Vite.

## Integrante

- Mariana Tovar ([@Marix201](https://github.com/Marix201))

## Arquitectura

- **Frontend:** React + Vite; consume la API por HTTP/JSON.
- **Backend:** Spring Boot, Spring Security, OAuth2 Resource Server, Spring Data JPA y Bean Validation.
- **Persistencia:** PostgreSQL 17 con esquema versionado en `database/`.
- **Autenticación:** JWT firmado con HS256, expiración de 30 minutos y encabezado `Authorization: Bearer <token>`.
- **Pruebas:** JUnit 5, Mockito, AssertJ y JaCoCo en backend; Vitest y React Testing Library en frontend.

## Funcionalidades de seguridad

- Registro de usuarios con validación de datos y contraseña almacenada como hash BCrypt.
- Inicio de sesión con emisión de token JWT.
- Consulta del perfil autenticado sin exponer contraseña ni hash.
- Protección de las operaciones de tareas mediante JWT.
- Aislamiento de tareas por propietario: el propietario se obtiene del sujeto del token, no de un identificador enviado por el cliente.
- Autorización basada en roles: las operaciones administrativas requieren `ADMIN`.
- Respuestas de seguridad: `401 Unauthorized` para solicitudes sin credenciales válidas y `403 Forbidden` cuando el usuario autenticado no tiene permisos.
- Validación de firma, expiración e issuer del token.

## Requisitos previos

- Git
- JDK 21
- Docker
- Node.js compatible con Vite (20.19+ o 22.12+)

## 1. Clonar y preparar variables de entorno

```bash
git clone https://github.com/Marix201/DOSW_Lab6_Tovar.git
cd DOSW_Lab6_Tovar
git switch feature/jwt-security
cp .env.example .env
```

Genera una clave secreta de al menos 32 caracteres y colócala en `JWT_SECRET` dentro de `.env`:

```bash
openssl rand -base64 48
```

No publiques ni compartas el archivo `.env` ni la clave JWT. El archivo `.env.example` contiene solo los nombres de las variables, sin secretos reales.

Variables disponibles:

| Variable | Uso |
|---|---|
| `JWT_SECRET` | Obligatoria; clave para firmar y validar tokens JWT. |
| `DB_URL` | Opcional; por defecto `jdbc:postgresql://localhost:5432/todo_db`. |
| `DB_USERNAME` | Opcional; por defecto `todo_user`. |
| `DB_PASSWORD` | Opcional; por defecto `todo_password`. |
| `CORS_ALLOWED_ORIGIN` | Opcional; por defecto `http://localhost:5173`. |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Opcionales; crean un administrador para desarrollo local si ambas están configuradas. No usar como mecanismo de provisión en producción. |

## 2. Levantar PostgreSQL y aplicar el esquema

```bash
docker pull postgres:17-alpine
docker volume create todo-postgres-data
docker run -d --name todo-postgres \
  -e POSTGRES_DB=todo_db \
  -e POSTGRES_USER=todo_user \
  -e POSTGRES_PASSWORD=todo_password \
  -p 5432:5432 \
  -v todo-postgres-data:/var/lib/postgresql/data \
  postgres:17-alpine
```

Si el contenedor ya existe y está detenido, usa `docker start todo-postgres` en lugar de volver a ejecutar `docker run`.

**Primera instalación de la base de datos:** aplica ambos scripts en orden, desde la raíz del repositorio. El script `001_create_schema.sql` crea la tabla inicial y no está diseñado para ejecutarse repetidamente:

```bash
docker cp database/001_create_schema.sql todo-postgres:/001_create_schema.sql
docker exec -i todo-postgres psql -U todo_user -d todo_db -f /001_create_schema.sql
docker cp database/002_add_users_security.sql todo-postgres:/002_add_users_security.sql
docker exec -i todo-postgres psql -U todo_user -d todo_db -f /002_add_users_security.sql
```

El segundo script agrega usuarios y propiedad de tareas, y conserva las tareas existentes asignándolas a una cuenta heredada no utilizable para iniciar sesión. La aplicación usa `spring.jpa.hibernate.ddl-auto=validate`, por lo que el esquema debe estar creado antes de iniciar el backend.

**Si ya tienes una base de datos con la tabla `tasks`:** no vuelvas a ejecutar `001_create_schema.sql`. Haz una copia de seguridad y aplica únicamente `002_add_users_security.sql` para incorporar la estructura de seguridad. Si la migración ya se aplicó, no necesitas ejecutarla de nuevo salvo que estés comprobando su idempotencia.

> En una instalación local existente, haz una copia de seguridad de la base de datos antes de ejecutar una migración. No elimines el volumen para intentar corregir errores de esquema.

## 3. Ejecutar el backend

Desde la raíz del repositorio:

```bash
cd backend
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080`. El backend carga el archivo `.env` desde la raíz del repositorio cuando se inicia desde `backend/`.

## 4. Ejecutar el frontend

En otra terminal:

```bash
cd frontend
npm install
npm run dev
```

La interfaz queda disponible en `http://localhost:5173`.

## Endpoints de la API

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Público | Registra un usuario con rol `USER`. |
| POST | `/api/v1/auth/login` | Público | Valida credenciales y devuelve un JWT. |
| GET | `/api/v1/users/me` | JWT válido | Devuelve el perfil del usuario autenticado. |
| GET | `/api/v1/tasks` | JWT válido | Lista únicamente las tareas propias. |
| GET | `/api/v1/tasks/{id}` | JWT válido | Consulta una tarea propia. |
| POST | `/api/v1/tasks` | JWT válido | Crea una tarea para el usuario autenticado. |
| PUT | `/api/v1/tasks/{id}` | JWT válido | Actualiza una tarea propia. |
| DELETE | `/api/v1/tasks/{id}` | JWT válido | Elimina una tarea propia. |
| GET | `/api/v1/admin/summary` | Rol `ADMIN` | Devuelve métricas agregadas administrativas. |

Para acceder a un endpoint protegido, envía el token en el encabezado:

```http
Authorization: Bearer <accessToken>
```

El token se obtiene en la respuesta de `POST /api/v1/auth/login`. No se debe enviar el hash de la contraseña ni aceptar que el cliente elija su rol o propietario de una tarea.

## Ejemplos de peticiones

### Registrar un usuario

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ana Pérez","email":"ana@example.com","password":"ContrasenaDemo123!"}'
```

### Iniciar sesión

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"ContrasenaDemo123!"}'
```

Copia el valor de `accessToken` de la respuesta para las peticiones autenticadas.

### Consultar el perfil

```bash
curl -i http://localhost:8080/api/v1/users/me \
  -H 'Authorization: Bearer <accessToken>'
```

### Comprobar que las tareas están protegidas

```bash
curl -i http://localhost:8080/api/v1/tasks
```

Sin token válido, la solicitud debe responder `401 Unauthorized`.

## Ejecutar las pruebas

Backend:

```bash
cd backend
./mvnw clean verify
```

El reporte JaCoCo se genera en `backend/target/site/jacoco/index.html` al ejecutar las pruebas del backend. La captura versionada en `docs/evidence/05-jacoco-report.png` corresponde a la medición actualizada documentada en el informe.

Frontend, desde otra terminal:

```bash
cd frontend
npm install
npm test
npm run build
```

## Documentación y entregables

- [Informe del laboratorio](docs/informe-laboratorio.md)
- [Informe de seguridad JWT](docs/informe-seguridad-jwt.md)
- [Scripts SQL](database/)
- [Plantilla de variables de entorno](.env.example)
- Reporte JaCoCo: `backend/target/site/jacoco/index.html` (se genera al ejecutar las pruebas de backend; el artefacto de CI también conserva el reporte de cada ejecución).
- Evidencia de cobertura: `docs/evidence/05-jacoco-report.png`.

## Seguridad y limitaciones

- No subir `.env`, claves JWT, contraseñas reales ni tokens de acceso al repositorio.
- La clave JWT debe mantenerse secreta y ser distinta en cada entorno.
- Las credenciales de ejemplo de este README son exclusivamente para una demostración local.
- La ejecución más reciente de CI para esta rama completó correctamente las pruebas de backend y frontend y la compilación. Si se modifica el código de la aplicación, vuelve a ejecutar las pruebas y actualiza los resultados documentados.
