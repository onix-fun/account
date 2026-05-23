# Identity Service

A modern Identity and Authentication service built with Kotlin and Ktor.

## Architecture

- **Stateless Access Tokens:** JWT (validation offloaded to Gateway).
- **Opaque Refresh Tokens:** Stored in PostgreSQL, cached in Redis.
- **Pure JDBC:** No ORM, SQL stored in `src/main/resources/sql/`.
- **Event-Driven:** Publishes events (user.created, etc.) to Redis.
- **Storage:** Avatar uploads to S3/MinIO.
- **Email Testing:** MailSlurper (catches all outgoing emails locally).
- **Security:** Password hashing with Argon2id.

## Tech Stack

- **Kotlin 2.x**
- **Ktor 3.x**
- **PostgreSQL** (Managed via Flyway)
- **Redis** (Lettuce client)
- **MinIO / S3**
- **MailSlurper**
- **HikariCP**

## API Endpoints

### Public
- `POST /api/auth/register` - Create a new user.
- `POST /api/auth/login` - Authenticate and get tokens.
- `GET /health` - Service health check.

### UI Consoles (Development)
- **MinIO Console:** `http://localhost:9001`
- **MailSlurper UI:** `http://localhost:8085`

### Protected (Requires Gateway headers)
- `GET /api/users/me` - Get current user profile.

## Development

1. Run the application (this will automatically start Docker dependencies):
   ```bash
   mvn clean compile exec:java
   ```
   *The database schema will be applied automatically via Flyway on startup.*

