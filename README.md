# just go

`just go`는 출발지, 희망 주행 시간, 풍경 테마를 바탕으로 드라이브 코스를 추천하는 Pleos Connect 차량용 앱 프로젝트입니다.

현재 구성 범위는 `INITIAL_SETUP.md`의 0~3단계입니다. 백엔드와 로컬 PostgreSQL 개발 환경만 만들었으며 Android 프로젝트, Pleos SDK, 추천 엔진, 외부 경로 API는 아직 구현하지 않았습니다.

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
- 추천 API, 추천 알고리즘, 외부 자동차 경로 API
- 계정, 사용자 위치/주행 기록 저장
- 원격 저장소 작업, push, 클라우드 배포

개발용 Debug 빌드와 서명된 실행 가능한 공모전 제출용 Release APK는 별도 완료 기준입니다. 향후 제출 APK는 공식 규칙을 재확인한 뒤 `apk/팀명_앱이름_release.apk` 형태로 준비하며, 서명키는 Git에 저장하지 않습니다.
