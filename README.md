# just go

`just go`는 출발지, 희망 주행 시간, 풍경 테마를 바탕으로 드라이브 코스를 추천하는 Pleos Connect 차량용 앱 프로젝트입니다.

현재 백엔드는 검수 DB, 전국도시공원정보표준데이터 API와 한국관광공사 TourAPI를 합쳐 장소 후보를 제공합니다. 후보 선택 요청에서는 사용자가 고른 장소 한 곳만 TMAP API Free로 실제 자동차 경로를 계산하며, 토큰이 없는 기존 클라이언트를 위한 자동 후보 계산도 호환 경로로 유지합니다. Android Java/XML 앱은 Pleos Connect Emulator에서 현재 위치 조회, LIVE 추천 API 호출, 코스 선택과 NaviHelper 단일 목적지 안내 시작까지 확인했습니다. 후보 선택 화면 연동, NaviHelper 다중 경유지, 실주행 기반 경관·순환 품질 조정과 Release APK는 아직 남아 있습니다.

현재 완료·보류·차단 항목과 다음 작업은 [`docs/progress.md`](docs/progress.md)에서 확인합니다. 실제 실행 명령과 검증 이력은 [`docs/setup-report.md`](docs/setup-report.md)에 날짜순으로 기록합니다. 외부 경로 업체의 공식 가격·쿼터·제약 비교는 [`docs/routing-provider-comparison.md`](docs/routing-provider-comparison.md), 키·쿼터·공개 배포 보호 기준은 [`docs/security.md`](docs/security.md), 실제 경관 후보의 출처·이용권한 기준은 [`docs/scenic-data-policy.md`](docs/scenic-data-policy.md), 1~5점 품질 평가표는 [`docs/scenic-quality-rubric.md`](docs/scenic-quality-rubric.md), 로컬 후보 등록 절차는 [`docs/scenic-candidate-import.md`](docs/scenic-candidate-import.md), 왕복 선형 분석 기준은 [`docs/route-shape-quality.md`](docs/route-shape-quality.md)에 정리했습니다.

## 역할 분담

| 담당자 | 담당 영역 |
|---|---|
| 김인영 | 프로젝트 전체 툴 설치·개발환경 세팅, 서버/백엔드, DB, 외부 경로 API, 추천 로직, 인프라 |
| 정도혜 | UI/UX, 프론트엔드, Android Java/XML 앱 기능, 준비된 환경에서의 Pleos 연동·에뮬레이터 활용·앱 빌드 |
| 공동 | 기능 기획, API 계약, 통합 테스트, 경관 코스 검수, 공모전 제출물 |

Android Studio, Android SDK, Pleos Emulator를 포함한 도구 설치와 공통 개발환경 구성은 김인영이 담당합니다. 정도혜는 구성된 Android 환경에서 UI/UX와 프론트·Android 구현에 집중합니다. Codex는 별도 지시가 없으면 김인영 담당 영역만 작업합니다.

## 기술 구성

- Backend: Java 21, Spring Boot 3.5.16, Gradle 8.14.6 Wrapper
- Persistence: MyBatis Spring Boot Starter 3.0.5, Mapper XML
- Database: PostgreSQL 17.11, Flyway
- Local infrastructure: Docker Compose
- Test: JUnit 5, Spring Boot Test, Testcontainers PostgreSQL

Java 패키지 `com.example.justgo.backend`와 Android `applicationId`의 `com.example.justgo`는 소유 도메인이 정해지기 전의 개발용 `PROVISIONAL` 값입니다.

## 핵심 동작

