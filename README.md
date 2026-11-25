# PrestaControl API

API REST para gestión de préstamos personales orientada a prestamistas, con autenticación stateless basada en JWT y aislamiento multiusuario por prestamista.

## Arquitectura

- **Stack**: Java 21, Spring Boot 4.2.0, Spring Security 7, Spring Data JPA, Jakarta Validation, JJWT 0.12.6, PostgreSQL.
- **Capas** (`com.prestacontrol.AppPrestaControl`):
  - `controller/`: `AuthController` (`/auth`), `PrestamoController` (`/prestamos`).
  - `service/`: `AuthService`, `PrestamoService`, `DuplicateEmailException`, `ResourceNotFoundException`.
  - `repository/`: `UsuarioRepository`, `PrestamoRepository`.
  - `entity/`: `Usuario`, `Prestamo`.
  - `dto/`: `RegisterRequest`, `LoginRequest`, `AuthResponse`, `PrestamoRequest`, `PrestamoUpdateRequest`, `PrestamoResponse`, `ResumenCarteraResponse`, `ErrorResponse`.
  - `security/`: `JwtService`, `CustomUserDetailsService`, `JwtAuthFilter`, `JwtAuthenticationEntryPoint`.
  - `config/`: `SecurityConfig`, `CorsConfig`, `GlobalExceptionHandler`.

## Esquema de Base de Datos

Definido en `sql/schema.sql`:

- **`usuario`**: `id` (`BIGINT IDENTITY PK`), `nombre` (`VARCHAR(100)`), `email` (`VARCHAR(120) UNIQUE`), `password` (`VARCHAR(100)` hash BCrypt), `rol` (`VARCHAR(20)`).
- **`prestamo`**: `id` (`BIGINT IDENTITY PK`), `cliente` (`VARCHAR(100)`), `dni` (`VARCHAR(8)`), `monto` (`NUMERIC(10,2)` > 0), `interes` (`NUMERIC(5,2)`), `fecha_prestamo` (`DATE`), `fecha_vencimiento` (`DATE`), `pagado` (`BOOLEAN`), `usuario_id` (`BIGINT FK -> usuario(id) ON DELETE CASCADE`, índice `idx_prestamo_usuario_id`).

## Endpoints

| Método | Ruta | Auth | Descripción | Códigos |
|---|---|---|---|---|
| `POST` | `/auth/register` | Pública | Registra usuario (`rol=USER`) y retorna JWT | `201`, `400`, `409` |
| `POST` | `/auth/login` | Pública | Autentica credenciales y retorna JWT | `200`, `400`, `401` |
| `GET` | `/prestamos` | Bearer JWT | Lista préstamos propios; filtros opcionales `pagado` y `buscar` | `200`, `401` |
| `GET` | `/prestamos/resumen` | Bearer JWT | Resumen de cartera (`capitalPrestado`, `totalPorCobrar`, `gananciaEsperada`, conteos) | `200`, `401` |
| `GET` | `/prestamos/{id}` | Bearer JWT | Obtiene préstamo propio por ID | `200`, `401`, `404` |
| `POST` | `/prestamos` | Bearer JWT | Crea préstamo (`fechaPrestamo = hoy`) | `201`, `400`, `401` |
| `PUT` | `/prestamos/{id}` | Bearer JWT | Actualiza campos no nulos del préstamo | `200`, `400`, `401`, `404` |
| `DELETE` | `/prestamos/{id}` | Bearer JWT | Elimina préstamo propio | `204`, `401`, `404` |

## Ejecución local

1. Copiar variables de entorno:
   ```bash
   cp .env.example .env
   ```
2. Levantar PostgreSQL con Docker Compose:
   ```bash
   docker compose up -d
   ```
3. Ejecutar pruebas y construir el JAR:
   ```bash
   ./gradlew test bootJar
   ```
4. Iniciar la aplicación:
   ```bash
   ./gradlew bootRun
   ```
   O ejecutando el JAR generado:
   ```bash
   java -jar build/libs/AppPrestaControl-0.0.1-SNAPSHOT.jar
   ```

## Despliegue en Render

1. Subir el repositorio a GitHub/GitLab con `Dockerfile` y `render.yaml` en la raíz.
2. En Render Dashboard, elegir **New + -> Blueprint** y seleccionar el repositorio.
3. Render detectará `render.yaml` para crear:
   - Base de datos PostgreSQL `prestacontrol-db` (`prestacontrol`).
   - Servicio web Docker `prestacontrol-api`, conectando `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD` vía `fromDatabase`, `JPA_DDL_AUTO=update` y generando automáticamente `JWT_SECRET`.
4. Confirmar el despliegue con **Apply**.
