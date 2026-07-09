-- Seed default admin user
-- Legacy hash for 'password'; DefaultUserPasswordBootstrap migrates to admin123 on startup
INSERT INTO users (id, username, email, password, full_name, active, role, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'admin',
    'admin@roadeye.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'System Administrator',
    true,
    'SUPER_ADMIN',
    NOW(),
    NOW()
);

-- Seed default system operator
-- Legacy hash for 'password'; DefaultUserPasswordBootstrap migrates to operator123 on startup
INSERT INTO users (id, username, email, password, full_name, active, role, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'operator',
    'operator@roadeye.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'System Operator',
    true,
    'SURVEILLANCE_OPERATOR',
    NOW(),
    NOW()
);

-- Seed system configurations
INSERT INTO system_configurations (id, config_key, config_value, description, data_type, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'ai.vehicle.model', 'yolov8n', 'AI model used for vehicle detection', 'string', NOW(), NOW()),
    (gen_random_uuid(), 'ai.plate.model', 'yolov8n-plate', 'AI model used for license plate recognition', 'string', NOW(), NOW()),
    (gen_random_uuid(), 'ai.confidence.threshold', '0.65', 'Minimum confidence threshold for AI detections', 'double', NOW(), NOW()),
    (gen_random_uuid(), 'evidence.retention.days', '90', 'Number of days to retain evidence records', 'integer', NOW(), NOW()),
    (gen_random_uuid(), 'frame.retention.hours', '72', 'Number of hours to retain raw frames', 'integer', NOW(), NOW());
