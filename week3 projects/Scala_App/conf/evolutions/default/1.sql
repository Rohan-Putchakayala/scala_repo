# --- !Ups

DROP TABLE IF EXISTS check_in_records;
DROP TABLE IF EXISTS visitors;

CREATE TABLE visitors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50) NOT NULL,
    company VARCHAR(255),
    purpose_of_visit VARCHAR(500) NOT NULL,
    host_employee_email VARCHAR(255) NOT NULL,
    id_proof_number VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE check_in_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    visitor_id BIGINT NOT NULL,
    check_in_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    check_out_time TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL,
    notifications_sent TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (visitor_id) REFERENCES visitors(id) ON DELETE CASCADE
);

CREATE INDEX idx_visitor_email ON visitors(email);
CREATE INDEX idx_check_in_status ON check_in_records(status);
CREATE INDEX idx_check_in_visitor ON check_in_records(visitor_id);

# --- !Downs

DROP TABLE IF EXISTS check_in_records;
DROP TABLE IF EXISTS visitors;
