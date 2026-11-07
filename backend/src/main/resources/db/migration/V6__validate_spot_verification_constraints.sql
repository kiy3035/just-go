-- 과거 버전에서 검수 근거 없이 VERIFIED였던 행은 삭제하지 않고 재검수 대기로 되돌립니다.
UPDATE spot
SET verification_status = 'PENDING'
WHERE verification_status = 'VERIFIED'
  AND (
      NULLIF(BTRIM(source_name), '') IS NULL
      OR NULLIF(BTRIM(source_reference), '') IS NULL
      OR NULLIF(BTRIM(usage_rights_note), '') IS NULL
      OR verified_at IS NULL
      OR theme_fit_score IS NULL
      OR theme_fit_score NOT BETWEEN 3 AND 5
      OR scenic_quality_score IS NULL
      OR scenic_quality_score NOT BETWEEN 3 AND 5
      OR route_suitability_score IS NULL
      OR route_suitability_score NOT BETWEEN 3 AND 5
      OR NULLIF(BTRIM(quality_review_note), '') IS NULL
  );

ALTER TABLE spot
    VALIDATE CONSTRAINT ck_spot_verified_provenance;

ALTER TABLE spot
    VALIDATE CONSTRAINT ck_spot_verified_quality;
