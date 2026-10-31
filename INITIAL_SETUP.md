# INITIAL_SETUP.md — just go 최초 1회 초기 구성 실행 지시서

> 실행 대상: 프로젝트 저장소 루트. 실행 전 `AGENTS.md`, `docs/requirements.md`, `docs/architecture.md`를 순서대로 읽는다.
> 목적: 두 개발자(김인영 / 정도혜)가 각자의 Windows PC에서 **로컬로 실행 가능한 개발 골격**을 구축한다.
> 역할 기준: 프로젝트 전체 툴 설치와 개발환경 세팅은 김인영이 담당하고, 정도혜는 준비된 환경에서 UI/UX·프론트엔드·Android 앱 구현을 담당한다.
> **주의: 이 작업은 초기 세팅용이다. 코스 추천 등 비즈니스 기능을 개발하지 않는다.**

## 0. 사전 점검 — 담당 김인영 (실행 전 보고)

1. 현재 저장소의 모든 파일·기존 Git 상태를 확인하고, 이미 존재하는 코드나 설정을 덮어쓰지 않는다.
2. 환경에서 Java 21, Android Studio/Android SDK, Gradle, Docker Desktop, Git 사용 가능 여부를 확인한다. `gradlew`는 Gradle 전역 설치를 전제로 하지 않는다.
2-1. **Pleos 에뮬레이터 실행 사전 검증:** 개발 PC의 CPU 가상화 활성화 여부, Windows 하이퍼바이저 설정, GPU 모델·전용 VRAM·드라이버, 사용 가능 메모리와 저장 공간을 기록한다. 공식 Pleos 문서의 GPU 최소·권장 사양을 비교하고 통과/미확인/부족으로 표시한다. 기기 사양을 추측하지 않는다. Pleos AVD는 API 34, Emulator 2.1.0, Cold boot, RAM 4GB 기준으로 안내한다.
3. Android Studio, Pleos Emulator, 개발자 계정, 외부 경로 API 키가 없다면 **사용자가 직접 설치/신청해야 하는 항목**으로 보고하고 대체 구현/가짜 SDK 코드를 만들지 않는다.
4. GitHub 원격 저장소 생성·push·클라우드 배포·유료 서비스 가입은 **사용자 명시 지시 없이는 실행하지 않는다**.
5. 버전은 임의의 존재하지 않는 좌표를 넣지 말고 실제 저장소에서 해결 가능한 호환 조합을 선택한다. 선택 이유와 버전을 기록한다.

## 1. 목표 디렉터리 구조

```text
repo-root/
├── AGENTS.md
├── INITIAL_SETUP.md
├── README.md                 # Codex가 생성
├── .gitignore               # Codex가 생성/보강
├── .editorconfig            # Codex가 생성
├── android-app/             # Java / Android project
├── backend/                 # Java 21 / Spring Boot / MyBatis
├── infra/
│   ├── compose.yaml         # 개발용 PostgreSQL만
│   └── .env.example
└── docs/
    ├── requirements.md
    ├── architecture.md
    ├── api-contract.yaml    # 최소 빈/초안 계약; 외부 공개 API 아님
    └── setup-report.md      # Codex 실행·검증 결과
```

이미 동일 목적의 폴더/파일이 있으면 구조를 중복 생성하지 않고 현재 구성을 존중한다.

## 2. 백엔드 골격 — 담당 김인영

- Java 21, Spring Boot 3.5.x의 **실존하며 호환되는 패치 버전**, Gradle Wrapper.
- 필수 의존성: Spring Web, Validation, Actuator, MyBatis Spring Boot Starter(3.0.x 호환 계열), PostgreSQL JDBC, Flyway + PostgreSQL 지원 모듈, 테스트(JUnit 5 / Spring Boot Test).
- Swagger/OpenAPI와 Lombok은 초기 필수 아님. 적용한다면 호환성, 도입 이유를 기록한다.
- 기능별 패키지 루트를 만들되 Service/Mapper 구현은 꼭 필요한 검증용 코드로 한정한다.
- DB 의존성 및 설정을 `local` 프로파일로 분리한다. 환경 변수로 URL/계정/비밀번호를 공급한다.
- Flyway `V1__init.sql`로 최소 개발용 `spot` 테이블 하나를 만들 수 있다. 실제 제품 테이블 전체를 임의로 확정하지 않는다.
- 개발용 데이터가 필요하면 `local` 전용 시드로 명확히 구분한다.
- Actuator health 엔드포인트를 통해 서버가 기동됐는지 확인하고, 민감한 Actuator 정보는 노출하지 않는다.
- MyBatis XML Mapper의 기본 로딩과 파라미터 바인딩이 되는 소규모 통합 검증을 추가한다.
- API 응답은 JSON 계약을 기준으로 정렬하되 추천 API는 기능 요청 전까지 실제 구현하지 않는다.

