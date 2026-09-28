-- Initial schema. Requires MySQL 8.0.16+ for enforced CHECK constraints.
-- Display labels and dataset routing remain in backend code.

CREATE TABLE source_file (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    table_name VARCHAR(64) NOT NULL,
    download_url VARCHAR(1024) NOT NULL,
    download_path VARCHAR(1024) NOT NULL,
    checksum CHAR(64) NOT NULL,
    CONSTRAINT chk_source_checksum CHECK (REGEXP_LIKE(checksum, '^[0-9a-f]{64}$')),
    CONSTRAINT uq_source_name UNIQUE (file_name),
    CONSTRAINT chk_source_table CHECK (table_name IN ('annual_employment_rate', 'monthly_unemployment_rate', 'quarterly_employment_rate', 'quarterly_employment_count', 'policy'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- Published observations only; missing measures are excluded during ingestion.
CREATE TABLE annual_employment_rate (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL,
    age_group VARCHAR(32) NOT NULL,
    sex VARCHAR(32) NOT NULL,
    education_attainment_level VARCHAR(32) NOT NULL,
    nuts_2_region VARCHAR(32) NOT NULL,
    employment_rate_percent DOUBLE NOT NULL,
    CONSTRAINT uq_annual_observation UNIQUE (year, age_group, sex, education_attainment_level, nuts_2_region),
    INDEX idx_annual_trend (age_group, sex, education_attainment_level, nuts_2_region, year),
    INDEX idx_annual_region (nuts_2_region, year),
    CONSTRAINT chk_annual_year CHECK (year BETWEEN 2019 AND 2025),
    CONSTRAINT chk_annual_age_group CHECK (age_group IN ('ALL', 'AGE_20_24', 'AGE_25_29', 'AGE_30_34', 'AGE_35_39', 'AGE_40_44', 'AGE_45_49', 'AGE_50_54', 'AGE_55_59', 'AGE_60_64')),
    CONSTRAINT chk_annual_sex CHECK (sex IN ('ALL', 'FEMALE', 'MALE')),
    CONSTRAINT chk_annual_education_attainment_level CHECK (education_attainment_level IN ('ALL', 'PRIMARY_LOWER_SECONDARY', 'UPPER_POST_SECONDARY', 'TERTIARY')),
    CONSTRAINT chk_annual_nuts_2_region CHECK (nuts_2_region IN ('IRELAND', 'NORTHERN_WESTERN', 'SOUTHERN', 'EASTERN_MIDLAND')),
    CONSTRAINT chk_annual_measure CHECK (employment_rate_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- Published observations only; missing measures are excluded during ingestion.
CREATE TABLE monthly_unemployment_rate (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL,
    month INT NOT NULL,
    age_group VARCHAR(32) NOT NULL,
    sex VARCHAR(32) NOT NULL,
    unemployment_rate_percent DOUBLE NOT NULL,
    CONSTRAINT uq_monthly_observation UNIQUE (year, month, age_group, sex),
    INDEX idx_monthly_trend (age_group, sex, year, month),
    CONSTRAINT chk_monthly_year CHECK (year BETWEEN 2019 AND 2025),
    CONSTRAINT chk_monthly_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT chk_monthly_age_group CHECK (age_group IN ('AGE_15_24', 'AGE_25_74', 'AGE_15_74')),
    CONSTRAINT chk_monthly_sex CHECK (sex IN ('ALL', 'FEMALE', 'MALE')),
    CONSTRAINT chk_monthly_measure CHECK (unemployment_rate_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- Published observations only; missing measures are excluded during ingestion.
CREATE TABLE quarterly_employment_rate (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL,
    quarter INT NOT NULL,
    age_group VARCHAR(32) NOT NULL,
    sex VARCHAR(32) NOT NULL,
    education_attainment_level VARCHAR(32) NOT NULL,
    employment_rate_percent DOUBLE NOT NULL,
    CONSTRAINT uq_quarterly_observation UNIQUE (year, quarter, age_group, sex, education_attainment_level),
    INDEX idx_quarterly_trend (age_group, sex, education_attainment_level, year, quarter),
    INDEX idx_quarterly_education (education_attainment_level, year, quarter),
    CONSTRAINT chk_quarterly_year CHECK (year BETWEEN 2019 AND 2025),
    CONSTRAINT chk_quarterly_quarter CHECK (quarter BETWEEN 1 AND 4),
    CONSTRAINT chk_quarterly_age_group CHECK (age_group IN ('ALL', 'AGE_20_24', 'AGE_25_29', 'AGE_30_34', 'AGE_35_39', 'AGE_40_44', 'AGE_45_49', 'AGE_50_54', 'AGE_55_59', 'AGE_60_64', 'AGE_25_54')),
    CONSTRAINT chk_quarterly_sex CHECK (sex IN ('ALL', 'FEMALE', 'MALE')),
    CONSTRAINT chk_quarterly_education_attainment_level CHECK (education_attainment_level IN ('ALL', 'PRIMARY_LOWER_SECONDARY', 'UPPER_POST_SECONDARY', 'TERTIARY')),
    CONSTRAINT chk_quarterly_measure CHECK (employment_rate_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- Published observations only; missing measures are excluded during ingestion.
CREATE TABLE quarterly_employment_count (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL,
    quarter INT NOT NULL,
    citizenship VARCHAR(32) NOT NULL,
    economic_sector VARCHAR(32) NOT NULL,
    employed_persons_thousands DOUBLE NOT NULL,
    CONSTRAINT uq_count_observation UNIQUE (year, quarter, citizenship, economic_sector),
    INDEX idx_count_trend (citizenship, economic_sector, year, quarter),
    INDEX idx_count_sector (economic_sector, year, quarter),
    CONSTRAINT chk_count_year CHECK (year BETWEEN 2019 AND 2025),
    CONSTRAINT chk_count_quarter CHECK (quarter BETWEEN 1 AND 4),
    CONSTRAINT chk_count_citizenship CHECK (citizenship IN ('ALL', 'IRELAND', 'EXCLUDING_IRELAND', 'OUTSIDE_EU_UK')),
    CONSTRAINT chk_count_economic_sector CHECK (economic_sector IN ('ALL', 'AGRICULTURE', 'INDUSTRY_CONSTRUCTION', 'SERVICES', 'INFORMATION_COMMUNICATION')),
    CONSTRAINT chk_count_measure CHECK (employed_persons_thousands >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- Strategy coverage is not an implementation interval; editions remain distinct.
CREATE TABLE policy (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    period_start INT NOT NULL,
    period_end INT NOT NULL,
    policy TEXT NOT NULL,
    type VARCHAR(32) NOT NULL,
    source_file_id BIGINT UNSIGNED NOT NULL,
    INDEX idx_policy_type_period (type, period_start, period_end),
    INDEX idx_policy_source (source_file_id),
    CONSTRAINT fk_policy_source FOREIGN KEY (source_file_id) REFERENCES source_file (id) ON DELETE RESTRICT,
    CONSTRAINT chk_policy_period CHECK (period_start <= period_end),
    CONSTRAINT chk_policy_text CHECK (REGEXP_LIKE(policy, '[^[:space:]]')),
    CONSTRAINT chk_policy_type CHECK (type IN ('ECONOMIC_MIGRATION', 'EMPLOYMENT_DEVELOPMENT', 'SKILLS_DEVELOPMENT', 'WORKING_CONDITIONS', 'EMPLOYMENT_INCLUSION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- One attempt per file and job; preparation may fail before source registration.
CREATE TABLE job_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_type VARCHAR(32) NOT NULL,
    source_file_id BIGINT UNSIGNED NULL,
    file_name VARCHAR(255) NOT NULL,
    checksum CHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    row_count BIGINT UNSIGNED NULL,
    error_message TEXT NULL,
    process_version VARCHAR(128) NOT NULL,
    INDEX idx_job_success (source_file_id, job_type, status, checksum, process_version),
    INDEX idx_job_file_time (file_name, started_at),
    CONSTRAINT fk_job_source FOREIGN KEY (source_file_id) REFERENCES source_file (id) ON DELETE RESTRICT,
    CONSTRAINT chk_job_type CHECK (job_type IN ('FILE_PREPARATION', 'CSV_IMPORT', 'POLICY_EXTRACTION')),
    CONSTRAINT chk_job_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_job_checksum CHECK (checksum IS NULL OR REGEXP_LIKE(checksum, '^[0-9a-f]{64}$')),
    CONSTRAINT chk_job_file CHECK (REGEXP_LIKE(file_name, '[^[:space:]]')),
    CONSTRAINT chk_job_version CHECK (REGEXP_LIKE(process_version, '[^[:space:]]')),
    CONSTRAINT chk_job_finished CHECK (
        (status = 'RUNNING' AND finished_at IS NULL)
        OR (status <> 'RUNNING' AND finished_at IS NOT NULL AND finished_at >= started_at)
    ),
    CONSTRAINT chk_job_input CHECK (
        job_type = 'FILE_PREPARATION' OR (source_file_id IS NOT NULL AND checksum IS NOT NULL)
    ),
    CONSTRAINT chk_job_success CHECK (
        status <> 'SUCCESS' OR (source_file_id IS NOT NULL AND checksum IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
