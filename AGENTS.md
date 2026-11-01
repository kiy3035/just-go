# AGENTS.md — just go 개발 하네스

> 적용 범위: 저장소 전체. Codex는 모든 작업 시작 전 이 문서와 해당 작업의 요구사항 문서를 읽는다.
> 상태: 2026-10-31 기준 확정 결정과 제안을 구분한 초기 버전.

## 1. 프로젝트 목적 및 참여자

- 공모전: 현대자동차그룹 2026 차량용 앱 개발 공모전.
- **확정 앱명·프로젝트명: `just go`** (표시명은 소문자와 공백 그대로 사용). 목적지를 정하지 않은 사용자에게 **출발지 + 희망 주행 시간 + 풍경 테마**로 드라이브 코스를 추천하는 Pleos Connect 차량용 Android 앱.
- 풍경: `CITY`, `MOUNTAIN`, `RIVER`, `LAKE`, `SEA` (도시·산·강·호수·바다).
- **김인영:** 프로젝트 전체 툴 설치와 개발환경 세팅을 총괄한다. 서버/백엔드, DB, 외부 경로 API, 추천 로직, 인프라를 담당한다.
- **정도혜:** UI/UX, 프론트엔드, Android(Java/XML) 앱 기능 구현을 중심으로 담당한다. 준비된 환경에서 Pleos SDK 연동, 에뮬레이터 활용, 앱 빌드를 수행한다.
- 공동: 기능 기획, API 계약, 통합 테스트, 경관 코스 검수, 공모전 제출물.
- **역할 경계:** JDK, Gradle, Docker, DB 도구뿐 아니라 Android Studio, Android SDK, Pleos Emulator 등 Android 측 도구의 설치와 공통 개발환경 구성도 김인영 담당이다. 정도혜는 준비된 Android 환경에서 화면, 사용자 경험, 프론트 로직과 앱 기능 구현에 집중한다.
- Codex는 별도 지시가 없으면 **김인영 담당 영역만 작업**한다. 정도혜 담당 영역은 수정하지 않으며, 공동 영역도 사용자가 명시적으로 요청한 경우에만 작업한다.
- 앱명·프로젝트명은 **`just go`로 확정**한다. Android `app_name` 및 README에 정확히 이 이름을 사용한다. 저장소 명칭은 `just-go`를 사용하고 기존 `origin` 원격 저장소를 유지한다. 별도 원격 저장소 생성은 사용자 명시 지시 없이는 실행하지 않는다. **Android package/applicationId 및 도메인은 미정**이다. 프로젝트 생성에 필요하면 `com.example.justgo`를 임시 개발용으로 사용하고 `PROVISIONAL`이라고 보고한다. 최종 Release/APK 제출 전 고유 applicationId의 사용 가능성을 확인한다. 임시 ID를 제출용 최종 ID로 간주하지 않는다.

## 2. 결정된 기술 및 금지 사항

| 영역 | 기준 | 상태 |
|---|---|---|
| 차량 플랫폼 | Android Automotive OS 기반 Pleos Connect | 확정 |
| Android 앱 언어 | **Java**, XML + ViewBinding 우선 | Java 확정 / UI 구조 권장 |
| Android 개발 | Android Studio + Pleos Connect Emulator | 필수 |
| Android SDK | API 34 환경, `minSdk >= 28`, Gradle 8 이상 | 공식 가이드 기준 |
| 차량 연동 | Pleos NaviHelper 우선; Vehicle SDK 후순위 | 우선순위 결정 |
| 백엔드 | Java 21, Spring Boot 3.5.x 계열 | 설계안 |
| DB 접근 | **MyBatis (Mapper XML 중심)** | 확정 — JPA 사용 금지 |
| RDB | PostgreSQL 17 | 설계안 |
| DB 스키마 버전 | Flyway | 설계안 |
| 빌드 | 각 모듈의 Gradle Wrapper | 설계안 |
| API | JSON 기반 REST, OpenAPI 문서 | 설계안 |
| 로컬 인프라 | Docker Compose + PostgreSQL | 설계안 |
| 배포 | **처음에는 두 사람 모두 로컬에서 개발** | 결정 |

- 사용자가 승인하지 않은 JPA, Hibernate ORM, MongoDB, Redis, Kafka, RabbitMQ, MSA, Kubernetes, API Gateway, AWS 리소스를 추가하지 않는다.
- 외부 경로 제공자는 **미정**이다. TMAP/카카오모빌리티/NAVER 중 하나를 코딩으로 확정하지 않는다. `DrivingRouteProvider`와 로컬 Mock/Fake 구현으로 교체 가능한 경계를 둔다.
- Pleos SDK 버전·Gradle 좌표·메서드·퍼미션은 반드시 공식 문서와 로컬에서 확인한다. **추측으로 코드를 만들어 성공한 것처럼 보고하지 않는다.**
- Android 앱과 백엔드의 Java 언어/바이트코드 수준은 별도로 다룬다. 백엔드의 Java 21 설정을 Android에 무조건 복사하지 않는다.
- **앱 목표 카테고리는 `Mobility Services`로 결정했다.** 다만 실제 Pleos 등록/심사에서 이 분류가 승인되는지는 검증 전이며, `AndroidManifest.xml`의 SDK 카테고리·퍼미션 선언과 동일한 개념이라고 단정하지 않는다. NaviHelper 사용만으로 Maps/Navigation 앱 또는 `APP_MAPS`/Distraction Optimized 선언을 임의로 추가하지 않는다.
- 주행 중 UI 허용 범위는 `CarUxRestrictions` 및 카테고리·지역 정책을 기준으로 결정한다. 기본 정책은 **정차 중 코스 탐색 → 사용자 동의하에 기존 차량 내비로 전달 → 주행 중에는 내비 사용**이다.

