# just go

`just go`는 출발지, 희망 주행 시간, 풍경 테마를 바탕으로 드라이브 코스를 추천하는 Pleos Connect 차량용 앱 프로젝트입니다.

초기 0~3단계 백엔드·인프라 구성 이후, 드라이브 조건을 고르는 반응형 웹 화면 시안과 추천 요청 API 초안을 추가했습니다. 외부 자동차 경로는 TMAP API Free로 결정했고 왕복·편도 Provider, 임시 ±15% 목표 시간 추천과 수동 경관 후보 품질 점수까지 구현했습니다. Android Java/XML 화면 골격도 저장소에 있으나 실제 API·Pleos 연동은 아직 구현하지 않았습니다. TMAP 실제 앱 키 호출, 실제 경관 데이터 검수와 경로 기반 순환 품질 평가는 아직 진행하지 않았습니다.

현재 완료·보류·차단 항목과 다음 작업은 [`docs/progress.md`](docs/progress.md)에서 확인합니다. 실제 실행 명령과 검증 이력은 [`docs/setup-report.md`](docs/setup-report.md)에 날짜순으로 기록합니다. 외부 경로 업체의 공식 가격·쿼터·제약 비교는 [`docs/routing-provider-comparison.md`](docs/routing-provider-comparison.md), 실제 경관 후보의 출처·이용권한 기준은 [`docs/scenic-data-policy.md`](docs/scenic-data-policy.md), 1~5점 품질 평가표는 [`docs/scenic-quality-rubric.md`](docs/scenic-quality-rubric.md)에 정리했습니다.

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

## 저장소 구조

```text
android-app/   Java/XML Android 화면 골격(실제 API·Pleos 연동 전)
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

서버를 실행하고 브라우저에서 `http://127.0.0.1:18080/`을 열면 왕복·편도, 30·60·120분 또는 직접 입력, 도시·산·바다·호수·강을 선택할 수 있습니다. 직접 입력은 현재 화면 시안에서만 10~240분 범위로 제한한 임시 기준입니다.

버튼을 누르면 선택한 조건만 요약합니다. 왕복·편도와 직접 입력은 공용 API 계약에 반영됐지만 현재 위치 입력이 없는 화면에서는 아직 백엔드로 전송하지 않으며, 실제 TMAP 경로나 추천 결과로 간주하면 안 됩니다.

### 추천 REST API 개발 확인

외부 경로 API를 연결하기 전 화면과 Java REST 흐름만 확인하려면 `local,mock` 프로필로 실행합니다. `mock` 프로필은 실제 도로 경로가 아닌 명백한 예시값만 반환합니다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "local,mock"
$env:SERVER_PORT = "18080"
.\gradlew.bat bootRun
```

추천 REST API 연결은 `POST /api/v1/routes/recommendations`를 별도로 호출해 확인합니다. 요청에는 `tripType`(`ROUND_TRIP` 또는 `ONE_WAY`)이 필수이며, `desiredMinutes`는 현재 임시 기준으로 10~240분 범위의 5분 단위입니다. `mock` 프로필 응답에는 `mock: true`, `mode: "MOCK"`가 표시되며 실제 경로·예상 시간·거리로 사용하면 안 됩니다.

`mock`과 `tmap` 프로필 없이 실행하면 같은 API는 `mode: "UNAVAILABLE"`과 빈 코스 목록을 반환합니다.

풍경 테마별 검수 완료 장소 후보는 `GET /api/v1/spots?theme=RIVER&limit=10`으로 확인합니다. 이 API는 DB에서 `VERIFIED`, 활성, 좌표 등록 조건을 모두 만족하는 장소만 반환합니다. 실제 장소 시드는 검수와 사용권 확인 전까지 저장소에 포함하지 않습니다.

추천 서비스는 같은 검수 조건의 장소 후보를 최대 20건 조회해 `DrivingRouteProvider` 요청에 전달합니다. `tmap` Provider는 무료 쿼터 보호를 위해 그중 앞의 최대 3건만 계산합니다. LIVE 결과는 희망 시간 ±15% 안에서 시간 차이·거리·경로 ID 순으로 정렬합니다. 맞는 후보가 없으면 가짜 코스 대신 실제 계산 후보 시간을 5분 단위로 맞춘 가까운 재검색 조건을 `suggestions`에 최대 3건 반환합니다. 각 LIVE 코스에는 Android가 내비 전달에 사용할 순서·역할·이름·WGS84 좌표의 `stops` 목록이 포함되고, MOCK은 가짜 좌표와 제안 없이 빈 목록을 반환합니다.

### TMAP Provider 준비

`tmap` 프로필은 TMAP Provider를 활성화합니다. 왕복은 검수 후보를 경유해 출발지로 복귀하는 다중 경유지 요청, 편도는 후보를 도착지로 사용하는 일반 자동차 경로 요청으로 구성됩니다. 프로필을 켠 상태에서 추천 API를 호출하면 외부 요청이 발생하므로 앱 키와 무료 쿼터를 확인해야 합니다.

백엔드는 실행 디렉터리의 `backend/.env`를 선택적으로 읽습니다. `.env.example`을 복사한 뒤 실제 로컬 DB 비밀번호와 TMAP 앱 키만 입력합니다. 실제 `.env`는 Git에서 제외되며 저장소에는 예시 파일만 둡니다.

```powershell
cd C:\myProject\just-go\backend
Copy-Item .env.example .env
# 메모장 또는 IDE에서 .env의 DB_PASSWORD와 TMAP_APP_KEY만 실제 로컬 값으로 변경
.\gradlew.bat bootRun --args="--spring.profiles.active=local,tmap"
```

`backend/.env`는 백엔드 전용이고 `infra/.env`는 PostgreSQL Docker Compose 전용이므로 서로 다른 파일입니다. 기본 connect timeout은 2초, read timeout은 5초이며 자동 재시도는 하지 않습니다. Free 다중 경유지 일 100건보다 낮은 로컬 상한 90건과 추천 요청당 후보 최대 3건 제한을 적용합니다. 키를 Git, 문서, 로그, Android 앱에 넣지 않습니다. 이 저장소에서는 아직 실제 앱 키 호출을 검증하지 않았습니다.

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

## 아직 하지 않은 작업

- Android 프로젝트의 `assembleDebug`, 단위 테스트, Lint 실제 검증
- Pleos Emulator/NaviHelper 연동 및 검증
- 실제 TMAP 앱 키를 사용한 외부 호출과 세부 경관·순환 품질 점수 (`mock` 프로필의 예시는 실제 추천이 아님)
- 계정, 사용자 위치/주행 기록 저장
- 클라우드 배포와 원격 HTTPS 백엔드

개발용 Debug 빌드와 서명된 실행 가능한 공모전 제출용 Release APK는 별도 완료 기준입니다. 향후 제출 APK는 공식 규칙을 재확인한 뒤 `apk/팀명_앱이름_release.apk` 형태로 준비하며, 서명키는 Git에 저장하지 않습니다.
