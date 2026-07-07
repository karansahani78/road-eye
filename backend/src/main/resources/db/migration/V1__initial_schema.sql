CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    phone VARCHAR(20),
    active BOOLEAN DEFAULT true,
    role VARCHAR(50) NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(500) UNIQUE NOT NULL,
    user_id UUID REFERENCES users(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE cameras (
    id UUID PRIMARY KEY,
    camera_code VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    source_type VARCHAR(50) NOT NULL,
    source_url_encrypted TEXT,
    location VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    direction VARCHAR(50),
    processing_fps INTEGER DEFAULT 15,
    resolution VARCHAR(50),
    active BOOLEAN DEFAULT true,
    health_status VARCHAR(50) DEFAULT 'UNKNOWN',
    last_heartbeat TIMESTAMP WITH TIME ZONE,
    last_successful_frame TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE camera_calibrations (
    id UUID PRIMARY KEY,
    camera_id UUID UNIQUE REFERENCES cameras(id),
    calibration_version VARCHAR(50),
    calibration_data TEXT,
    reference_point1 VARCHAR(255),
    reference_point2 VARCHAR(255),
    known_distance DOUBLE PRECISION,
    perspective_transform TEXT,
    algorithm_version VARCHAR(100),
    calibrated_by VARCHAR(100),
    calibrated_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE camera_health_events (
    id UUID PRIMARY KEY,
    camera_id UUID REFERENCES cameras(id),
    status VARCHAR(50),
    message TEXT,
    error_type VARCHAR(100),
    processed_frames BIGINT DEFAULT 0,
    dropped_frames BIGINT DEFAULT 0,
    inference_latency_ms DOUBLE PRECISION,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE system_configurations (
    id UUID PRIMARY KEY,
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT,
    description TEXT,
    data_type VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor VARCHAR(100),
    action VARCHAR(255) NOT NULL,
    target_type VARCHAR(100),
    target_id VARCHAR(100),
    correlation_id VARCHAR(100),
    metadata TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_cameras_code ON cameras(camera_code);
CREATE INDEX idx_cameras_health ON cameras(health_status);
CREATE INDEX idx_camera_health_events_camera ON camera_health_events(camera_id);
CREATE INDEX idx_camera_health_events_recorded ON camera_health_events(recorded_at);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_target ON audit_logs(target_type, target_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);
CREATE INDEX idx_system_configs_key ON system_configurations(config_key);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
