-- A single user type; JWTs are never stored in the database.
CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    token_version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_username CHECK (CHAR_LENGTH(TRIM(username)) BETWEEN 1 AND 50),
    CONSTRAINT chk_users_version CHECK (token_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