1. Android가 WGS84 출발 좌표, 희망 주행 시간, 왕복·편도, 화면 테마를 백엔드에 전달합니다.
2. 백엔드는 희망 시간과 주행 방식으로 직선거리 상한을 계산하고 검수 DB와 활성화된 공공데이터 원천에서 최대 50개의 POI 후보를 합칩니다.
3. 후보명·좌표 중복을 제거하고 테마·랜드마크 우선순위·거리로 정렬합니다. `ANYWHERE`와 `WATER`는 한 테마가 목록을 독점하지 않도록 순환 방식으로 채웁니다.
4. 후보 조회 결과에는 30분 유효 `candidateToken`이 포함됩니다. 이 단계에서는 TMAP 자동차 경로 API를 호출하지 않습니다.
5. 사용자가 한 후보를 고르면 해당 토큰의 장소 한 곳만 TMAP으로 실제 시간·거리와 경로를 계산합니다. 토큰을 생략한 기존 요청은 검수·정렬 상위 후보의 단일 2건과 조합 1건까지 계산하는 호환 흐름을 사용합니다.
6. Android가 선택한 LIVE 결과의 경유지·목적지를 Pleos NaviHelper에 전달하고 이후 안내는 차량 내장 내비게이션이 담당합니다.

## 후보 데이터 원천

