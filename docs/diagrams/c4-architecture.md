# Arquitectura C4 — ToDo Security

## Nivel 1. Contexto del sistema

```mermaid
flowchart TD
    User["Usuario"]
    Admin["Administrador"]
    System["Sistema ToDo Security"]

    User -->|"Registra cuenta, inicia sesión y gestiona sus tareas"| System
    Admin -->|"Inicia sesión y consulta el resumen administrativo"| System
```

El sistema permite a los usuarios autenticarse y administrar sus propias tareas. Los administradores tienen acceso adicional al resumen administrativo, sujeto a autorización.

## Nivel 2. Contenedores

```mermaid
flowchart TD
    Browser["Navegador web"]
    Frontend["Frontend React + Vite"]
    Backend["Backend Spring Boot / Java 21"]
    Database[("PostgreSQL 17")]

    Browser --> Frontend
    Frontend -->|"HTTP / JSON + Bearer JWT"| Backend
    Backend -->|"Spring Data JPA / Hibernate"| Database
```

El frontend consume la API REST del backend. El backend valida la autenticación y autorización antes de ejecutar operaciones protegidas y persiste la información en PostgreSQL.

## Nivel 3. Componentes del backend

```mermaid
flowchart TD
    Client["Frontend React"]
    AuthController["AuthController"]
    UserController["UserController"]
    TaskController["TaskController"]
    AdminController["AdminController"]
    Security["SecurityConfig"]
    Jwt["JwtService"]
    AuthService["AuthService / AuthServiceImpl"]
    TaskService["TaskService / TaskServiceImpl"]
    AdminService["AdminService / AdminServiceImpl"]
    UserRepository["UserRepository"]
    TaskRepository["TaskRepository"]
    DB[("PostgreSQL")]

    Client --> AuthController
    Client --> UserController
    Client --> TaskController
    Client --> AdminController

    AuthController --> AuthService
    AuthService --> Jwt
    Security --> Jwt
    UserController --> AuthService
    TaskController --> TaskService
    AdminController --> AdminService

    AuthService --> UserRepository
    TaskService --> TaskRepository
    AdminService --> UserRepository
    AdminService --> TaskRepository

    UserRepository --> DB
    TaskRepository --> DB
```

`SecurityConfig` establece las reglas de acceso de la API y utiliza el servicio JWT para apoyar la autenticación. Los controladores delegan la lógica de negocio en los servicios, que acceden a los datos mediante repositorios.

## Consideraciones de seguridad

- Las rutas de registro e inicio de sesión son públicas.
- Las demás rutas protegidas requieren un JWT válido.
- Los usuarios tienen el rol `USER` por defecto; las operaciones administrativas requieren `ADMIN`.
- Las contraseñas se almacenan usando BCrypt, no en texto plano.
- Las tareas deben estar asociadas a su propietario.
- La autenticación es sin estado en el servidor mediante tokens JWT.
