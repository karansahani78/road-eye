ALTER TABLE cameras
    ADD COLUMN IF NOT EXISTS location_source VARCHAR(50),
    ADD COLUMN IF NOT EXISTS location_accuracy_meters DOUBLE PRECISION;
