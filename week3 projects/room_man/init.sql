-- Database initialization script for Room Management System
-- This script creates the necessary tables and inserts sample data

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS room_management;
USE room_management;

-- Drop tables if they exist (for clean setup)
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS rooms;

-- Create rooms table
CREATE TABLE rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    location VARCHAR(255) NOT NULL,
    amenities TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rooms_active (is_active),
    INDEX idx_rooms_capacity (capacity)
);

-- Create reservations table
CREATE TABLE reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    employee_email VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    purpose TEXT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status VARCHAR(50) DEFAULT 'RESERVED',
    notifications_sent BOOLEAN DEFAULT FALSE,
    reminder_sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (room_id) REFERENCES rooms(id),
    INDEX idx_reservations_room_time (room_id, start_time, end_time),
    INDEX idx_reservations_status (status),
    INDEX idx_reservations_start_time (start_time),
    INDEX idx_reservations_employee_email (employee_email),
    INDEX idx_reservations_reminder (reminder_sent, start_time),
    INDEX idx_reservations_notifications (notifications_sent)
);

-- Insert sample rooms
INSERT INTO rooms (name, capacity, location, amenities, is_active) VALUES
('Conference Room A', 12, 'Floor 1, East Wing', 'Projector, Whiteboard, Video Conferencing, Air Conditioning', TRUE),
('Conference Room B', 8, 'Floor 1, West Wing', 'TV Screen, Whiteboard, Phone Conference, Wi-Fi', TRUE),
('Meeting Room 101', 6, 'Floor 2, North Side', 'Whiteboard, Phone, Wi-Fi', TRUE),
('Meeting Room 102', 6, 'Floor 2, South Side', 'TV Screen, Whiteboard, Wi-Fi', TRUE),
('Executive Boardroom', 20, 'Floor 3, Center', 'Large Conference Table, Projector, Video Conferencing, Catering Setup', TRUE),
('Training Room Alpha', 25, 'Floor 2, Training Center', 'Projector, Individual Desks, Microphone System, Wi-Fi', TRUE),
('Training Room Beta', 15, 'Floor 2, Training Center', 'TV Screen, Flexible Seating, Whiteboard, Wi-Fi', TRUE),
('Huddle Room 1', 4, 'Floor 1, Dev Area', 'TV Screen, Comfortable Seating, Wi-Fi', TRUE),
('Huddle Room 2', 4, 'Floor 1, Dev Area', 'Whiteboard, Comfortable Seating, Wi-Fi', TRUE),
('Phone Booth 1', 2, 'Floor 1, Quiet Zone', 'Soundproof, Phone, Wi-Fi', TRUE),
('Phone Booth 2', 2, 'Floor 1, Quiet Zone', 'Soundproof, Phone, Wi-Fi', TRUE),
('Innovation Lab', 10, 'Floor 3, Innovation Wing', 'Smart Board, Flexible Furniture, High-Speed Wi-Fi, 3D Printer Access', TRUE);

-- Insert sample reservations (some historical, some upcoming)
INSERT INTO reservations (room_id, employee_name, employee_email, department, purpose, start_time, end_time, status, notifications_sent, reminder_sent) VALUES
-- Past reservations
(1, 'Alice Johnson', 'alice.johnson@company.com', 'Engineering', 'Sprint Planning', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 2 HOUR, 'COMPLETED', TRUE, TRUE),
(2, 'Bob Smith', 'bob.smith@company.com', 'Marketing', 'Campaign Review', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 1 HOUR, 'COMPLETED', TRUE, TRUE),
(3, 'Carol Davis', 'carol.davis@company.com', 'HR', 'Interview Session', DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR) + INTERVAL 1 HOUR, 'COMPLETED', TRUE, TRUE),

-- Current and upcoming reservations
(4, 'David Wilson', 'david.wilson@company.com', 'Sales', 'Client Presentation', NOW() + INTERVAL 1 HOUR, NOW() + INTERVAL 2 HOUR, 'RESERVED', FALSE, FALSE),
(5, 'Eva Martinez', 'eva.martinez@company.com', 'Executive', 'Board Meeting', NOW() + INTERVAL 3 HOUR, NOW() + INTERVAL 5 HOUR, 'RESERVED', FALSE, FALSE),
(6, 'Frank Brown', 'frank.brown@company.com', 'Engineering', 'Technical Training', NOW() + INTERVAL 1 DAY, NOW() + INTERVAL 1 DAY + INTERVAL 4 HOUR, 'RESERVED', FALSE, FALSE),
(7, 'Grace Lee', 'grace.lee@company.com', 'Product', 'Product Demo', NOW() + INTERVAL 2 DAY, NOW() + INTERVAL 2 DAY + INTERVAL 2 HOUR, 'RESERVED', FALSE, FALSE),
(1, 'Henry Taylor', 'henry.taylor@company.com', 'Engineering', 'Architecture Review', NOW() + INTERVAL 3 DAY, NOW() + INTERVAL 3 DAY + INTERVAL 3 HOUR, 'RESERVED', FALSE, FALSE),
(8, 'Ivy Chen', 'ivy.chen@company.com', 'Design', 'Design Critique', NOW() + INTERVAL 4 DAY, NOW() + INTERVAL 4 DAY + INTERVAL 1 HOUR, 'RESERVED', FALSE, FALSE),
(9, 'Jack Robinson', 'jack.robinson@company.com', 'Engineering', 'Code Review', NOW() + INTERVAL 5 DAY, NOW() + INTERVAL 5 DAY + INTERVAL 1 HOUR, 'RESERVED', FALSE, FALSE),

