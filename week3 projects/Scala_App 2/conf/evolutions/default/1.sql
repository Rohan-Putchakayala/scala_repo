# --- !Ups

DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS rooms;

CREATE TABLE rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    location VARCHAR(255) NOT NULL,
    amenities TEXT,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    employee_email VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    notifications_sent TINYINT(1) NOT NULL DEFAULT 0,
    reminder_sent TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
);

CREATE INDEX idx_room_name ON rooms(name);
CREATE INDEX idx_room_active ON rooms(is_active);
CREATE INDEX idx_reservation_room ON reservations(room_id);
CREATE INDEX idx_reservation_status ON reservations(status);
CREATE INDEX idx_reservation_start_time ON reservations(start_time);
CREATE INDEX idx_reservation_end_time ON reservations(end_time);
CREATE INDEX idx_reservation_employee_email ON reservations(employee_email);

# --- !Downs

DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS rooms;
