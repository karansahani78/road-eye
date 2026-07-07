-- Geography
CREATE TABLE provinces (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE districts (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    province_id UUID NOT NULL REFERENCES provinces(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE municipalities (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    district_id UUID NOT NULL REFERENCES districts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Roads
CREATE TABLE roads (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(255) UNIQUE NOT NULL,
    road_type VARCHAR(100),
    province VARCHAR(255),
    district VARCHAR(255),
    length_km DOUBLE PRECISION,
    default_speed_limit INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE road_segments (
    id UUID PRIMARY KEY,
    road_id UUID NOT NULL REFERENCES roads(id),
    name VARCHAR(255),
    start_point VARCHAR(255),
    end_point VARCHAR(255),
    speed_limit INTEGER,
    length_meters DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE intersections (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(255) UNIQUE,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    has_traffic_signal BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE traffic_zones (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(255) UNIQUE,
    zone_type VARCHAR(100),
    speed_limit INTEGER,
    restricted BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE speed_limit_rules (
    id UUID PRIMARY KEY,
    camera_id VARCHAR(255),
    road_segment_id VARCHAR(255),
    speed_limit_kmh INTEGER NOT NULL,
    valid_from TIME,
    valid_to TIME,
    vehicle_category VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Vehicles
CREATE TABLE vehicle_observations (
    id UUID PRIMARY KEY,
    camera_id VARCHAR(255) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    track_id INTEGER,
    category VARCHAR(100),
    original_class_name VARCHAR(255),
    original_class_id INTEGER,
    confidence DOUBLE PRECISION,
    model_name VARCHAR(255),
    model_version VARCHAR(100),
    direction VARCHAR(50),
    bbox TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE vehicle_tracks (
    id UUID PRIMARY KEY,
    camera_id VARCHAR(255) NOT NULL,
    track_id INTEGER NOT NULL,
    first_seen TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen TIMESTAMP WITH TIME ZONE,
    category VARCHAR(100),
    direction VARCHAR(50),
    confidence DOUBLE PRECISION,
    lost BOOLEAN DEFAULT false,
    completed BOOLEAN DEFAULT false,
    trajectory TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Plates
CREATE TABLE plate_observations (
    id UUID PRIMARY KEY,
    camera_id VARCHAR(255) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    plate_text VARCHAR(255),
    normalized_plate VARCHAR(255),
    country VARCHAR(100),
    country_confidence DOUBLE PRECISION,
    ocr_confidence DOUBLE PRECISION,
    quality_score DOUBLE PRECISION,
    track_id INTEGER,
    bbox TEXT,
    alternatives TEXT,
    detector_version VARCHAR(100),
    ocr_model_version VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Speed
CREATE TABLE speed_measurements (
    id UUID PRIMARY KEY,
    camera_id VARCHAR(255) NOT NULL,
    measured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    track_id INTEGER,
    speed_kmh DOUBLE PRECISION,
    smoothed_speed_kmh DOUBLE PRECISION,
    confidence DOUBLE PRECISION,
    distance_m DOUBLE PRECISION,
    duration_ms DOUBLE PRECISION,
    calibration_version VARCHAR(100),
    algorithm_version VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Violations
CREATE TABLE violations (
    id UUID PRIMARY KEY,
    camera_id VARCHAR(255) NOT NULL,
    violated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    violation_type VARCHAR(50) NOT NULL,
    confidence DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL,
    vehicle_category VARCHAR(100),
    plate_text VARCHAR(255),
    normalized_plate VARCHAR(255),
    plate_country VARCHAR(100),
    speed_kmh DOUBLE PRECISION,
    speed_limit DOUBLE PRECISION,
    track_id INTEGER,
    detector_version VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE violation_evidence (
    id UUID PRIMARY KEY,
    violation_id VARCHAR(255) NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    file_path VARCHAR(500),
    bucket_name VARCHAR(255),
    object_key VARCHAR(500),
    file_size BIGINT,
    content_type VARCHAR(100),
    checksum VARCHAR(255),
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE violation_reviews (
    id UUID PRIMARY KEY,
    violation_id VARCHAR(255) NOT NULL,
    reviewer VARCHAR(255),
    previous_status VARCHAR(50),
    new_status VARCHAR(50),
    comment TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Alerts
CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    source VARCHAR(100),
    camera_id VARCHAR(255),
    message TEXT,
    acknowledged BOOLEAN DEFAULT false,
    acknowledged_by VARCHAR(255),
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved BOOLEAN DEFAULT false,
    resolved_at TIMESTAMP WITH TIME ZONE,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes
CREATE INDEX idx_provinces_code ON provinces(code);
CREATE INDEX idx_districts_code ON districts(code);
CREATE INDEX idx_districts_province ON districts(province_id);
CREATE INDEX idx_municipalities_code ON municipalities(code);
CREATE INDEX idx_municipalities_district ON municipalities(district_id);
CREATE INDEX idx_roads_code ON roads(code);
CREATE INDEX idx_road_segments_road ON road_segments(road_id);
CREATE INDEX idx_intersections_code ON intersections(code);
CREATE INDEX idx_traffic_zones_code ON traffic_zones(code);
CREATE INDEX idx_speed_limit_rules_camera ON speed_limit_rules(camera_id);
CREATE INDEX idx_vo_camera_timestamp ON vehicle_observations(camera_id, observed_at);
CREATE INDEX idx_vo_track_id ON vehicle_observations(track_id);
CREATE INDEX idx_vo_category ON vehicle_observations(category);
CREATE INDEX idx_vt_track_id ON vehicle_tracks(track_id);
CREATE INDEX idx_vt_camera ON vehicle_tracks(camera_id);
CREATE INDEX idx_po_plate ON plate_observations(normalized_plate);
CREATE INDEX idx_po_camera_timestamp ON plate_observations(camera_id, observed_at);
CREATE INDEX idx_po_country ON plate_observations(country);
CREATE INDEX idx_sm_track ON speed_measurements(track_id);
CREATE INDEX idx_sm_camera_timestamp ON speed_measurements(camera_id, measured_at);
CREATE INDEX idx_v_camera ON violations(camera_id);
CREATE INDEX idx_v_type ON violations(violation_type);
CREATE INDEX idx_v_status ON violations(status);
CREATE INDEX idx_v_timestamp ON violations(violated_at);
CREATE INDEX idx_v_camera_timestamp ON violations(camera_id, violated_at);
CREATE INDEX idx_ve_violation ON violation_evidence(violation_id);
CREATE INDEX idx_vr_violation ON violation_reviews(violation_id);
CREATE INDEX idx_alert_type ON alerts(type);
CREATE INDEX idx_alert_severity ON alerts(severity);
CREATE INDEX idx_alert_camera ON alerts(camera_id);
CREATE INDEX idx_alert_created ON alerts(created_at);
CREATE INDEX idx_alert_acknowledged ON alerts(acknowledged);