## 3. 참고 문서의 우선순위

1. 사용자와 합의한 확정 결정(이 문서의 명시적 확정 사항).
2. 공식 공모전·Pleos 최신 문서 및 SDK 실험 결과.
3. `docs/requirements.md`(제품 요구사항), `docs/architecture.md`(시스템 경계).
4. `INITIAL_SETUP.md`(초기 1회 실행 절차).

충돌이나 미확정 사항이 있으면 자동 결정하지 말고 **미결정 목록과 대안**으로 보고한다. 공식 문서 변경을 발견하면 문서 URL, 확인 날짜, 영향 범위를 기록한다.

## 4. 최초 작업과 후속 작업의 경계

- `INITIAL_SETUP.md` 작업에서는 **컴파일 가능한 골격, 로컬 DB, 빌드·테스트 명령, 환경변수 템플릿**까지만 만든다.
- 코스 추천 알고리즘, 실제 외부 지도 API 호출, 내비게이션 실제 SDK 호출, 계정·사용자 기록 기능은 **별도의 후속 지시가 있어야 구현**한다.
- **초기 `assembleDebug` 성공과 공모전 제출용 Release APK는 별개의 완료 기준**이다. 공모전 GitHub 선택 제출에는 서명되어 정상 실행되는 Release APK, 규정 파일명과 `apk/` 경로가 요구된다. 비밀 서명키는 Git에 저장하지 않는다. 현재 초기 세팅 작업에서는 Release 서명키를 생성·공유·커밋하지 않고 향후 체크리스트만 작성한다.
- 실제 SDK·API 키·개발자 계정에 접근할 수 없다면 stub과 인터페이스만 정의하고 `BLOCKED`/`NOT VERIFIED`로 표기한다.
- 출발지 좌표나 예상 주행 시간 등 실데이터로 오인될 가짜 반환값은 만들지 않는다. 명시된 `MOCK` 개발 모드에서만 예시값을 쓴다.

## 5. 아키텍처 / 코딩 규칙

### Backend
- 기능 단위 패키지: `route`, `spot`, `trip`, `integration`, `common`.
- 호출: Controller → Service → Mapper / 외부 Provider. Controller에는 비즈니스 로직을 두지 않는다.
- MyBatis는 Mapper Interface + XML을 기본으로 하며 DTO와 DB 모델을 구분한다.
- SQL 파라미터는 `#{}` 바인딩. `${}` 문자열 치환은 SQL 식별자 화이트리스트 검증 등 명확한 근거 없이는 금지.
- DB 변경은 `db/migration/V...__description.sql` 형식 Flyway 마이그레이션으로 관리; 이미 적용된 버전을 수정하지 않는다.
- 트랜잭션 경계는 서비스 계층; 외부 HTTP I/O를 DB 트랜잭션 내부에 오래 묶지 않는다.
- 시간/거리/좌표 단위를 명확히 정의(분, 미터, WGS84 위도·경도). DTO 검증과 경계값 테스트를 포함한다.
- 외부 API 호출에는 connect/read timeout, 오류 분류, 호출량 관리, 응답 검증을 둔다. 무한 재시도 금지.
- 로그는 `traceId` 기반. 인증 토큰, API 키, 사용자의 원시 위치·이동 이력은 로그에 남기지 않는다.

### Android
- Java + Android XML/ViewBinding 우선; Activity/Fragment에 네트워크 호출·추천 점수 계산 등 비즈니스 로직을 직접 넣지 않는다.
- ViewModel/Repository 및 Retrofit 등은 실제 호환 버전을 검증 후 선택한다.
- API 서버 URL은 `local`, `mock`, 추후 `remote` 프로파일로 분리하고 소스에 개인 IP·비밀키를 하드코딩하지 않는다.
- 차량 내비 전달 기능은 `NavigationGateway` 같은 추상화로 분리한다. 공식 NaviHelper 실제 연동은 문서 및 에뮬레이터 검증 전까지 미구현으로 표시한다.
- 운전 중 복잡한 터치·텍스트 입력을 제공하지 않는다. Android Automotive / Pleos 운행 제한 정책을 따른다.
- 실기기 차량이 없는 상태를 정상적인 개발 조건으로 취급한다. Pleos Emulator로 검증하고 별도 기록한다.
- Android 9+(API 28+)의 cleartext HTTP 기본 차단을 고려하여 로컬 HTTP 접근은 **debug 전용 Network Security Config 또는 debug 전용 설정**으로 한정한다. Release에 임의의 전역 `usesCleartextTraffic=true`를 남기지 않는다.
- Pleos 전용 에뮬레이터 실행을 확인하지 못했다면 일반 Android 에뮬레이터/Gradle 빌드 성공을 **Pleos 검증 성공**으로 표시하지 않는다.