| 원천 | 사용 목적 | 현재 상태와 제한 |
|---|---|---|
| PostgreSQL `SPOT` | 출처·이용권한·좌표·품질 점수를 수동 검수한 기준 후보 | 모든 실제 테마에 사용하며 외부 원천과 이름이 겹치면 DB 후보를 우선합니다. |
| [전국도시공원정보표준데이터 API](https://www.data.go.kr/data/15012890/standard.do) | 생활권 공원 후보를 넓게 확보 | `PARK` 전용입니다. 전국 목록을 기본 6시간 메모리 캐시하고 출발지 직선거리로 필터링합니다. |
| [한국관광공사 국문 관광정보 서비스](https://www.data.go.kr/data/15101578/openapi.do) | 위치기반 관광 POI와 공식 관광 신분류 코드 제공 | `CITY`, `MOUNTAIN`, `RIVER`, `LAKE`, `SEA`, `PARK`에 사용합니다. 위치 조회 반경은 API 최대값인 20km이며 같은 좌표·반경은 기본 10분 캐시합니다. |
| TMAP 자동차 경로 API | 선택 후보 또는 기존 자동 후보의 실제 자동차 시간·거리 계산 | 후보 검색 원천이 아닙니다. 선택 토큰 요청은 한 장소만, 토큰 없는 호환 요청은 최대 3개 경로만 계산하며 PostgreSQL 일일 상한을 적용합니다. |
| TMAP 주변 POI API | 20km 밖 후보와 일반 생활 POI 보강 검토 | **미구현**입니다. 현재 README에서 POI는 위 DB·공공데이터·TourAPI를 정규화한 장소 후보를 뜻합니다. |

## 화면 테마 분류 기준

화면의 `ANYWHERE`와 `WATER`는 조회 조건입니다. 저장되거나 정규화되는 실제 후보 테마는 `CITY`, `MOUNTAIN`, `RIVER`, `LAKE`, `SEA`, `PARK`입니다. TourAPI는 장소명보다 공식 `lclsSystm2`·`lclsSystm3` 신분류 코드를 우선하며, 신분류가 없는 레거시 항목만 이름 키워드로 보완합니다.

| 화면 선택 | 내부 조회·분류 기준 | 주요 후보 원천과 정렬 |
|---|---|---|
| 어디든 `ANYWHERE` | 여섯 실제 테마 전체 | 검수 DB를 먼저 유지하고 외부 `CITY → MOUNTAIN → RIVER → LAKE → SEA → PARK` 큐를 순환해 최대 50건을 구성합니다. |
| 도시 `CITY` | 자연·공원 코드가 아닌 관광 콘텐츠. `VE01` 랜드마크, `VE07` 전시, `HS01·02·04` 역사·안보, `VE04·05·06` 도시문화·복합관광·공연을 우선 | 검수 DB + TourAPI. 랜드마크·박물관·기념관·갤러리·문화거리·공연시설을 일반 교육·레저시설보다 먼저 보여 줍니다. |
| 산 `MOUNTAIN` | `NA01` 산·숲·계곡, 습지 `NA0304`를 제외한 `NA03` 자연생태, 수목원·정원 `NA0407`을 제외한 `NA04` 자연공원, 공원·정원명이 아닌 `NA05` 기타 자연관광 | 검수 DB + TourAPI. 신분류가 없을 때만 산림·휴양림·계곡·고개·오름·숲 및 이름 끝의 산·봉을 보완 기준으로 사용합니다. |
| 바다 `SEA` | `NA0205~09` 섬·염전·항구/포구·해안·해변, `VE0108` 등대, 바다 계열 수상레저 | 검수 DB + TourAPI. 신분류가 없을 때 해수욕장·해변·해안·바다·항구·포구·등대·갯벌 등을 보완합니다. |
| 강/호수 `WATER` | `RIVER`와 `LAKE`를 함께 조회하고 두 큐를 번갈아 구성 | 강은 `NA0201`, 폭포 `NA0103`, 민물 수상레저 등을 사용합니다. 호수는 `NA0202~04` 호수·저수지·연못/늪, `NA0304` 습지, `VE0107` 댐을 사용합니다. |
| 공원 `PARK` | 도시공원 API의 모든 유효 공원 + TourAPI `VE02` 테마공원, `VE03` 도시공원, `NA0407` 수목원·정원, 공원·정원명 `NA05` | 검수 DB + 전국도시공원 API + TourAPI. 직선거리순으로 넓게 보여 주며 최종 선택은 사용자가 합니다. |

모든 후보는 좌표 유효성, 요청 테마, 희망 시간 기반 직선거리 상한을 다시 검사합니다. 직선거리는 후보 축소용일 뿐 실제 운전 거리로 표시하지 않으며, 최종 시간·거리는 선택 후 TMAP 결과만 사용합니다.

## 아키텍처

> 프로젝트 완료 후 최종 컴포넌트 구성, 데이터 흐름, Pleos NaviHelper 연동 범위를 반영한 아키텍처 이미지를 추가할 예정입니다. 현재 구현 경계와 세부 결정은 [`docs/architecture.md`](docs/architecture.md)를 기준으로 합니다.

## 저장소 구조

```text
android-app/   Java/XML Android 앱, LIVE API·Pleos NaviHelper 연동
backend/       Spring Boot 백엔드와 로컬 컨테이너 이미지
infra/         로컬 PostgreSQL 및 선택적 백엔드 Compose
docs/          요구사항, 아키텍처, API 초안, 설치 결과
```

## 사전 요구사항

- JDK 21 (`java -version`, `javac -version`으로 확인)
- Docker Desktop과 Docker Compose
- Git

전역 Gradle 설치는 필요하지 않습니다. `backend/gradlew.bat`가 지정된 Gradle 버전을 내려받아 사용합니다.

## 1. 로컬 PostgreSQL 실행

`infra/.env.example`을 실제 로컬 파일로 복사하고 비밀번호를 반드시 변경합니다. `.env`는 Git에서 제외됩니다.

```powershell
cd infra
Copy-Item .env.example .env
# .env의 POSTGRES_PASSWORD를 로컬 전용 비밀번호로 변경
docker compose --env-file .env up -d
docker compose --env-file .env ps
```

기본 호스트 포트는 `127.0.0.1:5432`입니다. 충돌하면 `.env`의 `POSTGRES_PORT`만 바꿉니다. 외부 인터페이스에는 바인딩하지 않습니다.

## 2. 백엔드 테스트 및 실행

`infra/.env`는 Docker Compose의 변수 치환에만 쓰이며 호스트의 Spring Boot 프로세스에는 자동 전달되지 않습니다. PowerShell 세션이나 IntelliJ Run Configuration에 별도로 설정합니다.

```powershell
cd ..\backend
$env:SPRING_PROFILES_ACTIVE = "local"
$env:DB_URL = "jdbc:postgresql://127.0.0.1:5432/justgo"
$env:DB_USERNAME = "justgo"
$env:DB_PASSWORD = "<infra/.env에 설정한 로컬 비밀번호>"
.\gradlew.bat clean test
.\gradlew.bat bootRun
```

DB 포트를 바꿨다면 `DB_URL`의 포트도 동일하게 바꿉니다. 서버의 기본 8080 포트가 충돌하면 실행 전에 `$env:SERVER_PORT = "18080"`처럼 사용 가능한 포트를 지정합니다. 기동 후 해당 포트의 `/actuator/health`에서 상태를 확인하고 서버는 `Ctrl+C`로 종료합니다.

### 웹 입력 화면 시안 확인

서버를 실행하고 브라우저에서 기본 `http://127.0.0.1:8080/`을 열면 왕복·편도, 30·60·120분 또는 직접 입력과 여섯 풍경 조건을 확인할 수 있습니다. `SERVER_PORT=18080`을 지정했다면 해당 포트로 접속합니다. 직접 입력은 현재 화면 시안에서만 10~240분 범위로 제한한 임시 기준입니다.

버튼을 누르면 선택한 조건만 요약합니다. 왕복·편도와 직접 입력은 공용 API 계약에 반영됐지만 현재 위치 입력이 없는 화면에서는 아직 백엔드로 전송하지 않으며, 실제 TMAP 경로나 추천 결과로 간주하면 안 됩니다.

### 추천 REST API 개발 확인

외부 경로 API를 연결하기 전 화면과 Java REST 흐름만 확인하려면 `local,mock` 프로필로 실행합니다. `mock` 프로필은 실제 도로 경로가 아닌 명백한 예시값만 반환합니다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "local,mock"
$env:SERVER_PORT = "18080"
.\gradlew.bat bootRun
```

추천 REST API 연결은 `POST /api/v1/routes/recommendations`를 별도로 호출해 확인합니다. 요청에는 `tripType`(`ROUND_TRIP` 또는 `ONE_WAY`)이 필수이며, `desiredMinutes`는 현재 임시 기준으로 10~240분 범위의 5분 단위입니다. 왕복 LIVE 결과의 `repeatedSegmentPercent`는 임시 선분 재통과 비율이며 편도·MOCK·형상 없는 결과는 `null`입니다. `mock` 프로필 응답에는 `mock: true`, `mode: "MOCK"`가 표시되며 실제 경로·예상 시간·거리로 사용하면 안 됩니다.

`mock`과 `tmap` 프로필 없이 실행하면 같은 API는 `mode: "UNAVAILABLE"`과 빈 코스 목록을 반환합니다.

`mock`과 `tmap`은 결과의 의미가 서로 다르므로 동시에 활성화할 수 없습니다. 둘을 함께 지정하면 서버가 시작 단계에서 실패해 MOCK 결과를 LIVE로 오인하지 않게 합니다.

풍경 테마별 검수 완료 장소 후보는 `GET /api/v1/spots?theme=RIVER&limit=10`으로 확인합니다. 이 API는 DB에서 `VERIFIED`, 활성, 좌표 등록 조건을 모두 만족하는 장소만 반환합니다. 실제 장소 시드는 검수와 사용권 확인 전까지 저장소에 포함하지 않습니다.

추천 서비스는 같은 검수 조건의 장소 후보를 최대 20건 조회해 `DrivingRouteProvider` 요청에 전달합니다. `tmap` Provider는 무료 쿼터 보호를 위해 품질 우선순위가 높은 후보 두 곳의 단일 경로 2건과 두 곳을 잇는 조합 경로 1건만 계산합니다. LIVE 결과는 희망 시간과 차이가 크더라도 숨기지 않고 시간 차이·왕복 중복률·거리·경로 ID 순으로 최대 3건을 반환합니다. 선택 풍경에 검수 후보가 없을 때만 후보가 있는 다른 풍경을 `suggestions`에 최대 3건 반환합니다. 각 LIVE 코스에는 Android가 내비 전달에 사용할 순서·역할·이름·WGS84 좌표의 `stops` 목록이 포함되고, MOCK은 가짜 좌표와 제안 없이 빈 목록을 반환합니다.

### 넓은 장소 후보 조회

`public-data` 프로필을 추가하면 `GET /api/v1/places/candidates`가 검수 DB, 전국도시공원정보표준데이터 API, 한국관광공사 TourAPI 위치기반 관광정보를 합쳐 최대 50건을 반환합니다. `PARK`는 공원 표준데이터를 우선 사용하고, TourAPI 콘텐츠는 공식 신분류 코드와 코드가 없는 항목의 명칭 키워드를 사용해 `CITY`, `MOUNTAIN`, `RIVER`, `LAKE`, `SEA`, `PARK`로 분류합니다. `CITY`는 랜드마크·전시·역사·문화거리·공연시설을 일반 시설보다 먼저 보여 줍니다. `ANYWHERE`는 전체 테마를 번갈아 채우고, `WATER`는 강과 호수를 번갈아 채워 한 테마가 목록을 독점하지 않게 합니다. 이 목록은 실제 자동차 경로가 아니라 사용자 선택 전 후보이며 `straightLineDistanceMeters`도 거리 축소·정렬용 직선거리입니다.

각 후보의 `candidateToken`은 서버 메모리에서 30분만 유지됩니다. 사용자가 고른 토큰을 `POST /api/v1/routes/recommendations`의 선택적 `candidateToken`에 넣으면 그 장소 한 곳만 TMAP으로 계산합니다. 후보 조회 자체는 TMAP 호출권을 쓰지 않으며, 토큰에 출발 위치를 저장하지 않습니다. 상세 요청·응답은 [`docs/api-contract.yaml`](docs/api-contract.yaml)을 기준으로 합니다.

### TMAP Provider 준비

`tmap` 프로필은 TMAP Provider를 활성화합니다. 왕복은 한두 곳의 검수 후보를 경유해 출발지로 복귀하고, 편도 조합은 첫 후보를 경유해 마지막 후보에서 종료합니다. 후보가 한 곳인 편도만 일반 자동차 경로 API를 사용하고, 나머지는 다중 경유지 API를 사용합니다. 프로필을 켠 상태에서 추천 API를 호출하면 외부 요청이 발생하므로 앱 키와 무료 쿼터를 확인해야 합니다.

백엔드는 실행 디렉터리의 `backend/.env`를 선택적으로 읽습니다. `.env.example`을 복사한 뒤 로컬 실행에는 DB 비밀번호, TMAP 앱 키, 공원 API와 TourAPI의 키·엔드포인트를 입력하고, 공개 `production` 실행에는 별도 추천 API 접근 토큰도 입력합니다. 실제 `.env`는 Git에서 제외되며 저장소에는 예시 파일만 둡니다. 공원 URL은 전체 엔드포인트와 기본 URL을 모두 허용하고, TourAPI URL도 `locationBasedList2` 포함 여부를 자동 정규화합니다.

최종 통합·화면 캡처는 김인영 PC 한 대에서 PostgreSQL, 백엔드, Android Studio와 Pleos Emulator를 모두 실행합니다. 정도혜는 프론트 코드만 Git으로 전달하며 DB 비밀번호, TMAP·공공데이터 키, `.env` 파일을 공유하지 않습니다. 같은 위치·주행 조건에서 테마만 바꾸는 TourAPI 결과는 원시 위치 대신 SHA-256 해시 키로 기본 10분간 메모리에서 재사용해 탭 전환 호출량을 줄입니다.

```powershell
cd C:\myProject\just-go\backend
Copy-Item .env.example .env
# 메모장 또는 IDE에서 .env의 DB·TMAP·공공데이터 값을 실제 로컬 값으로 변경
.\gradlew.bat bootRun --args="--spring.profiles.active=local,tmap,public-data"
```

`backend/.env`는 백엔드 전용이고 `infra/.env`는 PostgreSQL Docker Compose 전용이므로 서로 다른 파일입니다. 기본 connect timeout은 2초, read timeout은 5초이며 자동 재시도는 하지 않습니다. Free 다중 경유지 일 100건보다 낮은 로컬 상한 90건과 추천 요청당 경로 계산 최대 3건 제한을 적용합니다. 호출권은 외부 요청 직전에 PostgreSQL에 서울 날짜와 건수만 원자적으로 예약하므로 서버 재시작·동시 요청에도 상한을 유지합니다. 키·좌표·경로·응답은 이 사용량 테이블에 저장하지 않습니다. 키를 Git, 문서, 로그, Android 앱에 넣지 않습니다. 실제 일반 자동차·다중 경유지 요청의 LIVE 처리를 검증했습니다.

로컬 서버는 기본 `127.0.0.1`로만 열립니다. 공개 배포에서는 `production` 프로필을 추가하고 32자 이상의 무작위 `ROUTE_API_ACCESS_TOKEN`을 비밀 환경변수로 설정해야 하며, 없으면 서버가 시작되지 않습니다. 추천 요청은 `X-Just-Go-Client-Token` 헤더로 이 별도 토큰을 전달합니다. 기본 분당 4회·동시 2회 제한과 API 응답 캐시 차단이 적용됩니다. 자세한 생성·교체·프록시 설정은 [`docs/security.md`](docs/security.md)를 따릅니다.

2026-11-07 최초 요청은 앱에 연결된 상품이 없어 HTTP 403으로 거부됐습니다. 같은 앱에 `Free TMAP` 상품을 활성화한 뒤 기존 `appKey`로 일반 자동차 경로와 다중 경유지 왕복 경로가 LIVE 처리되는 것을 확인했습니다. 유료 플랜 전환이나 결제수단 등록은 사용자 승인 전에는 진행하지 않습니다.

권한이나 후보 데이터를 변경한 뒤에는 백엔드를 `local,tmap,public-data`로 실행하고 다음 스크립트로 공개 테스트 좌표의 LIVE 응답을 재검증합니다. 스크립트는 앱 키·지점명·좌표·원시 응답을 출력하지 않으며, LIVE 실행에는 쿼터 사용 확인 스위치가 필요합니다.

```powershell
cd C:\myProject\just-go\backend
.\scripts\Test-RouteRecommendation.ps1 `
  -BaseUrl http://127.0.0.1:18080 `
  -ExpectedMode LIVE `
  -TripType ROUND_TRIP `
  -Theme RIVER `
  -DesiredMinutes 60 `
  -ConfirmExternalCall
```

## 3. Docker Compose로 PostgreSQL과 백엔드 함께 실행

JDK를 호스트에 직접 설정하지 않고 전체 로컬 서버를 확인하려면 `app` 프로필을 사용합니다. 이 프로필은 `backend/Dockerfile`로 Java 21 백엔드 이미지를 빌드하고 PostgreSQL이 healthy가 된 뒤 서버를 시작합니다. TMAP 프로필과 앱 키는 활성화하지 않습니다.

```powershell
cd C:\myProject\just-go\infra
docker compose --env-file .env --profile app up --build -d --wait
docker compose --env-file .env --profile app ps
```

기본 확인 주소는 `http://127.0.0.1:18080/`과 `http://127.0.0.1:18080/actuator/health`입니다. 포트가 충돌하면 `infra/.env`의 `BACKEND_PORT`를 변경합니다. `--profile app`을 생략하면 기존처럼 PostgreSQL만 실행됩니다.

## 4. 로컬 인프라 종료와 데이터 초기화

컨테이너만 중지하고 DB 데이터를 유지하려면 다음을 실행합니다.

```powershell
cd C:\myProject\just-go\infra
docker compose --env-file .env --profile app down
```

아래 명령은 `postgres_data` 볼륨과 모든 로컬 개발 데이터를 삭제합니다. 필요한 데이터가 없는지 확인한 뒤에만 실행합니다.

```powershell
docker compose --env-file .env --profile app down -v
```

## 남은 작업

- Android 후보 목록과 `candidateToken` 선택 흐름 연동
- Pleos NaviHelper 최대 2개 다중 경유지 실제 검증
- TourAPI 20km 밖 산·바다·강/호수 후보 확장 원천 결정
- 실제 또는 에뮬레이터 주행 기반 경관·차량 접근성·왕복 중복률 검수
- 최종 아키텍처 이미지와 공모전 제출 문서 정리
- 고유 `applicationId`, Release 서명, 실행 가능한 제출 APK
- 심사 방식에 따라 원격 HTTPS 백엔드 또는 기능 제한 오프라인 데모 결정

개발용 Debug 빌드와 서명된 실행 가능한 공모전 제출용 Release APK는 별도 완료 기준입니다. 향후 제출 APK는 공식 규칙을 재확인한 뒤 `apk/팀명_앱이름_release.apk` 형태로 준비하며, 서명키는 Git에 저장하지 않습니다.
