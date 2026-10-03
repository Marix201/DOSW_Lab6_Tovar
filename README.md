# ToDo App — Laboratorio 6 DOSW

Aplicación full stack de gestión de tareas (ToDo) desarrollada para el Laboratorio 6 de la asignatura DOSW. Backend en Spring Boot 4 con PostgreSQL, frontend en React con Vite.

## Integrantes

- Mariana Tovar ([@Marix201](https://github.com/Marix201))

## Arquitectura
DOSW_Lab6_Tovar/
├── backend/ # API REST en Spring Boot (Java 21)
├── frontend/ # SPA en React (Vite)
├── database/ # Scripts de esquema SQL
└── docs/ # Informe y evidencias

- **Backend**: Spring Boot 4.1.1, Spring Data JPA, Spring Validation, PostgreSQL Driver. Expone una API REST en `/api/v1/tasks`.
- **Frontend**: React + Vite, consume la API mediante `fetch`.
- **Base de datos**: PostgreSQL 17, corriendo en un contenedor Docker.
- **Pruebas**: JUnit 5 + Mockito + AssertJ en el backend (con cobertura JaCoCo), Vitest + Testing Library en el frontend.

## Requisitos previos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- [JDK 21](https://adoptium.net/)
- [Node.js LTS](https://nodejs.org/) (20.19+ o 22.12+)
- Git

## 1. Clonar el repositorio

```bash
git clone https://github.com/Marix201/DOSW_Lab6_Tovar.git
cd DOSW_Lab6_Tovar
git checkout develop
```

## 2. Levantar la base de datos

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

Crear el esquema:

```bash
docker cp database/001_create_schema.sql todo-postgres:/001_create_schema.sql
docker exec -i todo-postgres psql -U todo_user -d todo_db -f /001_create_schema.sql
```

Verificar que la tabla se creó:

```bash
docker exec -it todo-postgres psql -U todo_user -d todo_db -c "\dt"
```

En ejecuciones posteriores, solo es necesario iniciar el contenedor existente:

```bash
docker start todo-postgres
```

## 3. Ejecutar el backend

```bash
cd backend
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080/api/v1/tasks`.

### Ejecutar las pruebas del backend

```bash
cd backend
./mvnw clean verify
```

El reporte de cobertura JaCoCo queda en `backend/target/site/jacoco/index.html`.

## 4. Ejecutar el frontend

En otra terminal:

```bash
cd frontend
npm install
npm run dev
```

La aplicación queda disponible en `http://localhost:5173`.

### Ejecutar las pruebas del frontend

```bash
cd frontend
npm test
```

## Endpoints de la API

| Método | Ruta                   | Descripción                  |
|--------|------------------------|-------------------------------|
| GET    | /api/v1/tasks          | Lista todas las tareas        |
| GET    | /api/v1/tasks/{id}     | Obtiene una tarea por id      |
| POST   | /api/v1/tasks          | Crea una tarea                |
| PUT    | /api/v1/tasks/{id}     | Actualiza una tarea           |
| DELETE | /api/v1/tasks/{id}     | Elimina una tarea             |

## Documentación adicional

El informe completo del laboratorio, con arquitectura detallada, evidencias, resultados de pruebas y respuestas a las preguntas de análisis, está en [`docs/informe-laboratorio.md`](docs/informe-laboratorio.md).
