# Backend

Requires Java 17+, MySQL 8.0.16+ and a PDF-capable AI model.

## Configuration

Create `backend/.env` with plain `key=value` entries:

```properties
DB_URL=jdbc:mysql://localhost:3306/laborlens
DB_USERNAME=your_database_user
DB_PASSWORD=your_database_password
JWT_SECRET_KEY=your_random_secret_at_least_32_utf8_bytes
AUTH_COOKIE_SECURE=false
AUTH_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:8080
spring.ai.openai.api-key=your_api_key
spring.ai.openai.chat.options.model=your_chat_model

DATA_DIR=../data
```

Use `AUTH_COOKIE_SECURE=true` and HTTPS origins in production.

## Commands

Run from `backend/`.

Start the application:

```sh
./mvnw spring-boot:run
```

Run all ingestion jobs:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=ingestion
```

Download fresh files and force reprocessing:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=ingestion -Dspring-boot.run.arguments="--app.jobs.refresh=true --app.jobs.force=true"
```
