CREATE TABLE tmap_daily_call_usage (
    usage_date DATE PRIMARY KEY,
    used_calls SMALLINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tmap_daily_call_usage_count
        CHECK (used_calls BETWEEN 1 AND 100)
);

COMMENT ON TABLE tmap_daily_call_usage IS
    'TMAP Free 상한 보호용 날짜별 호출 예약 건수(키·좌표·경로·응답 미저장)';
COMMENT ON COLUMN tmap_daily_call_usage.usage_date IS
    'Asia/Seoul 기준 호출 예약 날짜';
COMMENT ON COLUMN tmap_daily_call_usage.used_calls IS
    '외부 요청 직전에 예약한 호출 건수';
