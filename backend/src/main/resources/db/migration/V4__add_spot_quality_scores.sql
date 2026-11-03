ALTER TABLE spot
    ADD COLUMN theme_fit_score SMALLINT,
    ADD COLUMN scenic_quality_score SMALLINT,
    ADD COLUMN route_suitability_score SMALLINT,
    ADD COLUMN quality_review_note VARCHAR(500),
    ADD CONSTRAINT ck_spot_quality_score_range
        CHECK (
            (theme_fit_score IS NULL OR theme_fit_score BETWEEN 1 AND 5)
            AND (scenic_quality_score IS NULL OR scenic_quality_score BETWEEN 1 AND 5)
            AND (route_suitability_score IS NULL OR route_suitability_score BETWEEN 1 AND 5)
        ),
    ADD CONSTRAINT ck_spot_verified_quality
        CHECK (
            verification_status <> 'VERIFIED'
            OR (
                theme_fit_score IS NOT NULL
                AND theme_fit_score BETWEEN 3 AND 5
                AND scenic_quality_score IS NOT NULL
                AND scenic_quality_score BETWEEN 3 AND 5
                AND route_suitability_score IS NOT NULL
                AND route_suitability_score BETWEEN 3 AND 5
                AND NULLIF(BTRIM(quality_review_note), '') IS NOT NULL
            )
        ) NOT VALID;

COMMENT ON COLUMN spot.theme_fit_score IS '선택 테마가 주행 구간에서 드러나는 정도(1~5)';
COMMENT ON COLUMN spot.scenic_quality_score IS '풍경의 연속성과 시각적 특징에 대한 수동 평가(1~5)';
COMMENT ON COLUMN spot.route_suitability_score IS '일반 승용차 경유지로 활용하기 쉬운 정도(1~5, 안전 보증 아님)';
COMMENT ON COLUMN spot.quality_review_note IS '점수 근거와 검수 시 유의사항';

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
      AND verified_at IS NOT NULL
      AND theme_fit_score BETWEEN 3 AND 5
      AND scenic_quality_score BETWEEN 3 AND 5
      AND route_suitability_score BETWEEN 3 AND 5
      AND NULLIF(BTRIM(quality_review_note), '') IS NOT NULL;
