# 경관 후보 로컬 가져오기

> 용도: 수동 검수와 이용권한 확인을 끝낸 실제 후보를 각 개발자의 로컬 PostgreSQL에 등록
>
> 공개 관리 API가 아니며 `candidate-import` 프로필을 명시한 경우에만 실행됩니다.

## 1. 안전 원칙

- 실제 파일은 `backend/local-data/`에 두며 이 디렉터리는 Git과 Docker 빌드 컨텍스트에서 제외됩니다.
- 사용자 위치·이동 이력, 담당자 이름·연락처·계정 정보, API 키를 파일에 넣지 않습니다.
- 공공 장소 좌표라도 원본과 이용조건을 확인하기 전에는 `VERIFIED` 후보로 등록하지 않습니다.
- 한 번에 1~500건, 파일 크기 1MB 이하만 처리합니다.
- 전체 후보를 먼저 검증하고 한 트랜잭션으로 저장하므로 한 건이 잘못되면 일부만 저장하지 않습니다.
- 후보명·출처명·참조가 모두 같은 기존 행은 중복으로 건너뜁니다.

## 2. 로컬 JSON 작성

`backend/local-data/scenic-candidates.json` 파일을 직접 만들고 아래 구조를 사용합니다. 아래 값은 필드 구조를 설명하기 위한 실행 불가 자리표시자이며 실제 장소가 아닙니다.

```json
{
  "candidates": [
    {
      "name": "<검수한 공개 장소명>",
      "theme": "CITY | MOUNTAIN | RIVER | LAKE | SEA 중 하나",
      "latitude": "<WGS84 위도 숫자>",
      "longitude": "<WGS84 경도 숫자>",
      "sourceName": "<제공기관 또는 자료명>",
      "sourceReference": "<원본 URL 또는 문서 식별자>",
      "usageRightsNote": "<이용허락과 출처표시 검토 내용>",
      "verifiedAt": "<ISO-8601 검수 시각>",
      "themeFitScore": "<3~5 정수>",
      "scenicQualityScore": "<3~5 정수>",
      "routeSuitabilityScore": "<3~5 정수>",
      "qualityReviewNote": "<점수 근거와 접근 시 유의사항>"
    }
  ]
}
```

위도·경도와 점수는 문자열이 아니라 JSON 숫자로 입력합니다. 좌표는 WGS84 범위를 확인한 뒤 DB 정밀도인 소수점 여섯 자리로 반올림됩니다. 세 점수 중 하나라도 3점 미만이면 추천용 VERIFIED 후보로 가져오지 않습니다.

## 3. 실행

먼저 `infra/.env`와 `backend/.env`의 로컬 DB 값이 서로 맞는지 확인하고 PostgreSQL을 실행합니다. 실제 비밀번호 파일은 Git에 올리지 않습니다.

```powershell
cd C:\myProject\just-go\infra
docker compose --env-file .env up -d postgres

cd ..\backend
$env:SCENIC_CANDIDATE_IMPORT_FILE = (Resolve-Path .\local-data\scenic-candidates.json).Path
.\gradlew.bat bootRun --args="--spring.profiles.active=local,candidate-import"
Remove-Item Env:\SCENIC_CANDIDATE_IMPORT_FILE
```

성공 로그에는 신규 저장 건수와 중복 제외 건수만 표시됩니다. 파일 경로, 후보명, 좌표와 출처 원문은 로그에 남기지 않습니다.

## 4. 등록 후 확인

- 같은 명령을 다시 실행했을 때 같은 후보가 중복 제외되는지 확인합니다.
- `/api/v1/spots?theme=...` 조회는 출처·품질 기준을 모두 만족한 후보만 반환합니다.
- 실제 TMAP 호출 전에는 후보 등록 성공을 실제 자동차 경로 검증 성공으로 간주하지 않습니다.
- 팀에서 확정한 실제 후보를 저장소에 포함해야 한다면 원본 이용조건을 다시 확인하고 별도 Flyway 마이그레이션으로 검토합니다. 로컬 파일을 그대로 커밋하지 않습니다.
