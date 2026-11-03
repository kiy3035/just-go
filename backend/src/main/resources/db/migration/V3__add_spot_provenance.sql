ALTER TABLE spot
    ADD COLUMN source_name VARCHAR(200),
    ADD COLUMN source_reference VARCHAR(1000),
    ADD COLUMN usage_rights_note VARCHAR(500),
    ADD COLUMN verified_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT ck_spot_verified_provenance
        CHECK (
            verification_status <> 'VERIFIED'
            OR (
                NULLIF(BTRIM(source_name), '') IS NOT NULL
                AND NULLIF(BTRIM(source_reference), '') IS NOT NULL
                AND NULLIF(BTRIM(usage_rights_note), '') IS NOT NULL
                AND verified_at IS NOT NULL
            )
        ) NOT VALID;

COMMENT ON COLUMN spot.source_name IS '경관 후보 원본을 제공하거나 작성한 기관·자료명';
COMMENT ON COLUMN spot.source_reference IS '검수 당시 확인한 원본 URL 또는 문서 식별자';
COMMENT ON COLUMN spot.usage_rights_note IS '이용허락 범위와 출처표시 조건 검토 기록';
COMMENT ON COLUMN spot.verified_at IS '경관·좌표·이용권한 검수를 완료한 시각';

DROP INDEX idx_spot_verified_theme;

CREATE INDEX idx_spot_verified_theme
    ON spot (theme, id)
    WHERE verification_status = 'VERIFIED'
      AND enabled = TRUE
      AND latitude IS NOT NULL
      AND longitude IS NOT NULL
      AND NULLIF(BTRIM(source_name), '') IS NOT NULL
      AND NULLIF(BTRIM(source_reference), '') IS NOT NULL
      AND NULLIF(BTRIM(usage_rights_note), '') IS NOT NULL
      AND verified_at IS NOT NULL;