-- Weekend reservations
(12, 'Karen White', 'karen.white@company.com', 'Innovation', 'Hackathon Planning', DATE_ADD(CURDATE(), INTERVAL 7 - WEEKDAY(CURDATE()) + 5 DAY), DATE_ADD(CURDATE(), INTERVAL 7 - WEEKDAY(CURDATE()) + 5 DAY) + INTERVAL 6 HOUR, 'RESERVED', FALSE, FALSE),

-- Reservations for next week
(2, 'Liam Garcia', 'liam.garcia@company.com', 'Marketing', 'Weekly Standup', DATE_ADD(CURDATE(), INTERVAL 7 - WEEKDAY(CURDATE()) + 7 DAY) + INTERVAL 9 HOUR, DATE_ADD(CURDATE(), INTERVAL 7 - WEEKDAY(CURDATE()) + 7 DAY) + INTERVAL 10 HOUR, 'RESERVED', FALSE, FALSE),
(3, 'Mia Anderson', 'mia.anderson@company.com', 'HR', 'Performance Review', DATE_ADD(CURDATE(), INTERVAL 7 - WEEKDAY(CURDATE()) + 8 DAY) + INTERVAL 14 HOUR, DATE_ADD(CURDATE(), INTERVAL 7 - WEEKDAY(CURDATE()) + 8 DAY) + INTERVAL 15 HOUR, 'RESERVED', FALSE, FALSE);

-- Create indexes for better performance
CREATE INDEX idx_reservations_room_status ON reservations(room_id, status);
CREATE INDEX idx_reservations_time_range ON reservations(start_time, end_time);
CREATE INDEX idx_reservations_created_at ON reservations(created_at);

-- Create a view for active reservations with room details
CREATE VIEW active_reservations_with_rooms AS
SELECT
    r.id as reservation_id,
    r.employee_name,
    r.employee_email,
    r.department,
    r.purpose,
    r.start_time,
    r.end_time,
    r.status,
    r.created_at as reservation_created,
    rm.id as room_id,
    rm.name as room_name,
    rm.capacity,
    rm.location,
    rm.amenities
FROM reservations r
JOIN rooms rm ON r.room_id = rm.id
WHERE r.status IN ('RESERVED', 'CHECKED_IN')
  AND rm.is_active = TRUE
ORDER BY r.start_time;

-- Create a view for room utilization statistics
CREATE VIEW room_utilization_stats AS
SELECT
    rm.id as room_id,
    rm.name as room_name,
    rm.capacity,
    rm.location,
    COUNT(r.id) as total_reservations,
    COUNT(CASE WHEN r.status = 'COMPLETED' THEN 1 END) as completed_reservations,
    COUNT(CASE WHEN r.status = 'CANCELLED' THEN 1 END) as cancelled_reservations,
    COUNT(CASE WHEN r.status = 'NO_SHOW' THEN 1 END) as no_show_reservations,
    AVG(TIMESTAMPDIFF(MINUTE, r.start_time, r.end_time)) as avg_duration_minutes
FROM rooms rm
LEFT JOIN reservations r ON rm.id = r.room_id
WHERE rm.is_active = TRUE
GROUP BY rm.id, rm.name, rm.capacity, rm.location
ORDER BY total_reservations DESC;

-- Grant permissions to the application user
GRANT SELECT, INSERT, UPDATE, DELETE ON room_management.* TO 'roomuser'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON room_management.* TO 'roomuser'@'localhost';
FLUSH PRIVILEGES;

-- Insert some test data for different time zones and edge cases
INSERT INTO reservations (room_id, employee_name, employee_email, department, purpose, start_time, end_time, status, notifications_sent, reminder_sent) VALUES
-- Back-to-back meetings
(1, 'Test User 1', 'test1@company.com', 'Engineering', 'Team Sync', NOW() + INTERVAL 6 HOUR, NOW() + INTERVAL 7 HOUR, 'RESERVED', FALSE, FALSE),
(1, 'Test User 2', 'test2@company.com', 'Engineering', 'Follow-up Meeting', NOW() + INTERVAL 7 HOUR, NOW() + INTERVAL 8 HOUR, 'RESERVED', FALSE, FALSE),

-- Long meeting
(5, 'Test User 3', 'test3@company.com', 'Executive', 'All Hands Meeting', NOW() + INTERVAL 2 WEEK, NOW() + INTERVAL 2 WEEK + INTERVAL 4 HOUR, 'RESERVED', FALSE, FALSE),

-- Early morning meeting
(10, 'Early Bird', 'early.bird@company.com', 'Operations', 'Daily Standup', DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 8 HOUR, DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 8 HOUR + INTERVAL 30 MINUTE, 'RESERVED', FALSE, FALSE),

-- Late evening meeting
(11, 'Night Owl', 'night.owl@company.com', 'Engineering', 'Release Planning', DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 18 HOUR, DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 19 HOUR, 'RESERVED', FALSE, FALSE);

-- Show table creation summary
SELECT 'Database initialization completed successfully!' as status;
SELECT COUNT(*) as total_rooms FROM rooms;
SELECT COUNT(*) as total_reservations FROM reservations;
SELECT status, COUNT(*) as count FROM reservations GROUP BY status;
