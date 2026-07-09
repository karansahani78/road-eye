CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    camera_id VARCHAR(255),
    incident_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(50) NOT NULL DEFAULT 'DETECTED',
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    description TEXT,
    detected_at TIMESTAMP WITH TIME ZONE,
    occurred_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    assigned_to VARCHAR(255),
    assigned_at TIMESTAMP WITH TIME ZONE,
    resolution_notes TEXT,
    source VARCHAR(100) DEFAULT 'AI_ENGINE',
    confidence DOUBLE PRECISION,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE vehicle_journeys (
    id UUID PRIMARY KEY,
    vehicle_identifier VARCHAR(255),
    identifier_type VARCHAR(50) NOT NULL DEFAULT 'PLATE',
    first_camera_id VARCHAR(255),
    last_camera_id VARCHAR(255),
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    camera_count INTEGER DEFAULT 1,
    observation_count INTEGER DEFAULT 1,
    total_distance_km DOUBLE PRECISION,
    average_speed_kmh DOUBLE PRECISION,
    route_geometry TEXT,
    completed BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE journey_segments (
    id UUID PRIMARY KEY,
    journey_id UUID NOT NULL REFERENCES vehicle_journeys(id),
    camera_id VARCHAR(255) NOT NULL,
    arrived_at TIMESTAMP WITH TIME ZONE NOT NULL,
    departed_at TIMESTAMP WITH TIME ZONE,
    direction VARCHAR(50),
    lane VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_incident_camera ON incidents(camera_id);
CREATE INDEX idx_incident_type ON incidents(incident_type);
CREATE INDEX idx_incident_status ON incidents(status);
CREATE INDEX idx_incident_severity ON incidents(severity);
CREATE INDEX idx_incident_detected ON incidents(detected_at);
CREATE INDEX idx_incident_camera_detected ON incidents(camera_id, detected_at);
CREATE INDEX idx_vj_identifier ON vehicle_journeys(vehicle_identifier);
CREATE INDEX idx_vj_identifier_type ON vehicle_journeys(identifier_type);
CREATE INDEX idx_vj_first_camera ON vehicle_journeys(first_camera_id);
CREATE INDEX idx_vj_completed ON vehicle_journeys(completed);
CREATE INDEX idx_js_journey ON journey_segments(journey_id);
CREATE INDEX idx_js_camera ON journey_segments(camera_id);