## 6. 개인정보·보안·라이선스

- `.env`, API 토큰, 비밀번호, 키스토어, 개인 로컬 설정을 커밋하지 않는다. `.env.example`에는 가짜 예시만 둔다.
- 실명, 연락처, 계정 정보, 결제 정보, 정확한 위치·이동 이력 등 개인정보나 민감정보를 저장소에 커밋하거나 원격 저장소에 push하지 않는다. 테스트에는 실제 정보와 혼동되지 않는 명백한 가짜 값만 사용한다.
- 커밋·push 전에는 비밀값과 개인정보 포함 여부를 확인하고, 발견하면 외부 전송을 중지한 뒤 안전하게 제거하거나 사용자에게 보고한다.
- 테스트 사용자·임시 위치만 사용하고 상세 운행 궤적을 기본 수집하지 않는다.
- 실제 개인정보 수집/익명 계정/주행 기록의 영구 저장은 제품 요구사항과 보안 설계 확정 후 구현한다.
- 지도/길찾기 API의 저장·캐시·표시·재배포 라이선스를 확인하기 전 외부 응답을 DB에 영구 저장하지 않는다.
- 외부 API 키는 백엔드에만 보관한다. Android APK에 절대 포함하지 않는다.
- 유료 서비스 결제, 구독, 과금 계정·API 연동, 결제수단 등록, 비용이 발생할 수 있는 리소스 생성이 필요해지면 실행 전에 즉시 중지한다. 서비스명, 목적, 예상 비용, 무료 대안, 필요한 권한을 사용자에게 보고하고 **명시적 승인 후에만** 진행한다.

## 7. 협업 / Git 규칙

- 단일 GitHub 모노레포: `/android-app`, `/backend`, `/infra`, `/docs`.
- 별도 작업 브랜치를 만들지 않고 모든 작업을 `main` 브랜치에 직접 반영한다.
- 커밋 제목은 `<type>: <한글 요약>` 형식으로 작성한다. `type`과 선택적 `scope`는 영어 소문자를 사용하고, 실제 변경 요약은 한글로 작성한다.
- 허용하는 기본 `type`은 `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`, `perf`이다. 예: `feat: 경관 장소 조회 추가`, `docs: 커밋 규칙 명확화`.
- 커밋 본문 설명은 한글로 작성하고 주요 변경 내용과 실행한 검증 결과를 요약한다.
- 공용 API를 변경할 때 `docs/api-contract.yaml` 또는 해당 계약 문서를 먼저/함께 수정한다.
- 각자 로컬 DB 사용. Flyway와 테스트 시드로 구조·개발 예시를 맞춘다. DB 원격 공유는 요구하지 않는다.
- 각자 독립 개발은 GitHub + 로컬 Mock 또는 로컬 서버로 충분하지만 **심사 APK가 실제 온라인 추천을 수행하려면 심사 환경에서 접근 가능한 HTTPS 백엔드**가 필요하다. 클라우드 배포는 초기 과제가 아니며, 제출 전에 서버 배포 또는 기능 제한이 명시된 오프라인 데모 방식 중 하나를 결정한다.
- 작업 범위를 벗어난 파일의 포매팅·삭제·일괄 리팩터링 금지.
- 기능 구현 이후에는 관련 자동화 테스트 및 문서 업데이트를 포함한다.

## 8. 작업 완료 보고 형식

Codex는 모든 작업을 아래 순서로 보고한다.
1. **변경 파일 목록**과 주요 의도.
2. **실제로 실행한 검증 명령**과 통과/실패 결과.
3. **미실행/검증 불가 항목**(이유와 필요한 사용자 조치).
4. **새로 필요해진 결정 사항 / 외부 비용 / API 권한**.
5. **다음 작업 1~3개**.

작업 실행을 하지 못했다면 성공이라고 쓰지 않는다. 외부 API/SDK를 모의한 경우 반드시 `MOCK`라고 표시한다.

## 9. 최신 공식 링크

- 공모전: https://appdevchallenge.auto-hmg.io/#contest
- 참가 규정: https://appdevchallenge.auto-hmg.io/guide/rules
- 제출 안내: https://appdevchallenge.auto-hmg.io/guide/submission
- Pleos Emulator: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Pleos NaviHelper: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro
- 앱 카테고리: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category
- Maps/Navigation 심사 정책: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide
- 주행 규제: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- Pleos Emulator GPU 조건: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator
