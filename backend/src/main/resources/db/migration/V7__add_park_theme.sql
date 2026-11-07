ALTER TABLE spot DROP CONSTRAINT ck_spot_theme;
ALTER TABLE spot ADD CONSTRAINT ck_spot_theme
    CHECK (theme IN ('CITY', 'MOUNTAIN', 'RIVER', 'LAKE', 'SEA', 'PARK'));
