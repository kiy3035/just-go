DELETE FROM spot
WHERE source_reference = 'https://www.data.go.kr/data/15021141/standard.do'
  AND source_name LIKE '공공데이터포털 전국관광지정보표준데이터 / %';

COMMENT ON TABLE spot IS
    '외부 원천의 자동 복제본이 아닌 수동 분류 보정·제외·fallback용 검수 장소';