## 3. 인프라 골격 — 담당 김인영

- `infra/compose.yaml`에 PostgreSQL 17 컨테이너와 **볼륨 기반 데이터 영속성**, healthcheck, 로컬 전용 포트 바인딩을 설정한다.
- 로컬 포트 충돌 시 `.env`로 호스트 포트를 바꿀 수 있게 한다.
- `.env.example`만 커밋한다. 실제 `.env` 및 DB 볼륨은 Git 대상에서 제외한다.
- `.env` 파일은 Docker Compose 치환용이며 **호스트에서 실행하는 Spring Boot 프로세스에 자동 주입되지 않는다.** 백엔드 로컬 실행 시 IntelliJ Run Configuration/PowerShell 사용자 환경변수/안전한 로컬 설정 중 실제 경로를 README에 안내한다. `127.0.0.1` 호스트 포트 매핑만 기본 허용한다.
- 비밀번호는 예시값이며 각 개발자 PC에서 변경하도록 README에 명시한다.
- 초기 단계에서는 AWS, EC2, Nginx, Caddy, Redis, Kafka, Kubernetes를 구축하지 않는다.
- 로컬 실행 명령과 DB 초기화/정리 시 데이터 삭제 위험을 명확히 문서화한다.

## 4. Android 골격 — 환경 세팅 김인영 / UI·UX·프론트 구현 정도혜

- **김인영:** Android Studio, Android SDK, JDK/Gradle 조합, Pleos Emulator/AVD 등 Android 개발 도구를 설치·구성하고 공통 프로젝트 빌드 환경을 준비한다. UI나 앱 기능 구현은 맡지 않는다.
- **정도혜:** 준비된 Android 환경에서 Java/XML 화면, ViewBinding, UI/UX, 프론트 로직, API 클라이언트와 Pleos 앱 기능을 구현한다.

- Android Studio 프로젝트는 **Java** 언어로 생성한다. XML/ViewBinding 우선, `minSdk >= 28`, `compileSdk=34`를 초기 기준으로 공식 Pleos 환경 호환성을 확인한다. 공식 퀵스타트는 Kotlin 예시이지만 개발 환경 안내는 Java도 허용한다.
- 앱 표시명은 **`just go`로 확정**한다(`strings.xml`의 `app_name`, README에 동일 표기). Android applicationId/namespace는 미정이므로 `com.example.justgo`를 **임시값**으로 허용한다. `docs/setup-report.md`와 README에 패키지 ID만 `PROVISIONAL` 표시하고 실제 Release 제출 전 고유 ID를 확정할 일로 남긴다.
- 목표 앱 카테고리는 `Mobility Services`로 결정한다. 다만 Pleos 공식 등록·심사 적합성은 검증 전이므로 Maps/Navigation 앱인 것처럼 `APP_MAPS`나 DO 허용 선언을 임의로 추가하지 않는다. 선언이 실제 Pleos 런처 실행에 필요한 경우 문서화된 범위만 적용하고 사용 목적·근거를 보고한다.
- Android Gradle Plugin/Gradle/JDK 조합은 호환성 검증 후 문서화한다.
- 홈 화면은 기능 없는 **정적 기본 화면 1개**만 만든다: 출발지, 30/60/90/120분, 도시/산/강/호수/바다, 추천 버튼(아직 서버 기능 호출하지 않음). 미구현 버튼은 사용자에게 미구현임을 알린다.
- `ApiClient`/`RouteRepository`, `NavigationGateway` 추상화의 최소 인터페이스 또는 패키지 구조만 준비한다.
- Android 앱의 `local`, `mock` API URL 설정 방법을 문서화하고, 민감값 및 개인 LAN IP 하드코딩을 금지한다. **Android cleartext 차단 정책**을 준수: 로컬 HTTP를 위해서는 debug 빌드 전용 명시적 허용만 사용하고 Release에는 HTTPS를 기본으로 한다.
- **NaviHelper 실제 호출 코드는 공식 SDK 좌표·메서드·권한이 확인되고 동작 검증될 때까지 넣지 않는다.** 별도의 PoC 체크리스트만 작성한다.
- **김인영:** Pleos Emulator 설치 절차와 환경 요구사항을 README에 안내하고 가능한 환경에서는 설치·기동을 검증한다. 설치를 실행하지 못한 경우 미검증으로 명시한다.
- **정도혜:** 기동이 확인된 Emulator에서 Android 화면과 프론트 기능의 실행 결과를 검증한다. 불가능하면 단순 Android 빌드 결과와 Pleos 미검증을 구분한다.

