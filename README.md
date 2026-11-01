# just go

`just go`는 출발지, 희망 주행 시간, 풍경 테마를 바탕으로 드라이브 코스를 추천하는 Pleos Connect 차량용 앱 프로젝트입니다.

초기 0~3단계 백엔드·인프라 구성 이후, Java REST 연결을 먼저 확인할 수 있는 개발용 웹 화면과 추천 요청 API 초안을 추가했습니다. Android 프로젝트와 Pleos SDK, 실제 추천 엔진, 외부 경로 API는 아직 구현하지 않았습니다.

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

Java 패키지 `com.example.justgo.backend`는 소유 도메인이 정해지기 전의 개발용 `PROVISIONAL` 네임스페이스입니다. Android `applicationId`를 정하거나 생성한 것은 아닙니다.

## 저장소 구조

```text
android-app/   Android 후속 작업을 위한 자리(프로젝트 미생성)
backend/       Spring Boot 백엔드
infra/         로컬 PostgreSQL Compose
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

### 브라우저 우선 개발 확인

외부 경로 API를 연결하기 전 화면과 Java REST 흐름만 확인하려면 `local,mock` 프로필로 실행합니다. `mock` 프로필은 실제 도로 경로가 아닌 명백한 예시값만 반환합니다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "local,mock"
$env:SERVER_PORT = "18080"
.\gradlew.bat bootRun
```

브라우저에서 `http://127.0.0.1:18080/`을 엽니다. 초기 좌표 `0, 0`은 실제 사용자 위치가 아닌 테스트 값입니다. 응답에 `mock: true`, `mode: "MOCK"`가 표시되며 실제 경로·예상 시간·거리로 사용하면 안 됩니다.

`mock` 프로필 없이 실행하면 같은 API는 `mode: "UNAVAILABLE"`과 빈 코스 목록을 반환합니다. 외부 경로 제공자를 확정하기 전에는 실제 추천 성공으로 간주하지 않습니다.

## 3. 로컬 인프라 종료와 데이터 초기화

컨테이너만 중지하고 DB 데이터를 유지하려면 다음을 실행합니다.

```powershell
cd ..\infra
docker compose --env-file .env down
```

아래 명령은 `postgres_data` 볼륨과 모든 로컬 개발 데이터를 삭제합니다. 필요한 데이터가 없는지 확인한 뒤에만 실행합니다.

```powershell
docker compose --env-file .env down -v
```

## 아직 하지 않은 작업

- Android 프로젝트 생성과 `assembleDebug`
- Pleos Emulator/NaviHelper 연동 및 검증
- 실제 추천 알고리즘과 외부 자동차 경로 API (`mock` 프로필의 예시는 실제 추천이 아님)
- 계정, 사용자 위치/주행 기록 저장
- 원격 저장소 작업, push, 클라우드 배포

개발용 Debug 빌드와 서명된 실행 가능한 공모전 제출용 Release APK는 별도 완료 기준입니다. 향후 제출 APK는 공식 규칙을 재확인한 뒤 `apk/팀명_앱이름_release.apk` 형태로 준비하며, 서명키는 Git에 저장하지 않습니다.
