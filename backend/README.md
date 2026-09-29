# Backend

Requires Java 17+, MySQL 8.0.16+ and an AI API key. Policy ingestion requires a PDF-capable model.

## Configuration

Create the `laborlens` database, then create `backend/.env` with plain `key=value` entries:

```properties
DB_URL=jdbc:mysql://localhost:3306/laborlens
DB_USERNAME=your_database_user
DB_PASSWORD=your_database_password
flyway.url=jdbc:mysql://localhost:3306/laborlens
flyway.user=your_database_user
flyway.password=your_database_password
JWT_SECRET_KEY=your_random_secret_at_least_32_utf8_bytes
AUTH_COOKIE_SECURE=false
AUTH_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080
spring.ai.openai.api-key=your_api_key
spring.ai.openai.chat.options.model=your_chat_model

DATA_DIR=../data
```

Use `AUTH_COOKIE_SECURE=true` and HTTPS origins in production.

## Commands

Run from `backend/`.

1. Run database migrations:

```sh
./mvnw flyway:migrate
```

2. Run all ingestion jobs:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=ingestion
```

To download fresh files and force reprocessing, use instead:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=ingestion -Dspring-boot.run.arguments="--app.jobs.refresh=true --app.jobs.force=true"
```

3. Start the application at `http://localhost:8080`:

```sh
./mvnw spring-boot:run
```

Or build and run the packaged application:

```sh
./mvnw clean package
java -jar target/laborlens-0.0.1-SNAPSHOT.jar
```
