-- Banking database schema

CREATE DATABASE IF NOT EXISTS banking_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE banking_db;

-- Customer profiles and authentication state
CREATE TABLE customers (
    customer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_code VARCHAR(30) NOT NULL UNIQUE,

    first_name VARCHAR(60) NOT NULL,
    middle_initial CHAR(1),
    last_name VARCHAR(60) NOT NULL,
    date_of_birth DATE,
    address VARCHAR(255),

    email VARCHAR(254) NOT NULL UNIQUE,
    phone VARCHAR(30),
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,

    status ENUM('ACTIVE', 'LOCKED', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_failed_count
    CHECK (failed_login_count >= 0)

) ENGINE = InnoDB
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