## 5. 개발자 간 독립 실행 전략

- **김인영:** 두 개발자에게 필요한 전체 툴 설치와 공통 개발환경 기준을 관리한다. 김인영 PC에서는 `infra` PostgreSQL + `backend` Spring Boot를 로컬 실행한다.
- **정도혜 PC:** 김인영이 준비한 Android Studio + Pleos Emulator 환경에서 UI/UX·프론트·Android 앱을 개발한다. 필요시 통합 확인 목적으로 같은 Git 저장소의 `backend`와 PostgreSQL을 실행할 수 있지만 서버·DB의 구현 및 소유 담당은 김인영이다.
- 통신은 기본적으로 **에뮬레이터 ↔ 해당 PC의 로컬 서버**. Android Emulator 일반 호스트 접근 주소 `10.0.2.2`는 Pleos Emulator에서 실제 확인 전까지 보장된 사실로 쓰지 않는다.
- 두 사람이 다른 네트워크에 있어도 각자의 로컬 서버와 샘플 데이터로 개발할 수 있어야 한다.
- 통합 테스트 시에는 같은 LAN, 승인된 VPN, 또는 추후 HTTPS 배포 중 하나를 선택한다. 공용 DB는 필요하지 않다.

## 6. 자동 검증 / 수동 검증

가능한 환경에서 **실제로 실행하고** 출력 결과를 `docs/setup-report.md`에 기록한다.

### 백엔드 검증

```powershell
cd infra
docker compose --env-file .env up -d
cd ..\backend
.\gradlew.bat clean test
.\gradlew.bat bootRun
```

- 명령은 README에 환경변수 준비·프로세스 종료 방법과 함께 보완한다.
- 서버 health URL과 PostgreSQL 접속 및 Flyway 적용 여부를 확인한다.
- Testcontainers 실행 가능 환경이면 DB 통합 테스트 추가를 권장하되, Docker 가용성 제약은 그대로 보고한다.

### Android 검증

```powershell
cd android-app
.\gradlew.bat assembleDebug
```

- `gradlew.bat` 위치가 다른 구조라면 실제 파일 기준으로 명령을 수정한다.
- Pleos Emulator가 구동 가능할 때만 설치·실행 및 화면 상태를 확인한다.
- SDK 로그인/라이선스/개발자 계정 의존으로 검증 불가하면 **불가 원인과 재현 절차**를 기록한다.

## 7. 완료 조건 (Definition of Done)

- [ ] 앱명·프로젝트명 `just go`, 사용자 결정 스택/역할이 README와 문서에 일치
- [ ] 두 모듈의 Gradle Wrapper와 명시 버전 존재
- [ ] PostgreSQL Compose와 Flyway 기본 마이그레이션 존재
- [ ] 백엔드 테스트가 실제 성공(불가능 시 근거 기록; 성공으로 계산하지 않음)
- [ ] Android 정적 화면 빌드 성공(불가능 시 근거 기록; 성공으로 계산하지 않음)
- [ ] `README.md`에 양쪽 로컬 실행·설정 방법 문서화
- [ ] `README.md` 또는 `docs/setup-report.md`에 `assembleDebug`(개발)와 **서명된 실행 가능 Release APK**(공모전 제출)가 별개임을 명시. 실제 제출 때 APK는 `apk/팀명_앱이름_release.apk`로 저장하고 소스·README와 함께 제출하며, 서명키는 Git에서 제외한다.
- [ ] `.gitignore`, `.env.example`, 비밀정보 비커밋 규칙 적용
- [ ] SDK/API 실제 연동은 미구현·미검증이라는 표기를 유지
- [ ] `docs/setup-report.md`에 변경 파일, 실행 명령, 결과, 미해결 이슈 기재
- [ ] 개발용 임시 앱 ID와 `Mobility Services` 목표 카테고리, 등록·심사 적합성 미검증 상태 보고
- [ ] Pleos Emulator 공식 GPU/가상화 요구사항 확인 결과 보고
- [ ] Debug용 HTTP 허용과 Release용 HTTPS 정책 분리 확인
- [ ] 서명된 Release APK 제작·테스트는 후속 작업으로 기록

**작업 종료:** 모든 초기 세팅 작업을 보고하고 멈춘다. 단, 환경 부재로 빌드하지 못했다면 체크리스트를 미완료로 남기고 현재 환경의 한계를 정확히 보고한다. 추천 엔진/지도 API/실제 NaviHelper 연동은 다음 지시 전까지 구현하지 않는다.
