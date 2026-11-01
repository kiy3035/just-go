ALTER TABLE spot
    ADD COLUMN latitude NUMERIC(9, 6),
    ADD COLUMN longitude NUMERIC(9, 6),
    ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_spot_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_spot_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_spot_verification_status
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'));

CREATE INDEX idx_spot_verified_theme
    ON spot (theme, id)
    WHERE verification_status = 'VERIFIED'
      AND enabled = TRUE
      AND latitude IS NOT NULL
      AND longitude IS NOT NULL;
