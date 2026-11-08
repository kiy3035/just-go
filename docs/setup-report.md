# just go 초기 구성 실행 보고서

> 실행일: 2026-10-31 (Asia/Seoul)
> 실행 범위: `INITIAL_SETUP.md` 0~3단계만
> 상태: 백엔드/인프라 검증 완료, Android/Pleos 구현 미수행
> 역할 기준: 전체 툴 설치·개발환경 세팅과 서버/DB/인프라는 김인영, UI/UX·프론트·Android 앱 구현은 정도혜 담당

## 1. 변경 파일과 주요 의도

### 저장소 공통

- `README.md`: `just go`의 범위, 백엔드/DB 실행법, 환경변수 분리, 데이터 삭제 주의사항을 기록했습니다.
- `.gitignore`: Gradle 산출물, `.env`, 로컬 IDE 설정, 키스토어/APK 등을 제외했습니다.
- `.editorconfig`: UTF-8, LF, Java/Gradle 4칸, YAML 2칸 기준을 정했습니다.
- `docs/api-contract.yaml`: 아직 추천 API를 확정하지 않은 빈 OpenAPI 3.1 초안을 추가했습니다.
- `android-app/README.md`: Android 프로젝트를 생성하지 않았음을 명시한 자리표시 문서만 추가했습니다.

### Backend

- `backend/build.gradle`, `settings.gradle`: Java 21, Spring Boot 3.5.16, MyBatis Starter 3.0.5, Flyway, PostgreSQL JDBC, Actuator, Validation, JUnit 5/Testcontainers를 구성했습니다.
- `backend/gradlew*`, `backend/gradle/wrapper/*`: Gradle 8.14.6 Wrapper를 추가했습니다.
- `JustGoBackendApplication`: 애플리케이션 진입점입니다.
- `common/TraceIdFilter`: 안전한 `X-Trace-Id`를 MDC와 응답 헤더에 연결하고 요청 종료 후 정리합니다.
- `spot/ScenicTheme`, `SpotRecord`, `SpotMapper`: 확정 테마와 최소 DB 모델/Mapper만 만들었습니다.
- `route`, `trip`, `integration/routing`의 `package-info.java`: 기능 경계만 만들고 비즈니스 구현은 넣지 않았습니다.
- `application.yml`: MyBatis XML 위치, health만 노출하는 Actuator, traceId 로그 형식을 설정했습니다.
- `application-local.yml`: DB 접속을 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수로 분리했습니다.
- `mapper/spot/SpotMapper.xml`: `${}` 없이 `#{}`만 사용해 삽입/조회 SQL을 구성했습니다.
- `db/migration/V1__init.sql`: 확정된 다섯 풍경 테마만 검사하는 최소 `spot` 테이블을 추가했습니다.
- `SpotMapperIntegrationTest`: 실제 PostgreSQL 17.11 임시 컨테이너에서 Flyway V1, XML 로딩, 파라미터 바인딩, 결과 매핑을 함께 검증합니다.

중요 클래스와 메서드에는 동작과 책임을 설명하는 한글 JavaDoc/주석을 넣었습니다.

### Infrastructure

- `infra/compose.yaml`: PostgreSQL 17.11 Alpine, 이름 있는 볼륨, healthcheck, `127.0.0.1` 전용 포트 바인딩, 포트 환경변수를 구성했습니다.
- `infra/.env.example`: 가짜 개발 예시값만 제공합니다. 실제 `.env`는 만들거나 커밋하지 않았습니다.

## 2. 사전 점검 결과

### 도구

| 항목 | 확인 결과 | 판정 |
|---|---|---|
| Java/Javac | Temurin OpenJDK 21.0.12.1 | 통과 |
| Gradle 전역 설치 | 없음 | 정상: Wrapper 사용 |
| Gradle Wrapper | 8.14.6, Java 21에서 실행 확인 | 통과 |
| Docker Desktop | 4.97.0, Engine 29.9.2 | 통과 |
| Docker Compose | v5.6.1 | 통과 |
| Git | 2.53.0.windows.3 | 사용 가능 |
| Android Studio | `C:\Program Files\Android\Android Studio\bin\studio64.exe` 존재 | 설치 파일 확인 |
| Android SDK/adb/emulator | 기본 SDK 경로와 명령을 찾지 못함 | 미확인/사용자 설치 필요 |
| Pleos AVD | 발견되지 않음 | 미설치 또는 미확인 |

초기 점검에서는 `C:\myProject\just-go\just-go`에 Git 메타데이터가 중첩되어 있었습니다. 이후 사용자 지시에 따라 기존 이력과 `origin`을 보존한 채 `.git`과 `.gitattributes`를 실제 작업 루트 `C:\myProject\just-go`로 옮겨 저장소 루트를 바로잡았습니다. 현재 작업 파일은 루트 `main` 브랜치의 추적 대상입니다.

### Pleos Emulator 사전 조건

| 항목 | 실제 확인값 | 판정 |
|---|---|---|
| CPU | AMD Ryzen 7 8700G, 8코어/16스레드 | 기록 |
| 펌웨어 가상화 | `VirtualizationFirmwareEnabled=True` | 통과 |
| Windows 하이퍼바이저 | `HypervisorPresent=True` | 통과 |
| 세부 Windows 선택 기능 | 관리자 권한이 없어 개별 상태 조회 실패 | 미확인 |
| 시스템 메모리 | 총 31.12GB, 점검 시 가용 4.91GB | 총량 충분 / 현재 가용량 주의 |
| 저장 공간 | C: 총 929.74GB, 여유 751.34GB | 통과 |
| GPU | AMD Radeon 780M Graphics, 전용 VRAM 0.5GB, 드라이버 32.0.21042.62 | **공식 최소 미달** |

2026-10-31에 확인한 Pleos 공식 문서 기준:

- Emulator 환경: Android API 28 이상, Gradle 8.0 이상, Pleos Connect Emulator 2.1.0, AVD API 34, Cold boot, RAM 4GB.
- GPU 최소: NVIDIA RTX 3060 Laptop 6GB 이상 또는 AMD RX 6600M/RX 6700S 8GB 이상.
- GPU 권장: NVIDIA RTX 4060/5060 Laptop 8GB 이상 또는 AMD RX 7700S/RX 7600M XT 8GB급 이상.

현재 Radeon 780M 내장 GPU와 전용 VRAM 0.5GB는 위 최소 외장 GPU 기준을 충족하지 않습니다. 따라서 Pleos Emulator 설치/실행 성공을 기대하거나 성공으로 기록하지 않았습니다.

공식 근거:

- https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator

## 3. 선택 버전과 근거

| 구성 | 선택 버전 | 근거 |
|---|---|---|
| Spring Boot | 3.5.16 | 요구한 3.5.x의 공식 공개 패치이며 Java 17~25, Gradle 8.4+ 지원 |
| Java | 21 | 프로젝트 요구 버전이자 설치된 LTS JDK |
| Gradle | 8.14.6 | Spring Boot 지원 범위의 공개된 최신 8.x 패치, Java 21 실행 지원 |
| MyBatis Starter | 3.0.5 | 공식 3.0 계열이 Spring Boot 3.2~3.5/Java 17+ 지원, 3.0.5는 Boot 3.5 기준선 |
| PostgreSQL | 17.11 | 2026-10-31 기준 지원 중인 17 계열 최신 패치 |
| Testcontainers | 1.21.4 | Spring Boot 3.5.16 의존성 관리로 해결된 버전 |

Gradle 배포 ZIP은 공식 SHA-256 `7988ed071b2a07900e2ec715fca15c6ab72bce6db433af301fa7fa7e9407bd24`를 Wrapper 설정에 고정했습니다. Wrapper JAR은 Gradle 공식 GitHub `v8.14.6` 태그에서 받았고 실제 SHA-256은 `7D3A4AC4DE1C32B59BC6A4EB8ECB8E612CCD0CF1AE1E99F66902DA64DF296172`입니다.

## 4. 실제 실행한 검증과 결과

| 명령/검증 | 결과 |
|---|---|
| `java -version`, `javac -version` | 통과: 21.0.12.1 |
| `docker version`, `docker compose version` | 통과: Engine 29.9.2, Compose v5.6.1 |
| `backend\gradlew.bat --version` | 통과: Gradle 8.14.6, Launcher JVM 21.0.12.1 |
| `docker compose --env-file .env.example config` | 통과: 구성 렌더링, loopback 바인딩 확인 |
| `POSTGRES_PORT=55432`로 `docker compose ... up -d` | 통과: 기존 5432 충돌을 피해서 실행 |
| Compose `ps` 및 `psql SELECT version()` | 통과: healthy, PostgreSQL 17.11 확인 |
| `backend\gradlew.bat clean test --no-daemon` | **통과: BUILD SUCCESSFUL**, 1 test, 0 skipped/failures/errors |
| 첫 `bootRun` (기본 8080) | 실패: 기존 프로세스가 8080 사용 중. 기존 프로세스는 중지하지 않음 |
| `SERVER_PORT=18080`로 `bootRun` | 통과: PostgreSQL 17.11 연결, Flyway V1 검증/적용, 서버 시작 |
| `GET http://127.0.0.1:18080/actuator/health` | 통과: HTTP 200, `{"status":"UP"}` |
| health 요청의 `X-Trace-Id: setup-check-1` | 통과: 응답 헤더에서 동일 값 확인 |
| `flyway_schema_history` 조회 | 통과: version 1, description init, success=true |
| `backend\gradlew.bat build --no-daemon` | **통과: BUILD SUCCESSFUL**, 실행 JAR 생성 |
| JAR 내용 확인 | 진입점, V1 마이그레이션, MyBatis XML 포함 확인 |

생성 JAR: `backend/build/libs/just-go-backend-0.0.1-SNAPSHOT.jar` (검증 시 30,154,424 bytes)

검증 종료 후 Spring Boot 프로세스와 Compose 컨테이너/네트워크는 정상 종료했습니다. `just-go-local_postgres_data` 볼륨은 데이터 삭제를 피하기 위해 보존했습니다.

## 5. 미실행 또는 검증 불가 항목

- Android 프로젝트 생성, `assembleDebug`, Pleos SDK/NaviHelper: 이번 범위에서 명시적으로 제외했습니다.
- Pleos Emulator 설치/실행: Android SDK/Pleos AVD가 확인되지 않고 GPU 최소 사양에도 미달하여 수행하지 않았습니다.
- 외부 경로 API, 개발자 계정, API 키: 공급자 미정이며 후속 작업 전까지 신청/구현하지 않았습니다.
- 추천 API/추천 알고리즘/사용자 기록: 초기 골격 범위를 넘으므로 구현하지 않았습니다.
- Git 원격 저장소를 새로 만들지는 않았습니다. 기존 `origin`과 `main` 이력을 그대로 사용하도록 중첩 저장소 루트만 바로잡았습니다.
- 세부 Hyper-V/Virtual Machine Platform 기능 상태: 관리자 권한이 없어 개별 Windows 기능 조회는 못했지만 펌웨어 가상화 및 하이퍼바이저 존재는 확인했습니다.
- 테스트 실행 중 Mockito/Byte Buddy의 향후 JDK 동적 에이전트 제한 경고가 있었으나 Java 21에서 테스트 결과에는 영향을 주지 않았습니다.

## 6. 새로 필요한 결정, 비용, 권한

1. **백엔드 Java 패키지 네임스페이스:** `com.example.justgo.backend`는 `PROVISIONAL`입니다. 소유 도메인 결정 후 Release 전에 변경해야 합니다. Android applicationId는 이번 작업에서 생성하지 않았습니다.
2. **Pleos 검증 장비:** 현재 GPU는 공식 최소 미달입니다. 최소 사양 이상의 외장 GPU가 있는 PC가 필요하며 비용/장비 결정은 사용자 몫입니다.
3. **로컬 포트:** 현재 PC에서 5432와 8080이 사용 중입니다. 기존 서비스 소유자를 확인하거나 `.env`의 DB 포트와 `SERVER_PORT`를 변경해야 합니다.
4. 외부 API 비용·계정·권한은 이번 작업에서 새로 발생하지 않았습니다.

## 7. 다음 작업 후보(별도 지시 필요)

1. API 계약을 먼저 합의한 뒤 추천 기능의 백엔드 골격을 별도 작업으로 진행합니다.
2. 외부 제공자를 확정하지 않은 상태로 `DrivingRouteProvider` 인터페이스와 로컬 PoC 경계를 준비합니다.

## 8. 2026-11-01 후속 Java/Web 우선 작업

- Android/Pleos 환경 설정은 사용자 지시에 따라 일시 보류했습니다.
- `POST /api/v1/routes/recommendations` 계약과 Controller → Service → `DrivingRouteProvider` 경계를 추가했습니다.
- 일반 실행은 `UNAVAILABLE`과 빈 결과를 반환하며 존재하지 않는 코스를 만들지 않습니다.
- `mock` 프로필에서만 `mock: true`, `mode: MOCK`인 화면 연결 검증용 예시를 반환합니다. 이 값은 실제 도로·시간·거리가 아닙니다.
- Spring Boot가 제공하는 `/` 개발용 웹 화면에서 테스트 좌표와 조건으로 REST 연결을 확인할 수 있게 했습니다. 실제 개인정보·위치를 입력하거나 저장하지 않습니다.
- 외부 API 계정·키·유료 서비스는 사용하지 않았습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 7개 테스트 통과(수정 전 MVC 테스트의 누락 프로필을 확인·보완한 뒤 재실행).
- 별도 테스트 DB(`127.0.0.1:55433`)에서 `local,mock` 서버 기동: PostgreSQL 17.11 연결, Flyway V1 적용, `/` HTTP 200, `/actuator/health` UP 확인.
- `POST /api/v1/routes/recommendations`: HTTP 200, `mock: true`, `mode: MOCK`, `X-Trace-Id` 유지 확인. 45분 요청은 HTTP 400과 `UNSUPPORTED_DURATION` 확인.
- 첫 실제 기동은 기존 보존 볼륨의 비밀번호 불일치로 실패했고, 두 번째 시도는 `.env.example` 문자열을 잘못 입력해 실패했습니다. 기존 데이터는 삭제하지 않았고 별도 테스트 볼륨과 정확한 가짜 예시값으로 재검증했습니다.
- `.\gradlew.bat build --no-daemon`: 성공, 실행 JAR 생성 확인.

## 9. 2026-11-01 경관 장소 후보 조회 작업

- 기존 V1 마이그레이션은 수정하지 않고 V2에서 WGS84 위도·경도, 검수 상태, 활성 여부를 추가했습니다.
- 검수 상태는 `PENDING`, `VERIFIED`, `REJECTED`로 구분하며 추천 후보 조회는 `VERIFIED`·활성·좌표 존재 조건을 모두 적용합니다.
- `GET /api/v1/spots?theme={THEME}&limit={1..50}` API와 Controller → Service → MyBatis XML 구조를 추가했습니다.
- 실제 장소 시드와 외부 지도 응답은 추가하지 않았습니다. 향후 수동 검수와 라이선스 확인을 거쳐 별도 등록해야 합니다.
- 개발용 웹 화면에서 장소 후보 조회 API를 확인할 수 있게 했습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 12개 테스트 통과, 실패·오류 0개.
- `.\gradlew.bat build --no-daemon`: 성공, V2 마이그레이션과 웹 정적 파일이 포함된 실행 JAR 생성 확인.
- 보존된 PostgreSQL 17.11 테스트 DB에서 기존 V1 다음에 V2만 추가 적용되는 것을 확인했습니다. `flyway_schema_history`의 V1·V2 모두 `success=true`입니다.
- 실제 HTTP 검증: `/` 200 및 장소 조회 폼 포함, `GET /api/v1/spots?theme=RIVER&limit=10` 200과 빈 목록, 알 수 없는 테마와 `limit=51`은 각각 400 오류 계약을 확인했습니다.

## 10. 2026-11-01 진척 현황판 및 추천 후보 연결

- 완료·보류·차단·미착수 상태를 한눈에 보는 `docs/progress.md`를 추가하고 README에서 연결했습니다.
- `SpotCandidateQuery` 읽기 경계를 추가해 추천 계층이 MyBatis 구현에 직접 의존하지 않도록 했습니다.
- 추천 요청마다 같은 테마의 VERIFIED·활성·좌표 보유 장소를 최대 20건 조회하고, 최소 식별자·이름·WGS84 좌표만 `DrivingRouteProvider` 요청 모델로 전달합니다.
- 장소 조회 트랜잭션이 끝난 뒤 Provider를 호출하는 구조를 유지해 향후 외부 HTTP I/O 동안 DB 트랜잭션을 열어두지 않습니다.
- 외부 경로 업체·계정·API 키·유료 서비스는 연결하지 않았고, 일반 프로필의 실제 경로 결과는 계속 `UNAVAILABLE`입니다.
- 첫 `clean test` 시도는 샌드박스가 사용자 Gradle 캐시의 잠금 파일 접근을 거부해 중단됐습니다. 승인된 로컬 캐시 접근으로 같은 명령을 재실행해 전체 14개 테스트 통과, 실패·오류·건너뜀 0개를 확인했습니다.
- `.\gradlew.bat build --no-daemon`은 성공했고, 실행 JAR에 `SpotCandidateQuery`, `DrivingRouteWaypointCandidate`, `RouteRecommendationService` 클래스가 포함된 것을 확인했습니다.

## 11. 2026-11-01 외부 자동차 경로 API 공식 문서 조사

- TMAP, 카카오모빌리티, NAVER Cloud의 공식 기능·가격·무료 쿼터·다중 경유지·저장 제한과 사전 절차를 `docs/routing-provider-comparison.md`에 비교했습니다.
- TMAP Free는 자동차 경로 일 1,000건, 다중 경유지 30 일 100건이며 무료 한도 초과 시 자동 차단됩니다. 응답 데이터는 저장 후 24시간 이상 사용할 수 없어 원본 영구 저장 금지로 정리했습니다.
- 카카오모빌리티는 공개 쿼터와 가격이 있지만 자동차 길찾기 API가 공식 제휴 API 목록에 있고 사전 제휴 계약이 필요하므로 즉시 PoC 대상에서 제외했습니다.
- NAVER Cloud는 신규 Maps 상품만 검토해야 하며, 구형 AI NAVER API 지도 기능은 2026-11-24 종료 예정입니다. 신규 Maps의 무료 한도 초과 가격이 공개 페이지에서 확인되지 않아 별도 확인 항목으로 남겼습니다.
- 기술 적합성, 비용 통제, 가입 선행 조건을 비교해 TMAP Free를 첫 PoC 후보로 제안했지만 제공자를 확정하거나 코드에 벤더 구현을 추가하지 않았습니다.
- 계정 생성, 앱 키 발급, 결제수단 등록, 상품 신청, API 호출은 모두 실행하지 않았고 비용도 발생하지 않았습니다.

## 12. 2026-11-01 TMAP Free 클라이언트 기반 구현

- 사용자 결정에 따라 첫 외부 자동차 경로 PoC 제공자를 TMAP API Free로 확정하고 AGENTS·요구사항·아키텍처 문서를 갱신했습니다.
- 공식 다중 경유지 30 규격에 맞춰 `POST /tmap/routes/routeSequential30?version=1`, `appKey` 헤더, WGS84 X=경도/Y=위도, 출발시각, 승용차·교통최적 추천 옵션을 구성했습니다.
- connect timeout 2초, read timeout 5초, 자동 재시도 없음, 로컬 일일 기본 상한 90건을 적용했습니다.
- HTTP 401/403 인증, 429 쿼터, 기타 4xx 입력, 5xx 제공자, 네트워크, 잘못된 응답을 구분하며 원시 오류 본문·앱 키·좌표를 로그에 남기지 않습니다.
- TMAP의 `totalDistance`(미터)와 `totalTime`(초)을 양수로 검증하고 내부 요약 모델로 변환합니다. 원시 경로 응답은 저장하지 않습니다.
- 테스트는 실제 TMAP 호출 대신 명백한 가짜 좌표와 `TEST_APP_KEY_ONLY`를 사용한 모의 HTTP로 요청 형식, 헤더, 단위 변환, 429 분류, 로컬 상한, 잘못된 응답·빈 경유지 차단을 검증합니다.
- 출발지 복귀형 왕복 여부는 아직 제품 제안 상태라 자동 결정하지 않았습니다. 실제 추천 Provider 연결과 앱 키 호출은 해당 결정 및 사용자 키 발급 후 진행합니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 19개 테스트 통과, 실패·오류·건너뜀 0개. 첫 시도는 샌드박스의 Gradle 캐시 접근 제한으로 중단되어 승인된 캐시 접근으로 재실행했습니다.
- `.\gradlew.bat build --no-daemon`: 성공. 실행 JAR에 TMAP 클라이언트 클래스와 `application-tmap.yml`이 포함된 것을 확인했습니다.

## 13. 2026-11-01 드라이브 조건 웹 화면 시안

- Spring Boot 정적 루트 화면을 차량용 화면 방향의 반응형 HTML/CSS 시안으로 개편했습니다.
- 왕복·편도, 30·60·120분과 직접 입력, 도시·산·바다·호수·강을 키보드 접근 가능한 단일 선택 항목으로 구성했습니다.
- 직접 입력 선택 시에만 분 입력란이 나타나며 화면 시안의 임시 범위는 10~240분입니다.
- 버튼을 누르면 선택 내용만 요약하고, 실제 TMAP 추천 미연결 상태를 함께 표시합니다. 가짜 경로·시간·거리는 만들지 않습니다.
- 브라우저에서 편도·75분·바다를 선택해 `편도 · 75분 · 바다 풍경` 요약과 미연결 안내가 표시되는 것을 직접 확인했습니다.
- 왕복·편도와 직접 시간은 아직 `docs/api-contract.yaml` 요청 계약에 없으므로 이 화면에서 백엔드 API로 전송하지 않습니다.
- `WebPrototypeTest`를 추가해 핵심 선택지와 TMAP 미연결 안내 문구가 정적 화면에서 누락되지 않도록 했습니다.
- 색상 비교 후 최종 선택에 따라 전체 배경은 검정에 가까운 포레스트 차콜로 유지하고, 선택 상태는 편안한 세이지 그린(`#b9d97d`), 주요 버튼과 도착 핀은 물빛 청록(`#80b9aa`)으로 조정했습니다. 비교용 A~D 색상표는 제거했습니다.
- 최종 상태에서 `.\gradlew.bat clean test --no-daemon`을 실행해 전체 20개 테스트가 실패·오류·건너뜀 없이 통과했습니다.
- `.\gradlew.bat build --no-daemon`도 성공해 최종 정적 화면과 테스트가 실행 JAR에 포함되는 것을 확인했습니다.

## 14. 2026-11-01 왕복·편도 및 직접 시간 API 계약

- 추천 요청에 필수 `tripType`을 추가하고 `ROUND_TRIP`, `ONE_WAY` 두 값만 허용했습니다.
- 희망 시간은 웹 시안과 맞춘 임시 개발 기준으로 10~240분 범위의 5분 단위만 허용하도록 변경했습니다.
- 주행 방식은 Controller → Service → `DrivingRouteRequest` Provider 경계까지 전달하지만, 실제 출발·도착 좌표 구성과 TMAP 호출은 아직 수행하지 않습니다.
- `docs/api-contract.yaml`을 0.2.0 초안으로 갱신하고 요구사항·아키텍처·README·진척 현황을 함께 맞췄습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 23개 테스트 통과, 실패·오류·건너뜀 0개. 75분 편도 성공, 44분 거부, 주행 방식 누락 거부와 Provider 전달을 포함합니다.
- `.\gradlew.bat build --no-daemon`: 성공. 변경된 API DTO·Provider 요청 모델·정적 화면·계약 문서가 있는 상태에서 실행 JAR을 생성했습니다.

## 15. 2026-11-01 TMAP 추천 Provider 연결

- `tmap` 프로필에서만 활성화되는 `TmapDrivingRouteProvider`를 추가하고 기본 실행의 `UNAVAILABLE`, 개발용 `mock`의 `MOCK`과 분리했습니다.
- `ROUND_TRIP`은 검수 경관 후보를 경유지로 두고 출발 좌표로 복귀하며, `ONE_WAY`는 후보 좌표를 도착지로 사용합니다.
- 경유지가 있는 요청은 TMAP 다중 경유지 30 API, 없는 편도 요청은 일반 자동차 경로안내 API 형식으로 전송하도록 클라이언트를 확장했습니다.
- 무료 쿼터 보호를 위해 추천 요청당 앞의 후보 최대 3건만 계산합니다. 목표 시간 기반 제외·정렬과 경관 점수화는 아직 구현하지 않았습니다.
- TMAP의 초 단위 시간은 부족하게 표시하지 않도록 올림한 분 단위로 변환하고, 인증·쿼터 오류는 503, 그 밖의 외부 제공자 오류는 502로 안전하게 반환합니다.
- 실제 TMAP 호출 없이 가짜 좌표와 모의 HTTP 서버로 왕복·편도 요청 형식, 결과 변환, 최대 3건 제한, 후보 없음 처리를 검증했습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 29개 테스트 통과, 실패·오류·건너뜀 0개. TMAP 오류의 502/503 안전 변환도 포함합니다.
- `.\gradlew.bat build --no-daemon`: 성공. TMAP Provider, 단건·다중 경로 클라이언트와 최종 문서 상태로 실행 JAR을 생성했습니다.
- 실제 앱 키, 계정, 결제수단, 유료 서비스는 사용하지 않았고 외부 네트워크 요청도 실행하지 않았습니다. 실제 TMAP 응답 검증은 사용자 키 발급 후 별도 작업입니다.

## 16. 2026-11-01 목표 시간 기반 추천 정렬

- LIVE 자동차 경로에만 희망 시간 ±15% 임시 허용 오차를 적용하는 `RouteRecommendationPolicy`를 추가했습니다.
- 허용 범위 후보는 목표 시간 절대 차이, 거리, 경로 ID 순으로 정렬하고 최대 3건만 반환합니다. 정수 곱셈으로 비교해 부동소수점 경계 오차를 피했습니다.
- 60분 요청의 허용 경계 51분·69분은 포함하고 50분·70분은 제외하도록 테스트했습니다.
- 일치 후보가 없으면 실제로 존재하지 않는 대체 코스를 만들지 않고 LIVE 모드의 빈 목록과 시간·풍경 조건 변경 안내를 반환합니다.
- MOCK과 UNAVAILABLE 결과에는 LIVE 추천 정책을 적용하지 않습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 33개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 성공. 추천 정책과 갱신된 API 계약을 포함한 실행 JAR을 생성했습니다.
- 실제 TMAP 호출, API 키, 계정, 결제수단, 유료 서비스는 사용하지 않았습니다.

## 17. 2026-11-01 추천 경유지·도착지 API 계약

- Provider 후보와 외부 API DTO를 분리한 상태로 순서, `WAYPOINT`/`DESTINATION` 역할, 이름, WGS84 좌표를 가진 경로 지점 모델을 추가했습니다.
- 왕복 LIVE 코스는 검수 경관 후보를 1번 경유지로, 요청의 출발 좌표를 2번 복귀 목적지로 반환합니다. 편도는 검수 경관 후보를 1번 목적지로 반환합니다.
- 지점 순서와 WGS84 위도·경도 범위를 외부 전달 전에 검증하고, 응답 목록은 방어적으로 복사합니다.
- MOCK 응답은 실제 위치로 오인될 가짜 좌표를 만들지 않고 `stops: []`를 반환합니다.
- 첫 전체 테스트에서 현재 화면에서 이미 제거된 TMAP 미연결 문구를 검사하던 기존 `WebPrototypeTest` 1건이 실패했습니다. UI는 수정하지 않고 현재 화면의 코스 검색 버튼을 검사하도록 낡은 조건을 갱신했습니다.
- `.\gradlew.bat clean test --no-daemon`: 수정 후 전체 36개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 성공. 경로 지점 DTO와 OpenAPI 0.5.0 계약을 포함한 실행 JAR을 생성했습니다.
- 실제 TMAP 호출, API 키, 계정, 결제수단, 유료 서비스는 사용하지 않았습니다.

## 18. 2026-11-01 백엔드 로컬 `.env` 로딩

- Spring Boot 공식 Config Data import 방식으로 실행 디렉터리의 `backend/.env`를 properties 형식으로 선택 로드하도록 구성했습니다.
- 실제 `.env`는 기존 `.gitignore`의 `**/.env` 규칙으로 계속 제외하고, 저장소에는 가짜 자리표시자만 있는 `backend/.env.example`을 추가했습니다.
- `backend/.env`에는 백엔드 DB 접속값과 `TMAP_APP_KEY`를 두며, PostgreSQL Compose용 `infra/.env`와 용도를 분리했습니다.
- `git check-ignore -v --no-index backend/.env`: `.gitignore`의 `**/.env` 규칙 적용을 확인했습니다.
- 첫 빌드 명령은 Gradle Wrapper가 없는 저장소 루트에서 실행해 실패했고, `backend` 디렉터리에서 다시 실행했습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 36개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 백엔드 디렉터리에서 성공했습니다.
- 실제 앱 키·비밀번호 파일은 생성하거나 읽지 않았습니다.

## 19. 2026-11-03 백엔드 로컬 컨테이너 통합

- `backend/Dockerfile`에 Java 21 JDK 빌드 단계와 JRE 실행 단계를 분리한 이미지를 추가했습니다. 실행 단계는 `justgo` 비루트 사용자를 사용합니다.
- `backend/.dockerignore`에서 `.env`, Gradle 캐시, 빌드 결과와 로그를 빌드 컨텍스트에서 제외했습니다.
- `infra/compose.yaml`에 선택적 `app` 프로필을 추가했습니다. 기본 Compose 실행은 기존처럼 PostgreSQL만 올리고, `--profile app`을 지정할 때만 백엔드 이미지 빌드·기동과 healthcheck를 수행합니다.
- Compose 백엔드는 `local` 프로필만 활성화하며 TMAP 앱 키나 외부 호출을 요구하지 않습니다. PostgreSQL과 백엔드 포트는 모두 `127.0.0.1`에만 바인딩합니다.
- `docker compose --env-file .env.example --profile app config`: 통과. DB 서비스명 연결, loopback 포트와 healthcheck 렌더링을 확인했습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 36개 테스트 통과, 실패·오류·건너뜀 0개.
- `docker compose --env-file .env.example --profile app build backend`: 통과. `eclipse-temurin` Java 21 기반 `just-go-backend:local` 이미지를 생성했습니다.
- 별도 테스트 프로젝트와 가짜 전용 비밀번호, PostgreSQL 호스트 포트 55434, 백엔드 포트 18082로 `docker compose ... up --build -d --wait`를 실행했습니다. PostgreSQL과 백엔드가 모두 healthy 상태가 됐습니다.
- Flyway `flyway_schema_history`에서 V1·V2가 모두 `success=true`임을 확인했습니다. 호스트에서 `/`와 `/actuator/health`가 HTTP 200이고 컨테이너 내부 health 응답이 `{"status":"UP"}`임을 확인했습니다.
- 컨테이너의 실행 사용자는 `uid=100(justgo)`로 확인했습니다. 샌드박스에서 첫 호스트 HTTP 요청과 일부 Docker 파이프 접근이 거부되어 승인된 동일 검증을 다시 실행했고 통과했습니다.
- 검증 전용 컨테이너, 네트워크와 `just-go-container-check_postgres_data` 볼륨은 종료 후 삭제했습니다. 기존 `just-go-local_postgres_data` 볼륨은 건드리지 않았고 로컬 백엔드 이미지는 다음 실행을 위해 보존했습니다.
- 실제 `.env`, TMAP 앱 키, 실제 위치, 외부 유료 서비스는 사용하지 않았습니다.

## 20. 2026-11-03 결과 없음 대체 시간 제안

- LIVE 계산 결과는 존재하지만 희망 시간 ±15% 안에 코스가 없을 때, 가짜 코스 대신 실제 후보의 예상 시간을 근거로 가까운 재검색 조건을 최대 3건 반환하도록 했습니다.
- 후보 시간은 현재 API 입력 기준인 10~240분 범위만 사용하고 가장 가까운 5분 단위로 반올림합니다. 같은 시간과 중복 제안은 제거하고 목표 시간 차이가 작은 순으로 정렬합니다.
- `RouteConditionSuggestion`에는 대체 희망 시간, 기존 왕복·편도 방식, 기존 풍경 테마와 근거만 포함합니다. 좌표·예상 거리·가짜 경로는 포함하지 않습니다.
- MOCK, UNAVAILABLE, 정상 LIVE 추천에는 `suggestions: []`를 반환합니다. 실제 후보에서 만들 수 있는 지원 시간이 없을 때도 빈 제안과 일반 조건 변경 안내를 반환합니다.
- 첫 `clean test`는 삼항식의 빈 목록이 `List<Object>`로 추론되어 컴파일 오류 2건으로 실패했습니다. `List<RouteConditionSuggestion>` 타입을 명시한 뒤 동일 명령을 다시 실행했습니다.
- `.\gradlew.bat clean test --no-daemon`: 수정 후 전체 37개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 성공. OpenAPI 0.6.0 계약과 대체 조건 DTO를 포함한 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. 변경된 API를 포함한 `just-go-backend:local` 이미지를 다시 생성했습니다.
- 실제 TMAP 인증·외부 호출은 사용자 지시에 따라 실행하지 않았습니다.

## 21. 2026-11-03 경관 후보 출처·이용권한 검수

- 공공데이터포털의 전국관광지정보표준데이터, 공공데이터 이용정책과 공공누리 유형 안내를 공식 자료로 확인하고 `docs/scenic-data-policy.md`에 후보 사용 기준을 정리했습니다.
- 전국관광지 표준데이터는 위도·경도를 제공하고 메타데이터상 이용허락 제한 없음으로 표시되지만, 경관 주행 적합성·자동차 접근성·테마·좌표 최신성은 별도 수동 검수가 필요하다고 판단했습니다.
- Flyway V3에서 `source_name`, `source_reference`, `usage_rights_note`, `verified_at`을 추가했습니다. 새 VERIFIED 행은 이 값이 모두 없으면 DB가 거부합니다.
- 기존 V2 DB의 출처 없는 VERIFIED 행을 삭제하거나 임의 변경하지 않도록 제약을 `NOT VALID`로 추가했습니다. 대신 MyBatis 추천 후보 조회에서 출처 이력이 불완전한 행을 제외합니다.
- `SpotCandidateRegistration`으로 후보 등록 파라미터를 묶고 중요한 클래스·메서드에 한글 주석을 추가했습니다.
- 실제 장소·좌표·외부 API 키는 추가하지 않았고 테스트에는 `example.invalid`와 명백한 TEST 전용 값만 사용했습니다.
- V2까지만 적용한 PostgreSQL 17에 출처 없는 기존 VERIFIED 테스트 행을 만든 뒤 V3를 적용했습니다. 기존 행은 보존되고 새 출처 없는 VERIFIED 저장은 SQLSTATE `23514`로 거부되는 것을 확인했습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 39개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 성공. V3 마이그레이션을 포함한 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. V3가 포함된 로컬 백엔드 이미지를 생성했습니다.

## 22. 2026-11-03 경관 후보 품질 점수

- `docs/scenic-quality-rubric.md`에 테마 적합성, 풍경 품질, 경유지 적합성의 1~5점 수동 평가 기준을 추가했습니다.
- 임시 PoC 기준으로 세 항목이 모두 3점 이상이어야 VERIFIED로 저장할 수 있으며, 1~2점 후보는 PENDING 또는 REJECTED로 관리합니다.
- Flyway V4에 세 점수와 `quality_review_note`를 추가했습니다. 기존 V3 VERIFIED 행은 보존하되 품질 평가가 없으면 추천 조회에서 제외합니다.
- 후보는 세 점수의 동일 가중치 합계가 높은 순, 같은 점수면 DB 식별자 순으로 조회됩니다. 이 순서는 외부 경로 계산 후보 우선순위이며 최종 목표 시간 ±15% 정책을 대체하지 않습니다.
- `route_suitability_score`는 일반 승용차 경유지 사용 편의에 대한 검수값이며 실시간 안전·도로 통제·기상을 보증하지 않습니다.
- 실제 장소나 좌표는 추가하지 않았고 모든 테스트 값은 명백한 TEST 전용 값과 `example.invalid`만 사용했습니다.
- V3까지만 적용한 PostgreSQL 17에 출처가 있는 기존 VERIFIED 테스트 행을 만든 뒤 V4를 적용했습니다. 기존 행은 보존되고 새 품질 점수 없는 VERIFIED 저장과 3점 미만 점수가 있는 VERIFIED 저장은 SQLSTATE `23514`로 거부되는 것을 확인했습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 42개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 성공. V4 마이그레이션과 품질 점수 조회를 포함한 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. V4가 포함된 `just-go-backend:local` 이미지를 생성했습니다.
- 실제 TMAP 인증·외부 호출, 실제 위치 데이터, 결제수단이나 유료 서비스는 사용하지 않았습니다.

## 23. 2026-11-03 왕복 선분 중복률

- TMAP 공식 다중 경유지 샘플에서 응답 `features[].geometry`가 GeoJSON이며 `LineString` 좌표가 `[경도, 위도]` 배열임을 확인했습니다. 자동차 경로안내 문서에서는 `totalValue=1`이 전체 응답, `totalValue=2`가 거리·시간 등의 요약 응답임을 확인했습니다.
- 왕복 다중 경유지 응답의 LineString을 WGS84 소수점 여섯 자리 선분으로 정규화하고, 방향이 반대인 복귀 선분도 같은 선분으로 인식하도록 구현했습니다.
- 최초 통과 이후 재통과한 선분의 Haversine 거리 합계를 전체 분석 거리로 나눠 `repeatedSegmentPercent` 정수 백분율을 계산합니다. 원시 좌표는 DB·파일·로그에 저장하지 않습니다.
- 유효한 선형이 없는 응답과 편도·MOCK 결과는 0으로 추측하지 않고 `null`을 반환합니다. 희망 시간 차이가 같은 LIVE 후보에서는 중복률이 낮은 왕복을 거리보다 먼저 정렬합니다.
- 실제 중복률 상한은 임의로 두지 않았습니다. 실제 TMAP 응답 분포와 주행 검증 후 결정해야 합니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 46개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 성공. OpenAPI 0.7.0 계약과 선형 분석 구현을 포함한 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. 선형 분석 구현이 포함된 `just-go-backend:local` 이미지를 생성했습니다.
- 실제 TMAP 인증·외부 호출, 실제 위치 데이터, 결제수단이나 유료 서비스는 사용하지 않았습니다. GeoJSON 파싱 검증은 명백한 가짜 좌표를 사용한 MOCK HTTP 응답입니다.

## 24. 2026-11-03 검수 후보 기반 대체 풍경 제안

- 선택한 풍경에 검수 완료 후보가 없을 때만 DB에서 사용 가능한 다른 풍경을 조회해 최대 3개 재검색 조건으로 반환하도록 했습니다.
- 대체 풍경 조회에도 활성 상태, WGS84 좌표, 출처·이용권한·검수 시각, 세 품질 점수 3점 이상과 검수 근거 조건을 동일하게 적용했습니다.
- 제안 순서는 확정 테마 표시 순서로 고정하고 선택한 테마와 중복값을 제외합니다. 후보 존재만 확인하므로 실제 시간·경로 성공을 보장하지 않는다는 안내를 함께 반환합니다.
- 시간 범위가 맞지 않는 LIVE 결과는 기존 실제 후보 기반 대체 시간 제안을 유지하며, 개발용 MOCK 결과에는 대체 풍경 로직을 적용하지 않습니다.
- 실제 장소·좌표·TMAP 인증·외부 호출은 추가하지 않았습니다. 테스트 값은 명백한 TEST 전용 값만 사용했습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 49개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 성공. OpenAPI 0.8.0 계약과 대체 풍경 조회를 포함한 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. 대체 풍경 제안이 포함된 `just-go-backend:local` 이미지를 생성했습니다.

## 25. 2026-11-04 경관 후보 로컬 가져오기

- 공개 관리 API 없이 `candidate-import` 프로필에서만 로컬 JSON 후보를 PostgreSQL에 등록하도록 구성했습니다.
- 실제 파일은 Git과 Docker 빌드 컨텍스트에서 제외되는 `backend/local-data/`에 두며, 파일 경로·후보명·좌표·출처 원문은 로그에 남기지 않고 신규·중복 건수만 기록합니다.
- 1MB·500건 상한, 알 수 없는 JSON 필드 거부, DB 문자열 길이, WGS84 좌표 범위와 소수점 여섯 자리 정규화, 출처 이력과 세 품질 점수 3~5점을 검증합니다.
- 파일 전체를 DB 작업 전에 검증하고 한 트랜잭션으로 저장합니다. 후보명·출처명·참조가 같은 기존 행은 재실행 시 중복으로 건너뜁니다.
- PostgreSQL 17 Testcontainers에서 명백한 TEST 전용 JSON을 실제 `candidate-import` 프로필로 읽고 VERIFIED 추천 조회까지 연결되는 것을 확인했습니다.
- 실제 장소·개인 위치·TMAP 앱 키·외부 호출은 사용하지 않았습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 55개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 성공. 후보 가져오기 프로필을 포함한 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. 후보 가져오기 구현이 포함된 `just-go-backend:local` 이미지를 생성했습니다.

## 26. 2026-11-04 출발지 기반 후보 거리 필터

- TMAP 호출 전에 희망 시간과 왕복·편도 방식으로 후보 직선거리 상한을 계산하도록 했습니다. 임시 상한 속도는 실제 제한속도 판정이 아닌 호출 전 여유 필터로 150km/h를 사용합니다.
- 편도는 희망 시간 전체, 왕복은 출발지 복귀를 고려해 절반 시간에 해당하는 직선거리를 상한으로 사용합니다. 60분 기준 편도 150km, 왕복 75km입니다.
- PostgreSQL 17의 Haversine 계산으로 출발지 반경 밖 후보를 제외하고, 범위 안에서는 기존 품질 합계·직선거리·식별자 순으로 최대 20건을 Provider 경계에 전달합니다.
- 실제 자동차 경로 시간은 직선거리로 확정하지 않으며 기존 TMAP 결과의 목표 시간 ±15% 정책으로 최종 판정합니다.
- 출발 좌표와 계산 거리는 DB에 저장하거나 로그로 남기지 않습니다. 테스트에는 0도 부근의 명백한 가짜 좌표만 사용했습니다.
- 실제 TMAP 인증·외부 호출과 실제 장소 데이터는 사용하지 않았습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 60개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 성공. 후보 거리 필터를 포함한 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. 후보 거리 필터가 포함된 `just-go-backend:local` 이미지를 생성했습니다.

## 27. 2026-11-04 경관 후보 저장 없는 사전 검증

- `candidate-import` 프로필에 `SCENIC_CANDIDATE_IMPORT_DRY_RUN=true` 옵션을 추가해 JSON 전체 형식과 현재 DB 중복을 확인하되 INSERT는 실행하지 않도록 했습니다.
- 사전 검증 결과는 전체, 등록 가능, DB 중복, 파일 내 중복 건수만 로그로 남기며 후보명·좌표·출처·파일 경로는 출력하지 않습니다.
- 같은 이름·출처·참조 조합이 파일 안에 반복되면 한 번만 DB 중복을 조회하고 별도의 파일 내 중복 건수로 집계합니다. 실제 등록에서도 같은 입력 중복을 건너뜁니다.
- PostgreSQL 17 통합 테스트에서 사전 검증 전후 동일성 조회 결과가 0건으로 유지되어 DB에 저장되지 않는 것을 확인했습니다.
- 실제 장소·개인 위치·TMAP 앱 키·외부 호출·유료 서비스는 사용하지 않았습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 64개 테스트 통과, 실패·오류·건너뜀 0개.
- `.\gradlew.bat build --no-daemon`: 성공. dry-run 검증 모드를 포함한 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. dry-run 검증 모드가 포함된 `just-go-backend:local` 이미지를 생성했습니다.

## 28. 2026-11-04 TMAP 호출 상한 영속화

- 기존 메모리 호출 제한은 서버 재시작 시 사용량이 초기화되는 빈틈이 있어 Flyway V5와 MyBatis XML 기반 PostgreSQL 날짜별 호출 예약으로 교체했습니다.
- 외부 HTTP 요청 직전에 서울 날짜의 사용량을 원자적으로 증가시키며 기본 90건 상한에 도달하면 TMAP 요청을 보내기 전에 중단합니다.
- DB에는 날짜·예약 건수·갱신 시각만 저장하고 앱 키, 출발지·도착지·경유지 좌표, 경로와 응답은 저장하지 않습니다.
- 호출권 예약 SQL은 한 문장으로 끝나며 그 뒤에 외부 HTTP를 수행하므로 네트워크 I/O를 DB 트랜잭션 안에 오래 묶지 않습니다.
- PostgreSQL 17에서 제한기 인스턴스를 새로 만들어도 1건 상한이 유지되고, 동시 10개 예약에서 설정한 3건만 성공하는 것을 검증했습니다.
- V4까지만 적용한 PostgreSQL 17 DB에 기존 장소 행을 만든 뒤 V5를 적용해 장소 데이터가 유지되고 빈 호출량 테이블과 100건 DB 제약이 추가되는 업그레이드 경로를 자동 검증했습니다.
- 테스트에서는 `TEST_APP_KEY_ONLY`와 고정 시각만 사용했고 실제 TMAP 인증·외부 호출·유료 서비스는 사용하지 않았습니다.
- `.\gradlew.bat clean test --no-daemon`: 전체 70개 테스트 통과, 실패·오류·건너뜀 0개. V4→V5 업그레이드와 `tmap` 프로필의 실제 Spring 배선도 함께 검증했습니다.
- `.\gradlew.bat build --no-daemon`: 성공. Flyway V5와 영속 호출 제한을 포함한 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. V5와 영속 호출 제한이 포함된 `just-go-backend:local` 이미지를 생성했습니다.

## 29. 2026-11-07 서울·수도권 초기 경관 후보 검증

- MVP 초기 지역을 서울·경기·인천으로 확정했습니다.
- 공공데이터포털 전국관광지정보표준데이터의 897건 중 서울·경기·인천 147건을 후보군으로 분리하고, 도시·산·강·호수·바다 테마별 2건씩 총 10건을 Git 제외 로컬 JSON으로 준비했습니다.
- `SCENIC_CANDIDATE_IMPORT_DRY_RUN=true` 실행 결과 전체 10건, 등록 가능 10건, DB 중복 0건, 파일 중복 0건을 확인했습니다.
- 실제 등록은 신규 10건·중복 0건, 같은 파일 재실행은 신규 0건·DB 중복 10건으로 끝나 멱등성을 확인했습니다.
- PostgreSQL 조회 결과 다섯 테마가 각 2건이고 추천 조건을 만족하는 후보가 총 10건임을 확인했습니다.
- 로컬 파일의 장소명·좌표·출처 원문은 Git, 실행 로그와 이 문서에 기록하지 않았습니다. 공개 원천의 이용조건과 데스크 검수는 마쳤지만 실제 주행 중 경관·접근성·도로 상태는 아직 최종 검증하지 않았습니다.

## 30. 2026-11-07 TMAP 실제 인증 시도

- 실제 비밀값은 출력하지 않고 `backend/.env`의 필수 DB 설정과 `TMAP_APP_KEY`가 비어 있지 않으며 자리표시자가 아닌지만 확인했습니다. 파일은 `.gitignore`의 `**/.env` 규칙으로 제외됩니다.
- PostgreSQL 17.11, Flyway V1~V5, 초기 후보 10건이 있는 임시 DB에서 `local,tmap` 프로필로 백엔드를 18082 포트에 기동했고 `/actuator/health`가 `UP`임을 확인했습니다.
- 개인정보가 아닌 서울시청 공개 좌표를 출발지로 사용해 왕복 60분·강 테마 추천을 1건 요청했습니다.
- 백엔드는 HTTP 503 `ROUTE_PROVIDER_UNAVAILABLE`을 반환했고, 키를 노출하지 않는 직접 상태 확인에서 다중 경유지 30과 일반 자동차 경로안내가 모두 HTTP 403을 반환했습니다. 로컬 키 로딩과 외부 서버 도달은 확인됐지만 두 경로 API의 인증은 실패했습니다.
- 동일 실패의 반복 호출은 중단했습니다. TMAP 마이페이지에서 해당 앱의 `앱키(appKey)` 값과 자동차 경로안내·다중 경유지 30 API 사용 권한을 확인해야 합니다.
- 외부 제공자 오류 처리 로그에는 키·좌표·원시 응답·예외 메시지 없이 정규화된 `errorType`만 남기도록 보강했습니다. 사용자 응답의 기존 비밀값 없는 오류 계약은 유지했습니다.
- `./gradlew.bat clean test --no-daemon`: 전체 70개 테스트 통과, 실패·오류·건너뜀 0개.
- `./gradlew.bat build --no-daemon`: 성공. 실행 JAR 생성과 전체 검사를 통과했습니다.
- 유료 플랜, 결제수단, 과금 리소스는 사용하거나 신청하지 않았습니다.

## 31. 2026-11-07 초기 후보 로컬 추천 스모크

- TMAP 키를 사용하지 않는 `local,mock` 프로필로 PostgreSQL 17.11과 Flyway V1~V5를 연결했습니다.
- `GET /api/v1/spots`를 다섯 테마에 호출해 각 2건, 총 10건의 검수 후보가 REST 계층까지 조회되는 것을 확인했습니다.
- 개인정보가 아닌 서울시청 공개 좌표와 60분 조건으로 다섯 테마 각각의 왕복·편도, 총 10개 추천 요청을 실행했습니다.
- 모든 조합이 `mode=MOCK`, `mock=true`, 코스 1건으로 응답했고 실제 좌표로 오인할 `stops`는 빈 목록임을 확인했습니다.
- 이 결과는 DB 후보 조회와 REST 연결 검증이며 실제 도로 시간·거리·경관 품질 또는 TMAP 성공을 의미하지 않습니다.
- 실제 `.env`와 로컬 후보 파일은 Git에서 제외되고, 백엔드 Docker 빌드 컨텍스트에서도 `.dockerignore`의 `.env`, `local-data/` 규칙으로 제외되는 것을 확인했습니다.

## 32. 2026-11-07 VERIFIED 제약 전체 검증

- Flyway V6에서 출처 또는 품질 근거가 불완전한 과거 VERIFIED 행을 삭제하지 않고 `PENDING`으로 전환하도록 했습니다.
- 정상 출처와 세 품질 점수를 가진 VERIFIED 행은 상태를 유지합니다.
- 정리 후 `ck_spot_verified_provenance`, `ck_spot_verified_quality`에 `VALIDATE CONSTRAINT`를 적용해 새 행뿐 아니라 기존 전체 데이터에도 제약이 성립하도록 했습니다.
- V3·V4 각각의 도입 테스트는 해당 버전까지만 검증하도록 범위를 고정하고, V2에서 만든 불완전 VERIFIED 행과 V5의 정상 VERIFIED 행을 V6으로 올리는 별도 PostgreSQL 17 테스트를 추가했습니다.
- 표적 마이그레이션 테스트 4종이 통과했습니다. 불완전 행은 PENDING, 정상 행은 VERIFIED로 유지됐고 두 제약의 `convalidated`가 모두 참이며 불완전 행의 VERIFIED 재전환은 SQLSTATE `23514`로 거부됐습니다.
- `./gradlew.bat clean test build --no-daemon`: 전체 73개 테스트 통과, 실패·오류·건너뜀 0개이며 실행 JAR 빌드 성공.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. V6가 포함된 `just-go-backend:local` 이미지를 생성했습니다.
- 별도 임시 포트 55436/18083에서 PostgreSQL과 백엔드 컨테이너를 기동해 health `UP`, Flyway V6 성공 1건, 완전 검증된 VERIFIED 제약 2건, `uid=100(justgo)` 비루트 실행을 확인했습니다.
- 검증용 `just-go-v6-check` 컨테이너·네트워크·볼륨은 종료 후 삭제했으며 다른 Docker 프로젝트와 영구 볼륨은 건드리지 않았습니다.

## 33. 2026-11-07 경로 제공자 실행 안전장치

- `mock`과 `tmap` 프로필을 동시에 활성화하면 서버 시작 단계에서 거부하도록 `RoutingProfileGuard`를 추가했습니다. 개발용 MOCK 결과를 LIVE로 오인하는 실행 구성을 막습니다.
- `backend/.env.example`의 `replace-with-local-tmap-app-key` 자리표시자는 Bean Validation에서 거부하고, 실제 키 형식을 임의로 추측하지 않도록 그 외 비어 있지 않은 값은 허용합니다.
- TMAP 인증 오류의 MVC 계약 테스트를 추가해 HTTP 503, `ROUTE_PROVIDER_UNAVAILABLE`, 요청 `X-Trace-Id` 유지, 비밀 예외 메시지 비노출을 검증했습니다.
- 일반 자동차 경로안내의 `carType=1`은 승용차, 다중 경유지 30의 `carType=1`도 승용차라는 공식 정의를 2026-11-07 재확인했습니다. 현재 요청 모델과 일치해 코드는 변경하지 않았습니다.
- 프로필 조합, 예시 키 거부, MVC 장애 계약의 표적 테스트가 모두 통과했습니다.
- `./gradlew.bat clean test build --no-daemon`: 전체 78개 테스트 통과, 실패·오류·건너뜀 0개이며 실행 JAR 빌드 성공.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. 프로필·키 안전장치가 포함된 로컬 이미지를 생성했습니다.

## 34. 2026-11-07 추천 API 재현 스모크 스크립트

- `backend/scripts/Test-RouteRecommendation.ps1`을 추가해 키를 명령줄에 넣지 않고 로컬 추천 API의 MOCK 또는 LIVE 결과를 재검증할 수 있게 했습니다.
- 실제 사용자 위치 대신 서울시청 공개 좌표를 사용하며 키·지점명·좌표·원시 외부 응답은 출력하지 않습니다. 시간·거리·중복률·지점 수 요약과 traceId만 표시합니다.
- LIVE 모드는 `-ConfirmExternalCall`이 없으면 실행하지 않아 무료 쿼터의 우발적 사용을 막습니다.
- LIVE 성공은 `mode=LIVE`, `mock=false`뿐 아니라 실제 계산 코스 또는 같은 테마·주행 방식의 계산 기반 대체 시간 제안이 있어야 인정합니다. 후보가 없어 외부 호출을 하지 않은 빈 LIVE는 실패 처리합니다.
- PowerShell AST 구문 오류 0건, LIVE 확인 스위치 누락과 5분 단위 위반 방어를 확인했습니다.
- 임시 PostgreSQL 17/Flyway V6와 `local,mock` 백엔드에서 편도·도시·75분 요청을 실행해 `mode=MOCK`, 코스 1건, 지점 0건, `result=PASS`를 확인했습니다.
- Compose의 기본 `local` 프로필은 예상대로 `UNAVAILABLE`을 반환했고 스크립트가 기대 모드 불일치로 실패 처리했습니다.
- 검증용 `just-go-script-check` 컨테이너·네트워크·볼륨은 종료 후 삭제했습니다.

## 35. 2026-11-07 TMAP 호출 한도 부분 성공 보존

- 한 추천 요청에서 후보를 순서대로 계산하다 일일 호출 한도가 소진되면, 앞에서 이미 성공한 LIVE 경로를 버리지 않고 반환한 뒤 추가 호출을 중단하도록 Provider를 보강했습니다.
- 첫 후보부터 한도가 소진된 경우에는 성공 결과가 없으므로 기존 HTTP 503 경로를 유지합니다. 인증·네트워크·외부 응답 오류도 부분 성공으로 숨기지 않고 기존 502/503 오류 계약을 유지합니다.
- 부분 성공, 첫 호출 한도 소진, 성공 뒤 인증 실패를 각각 단위 테스트로 추가했습니다. 테스트 메시지에는 실제 키·좌표·외부 응답을 사용하지 않았습니다.
- 최초 전체 검증은 Docker Desktop 엔진이 꺼져 있어 Testcontainers 통합 테스트 7개 클래스가 초기화 단계에서 실패했습니다. 엔진을 시작한 뒤 같은 명령을 다시 실행해 코드 문제가 아님을 확인했습니다.
- `.\gradlew.bat clean test build --no-daemon`: 재실행 성공. 전체 81개 테스트 통과, 실패·오류·건너뜀 0개이며 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`: 성공.
- `docker compose --env-file .env.example --profile app build backend`: 성공. 변경된 Provider를 포함한 `just-go-backend:local` 이미지를 생성했습니다.
- 실제 TMAP 호출과 유료 서비스·결제·과금 리소스는 사용하지 않았습니다.

## 36. 2026-11-07 Free TMAP 상품 활성화 후 LIVE 재검증

- 기존 앱키는 정상적으로 발급·활성화되어 있었지만 앱의 상품 관리가 비어 있어 일반 자동차 경로와 다중 경유지 30이 모두 HTTP 403으로 거부된 상태였습니다.
- 사용자가 같은 앱에 `Free TMAP` 상품을 등록하고 활성화한 뒤 기존 키를 변경하지 않고 재검증했습니다. 별도 유료 상품, 결제수단, 종량제·정액제 전환은 사용하지 않았습니다.
- 임시 PostgreSQL 17.11에 Flyway V1~V6와 Git 제외 로컬 후보 10건을 적용했습니다. 외부 호출을 줄이기 위해 이 임시 DB에서만 테마별 후보를 1건으로 제한했습니다.
- `local,tmap` 백엔드의 health `UP`을 확인하고 개인 위치가 아닌 공개 테스트 좌표로 편도 일반 자동차 경로와 왕복 다중 경유지 경로를 실행했습니다. 외부 호출권은 총 3건만 예약했습니다.
- 최종 엄격 검증에서 왕복 요청은 HTTP 200, `mode=LIVE`, `mock=false`였고 traceId가 유지됐습니다. 실제 계산 시간이 요청 조건의 ±15% 범위를 벗어나 코스를 꾸며내지 않고 계산 기반 대체 조건을 반환했습니다. TMAP 데이터의 24시간 사용 제한을 고려해 세부 시간·거리 값은 문서에 영구 기록하지 않습니다.
- 앱 키, 지점명, 좌표, 원시 TMAP 요청·응답은 출력하거나 저장소에 기록하지 않았습니다.
- 검증용 백엔드 프로세스를 종료하고 `just-go-tmap-recheck` PostgreSQL 컨테이너·네트워크·볼륨을 모두 삭제했습니다.

## 37. 2026-11-07 두 경관 후보 조합 경로

- 기존 Provider는 경관 후보를 한 곳씩만 계산해 긴 주행 시간 조건을 탐색하기 어려웠습니다. 품질 우선순위가 높은 두 후보를 각각 계산한 뒤 두 곳을 잇는 조합을 세 번째로 계산하도록 확장했습니다.
- 왕복 조합은 `경관 후보 1 → 경관 후보 2 → 출발지 복귀`, 편도 조합은 `경관 후보 1 → 경관 후보 2에서 종료` 순서로 TMAP 요청과 API `stops`를 구성합니다. 단일 경로의 기존 ID 형식은 유지하고 조합 경로만 별도 ID로 구분합니다.
- 무료 쿼터 보호를 위해 추천 요청당 외부 계산은 여전히 최대 3건이며, 호출 한도 중간 소진 시 앞선 성공 결과를 보존하는 정책도 유지합니다.
- 단일 2건과 조합 1건, 왕복 두 경유지와 출발지 복귀 순서를 단위 테스트로 추가했습니다. `.\gradlew.bat clean test build --no-daemon` 실행 결과 전체 82개 테스트가 통과했고 실패·오류·건너뜀은 0개였습니다.
- `docker compose --env-file .env.example --profile app config --quiet`와 `docker compose --env-file .env.example --profile app build backend`가 성공해 Java 21 컨테이너 이미지를 생성했습니다.
- PostgreSQL 17.11, Flyway V1~V6, 로컬 후보 10건으로 다섯 테마의 120분 왕복을 실제 Free TMAP에서 확인했습니다. 모든 요청은 HTTP 200, `mode=LIVE`, `mock=false`였고 조합 계산도 성공했지만 초기 후보 묶음에는 ±15%에 맞는 결과가 없어 실제 계산 기반 대체 조건을 반환했습니다.
- 이 검증에서는 외부 호출권 15건을 사용했습니다. 앱 키·지점명·좌표·원시 응답과 24시간 제한 대상 세부 시간·거리 값은 저장하지 않았습니다.
- 검증용 `just-go-combined-check` 백엔드 프로세스와 PostgreSQL 컨테이너·네트워크·볼륨은 모두 종료·삭제했습니다.

## 38. 2026-11-07 수도권 후보 보강과 비대칭 시간 범위

- 공공데이터포털 전국관광지정보표준데이터에서 산·강·호수 후보를 1건씩 추가해 Git 제외 로컬 후보를 10건에서 13건으로 확장했습니다. 공개 원천·이용허락·좌표와 데스크 품질 점수는 확인했지만 실제 차량 접근성·주행 중 경관은 여전히 현장 또는 에뮬레이터 검수가 필요합니다.
- 임시 PostgreSQL 17.11에서 13건 모두 dry-run과 실제 등록을 통과했고 파일·DB 중복은 0건이었습니다. 보강한 세 테마의 120분 왕복은 모두 `HTTP 200`, `mode=LIVE`, `mock=false`였지만 당시 ±15% 기준에 맞는 코스는 없어 실제 계산 기반 대체 조건을 반환했습니다. 이 분포 확인에는 외부 호출권 9건을 사용했습니다.
- 사용자가 선택한 시간보다 긴 코스를 더 보수적으로 제한하도록 기본 시간 범위를 -20%~+15%로 분리했습니다. `ROUTE_ALLOWED_UNDERRUN_PERCENT`, `ROUTE_ALLOWED_OVERRUN_PERCENT` 환경변수로 각각 조정할 수 있고 두 값은 0~50 범위로 검증합니다.
- 60분 기준 48~69분 경계, 환경별 비율 적용, API 안내 문구를 자동 테스트로 검증했습니다. 최초 전체 테스트에서 MVC 슬라이스가 새 설정 Bean을 가져오지 못한 6건의 실패를 확인했고 테스트 구성을 보완한 뒤 동일 전체 명령을 재실행했습니다.
- `.\gradlew.bat clean test build --no-daemon`: 최종 재실행 성공. 전체 83개 테스트 통과, 실패·오류·건너뜀 0개이며 실행 JAR을 생성했습니다.
- `docker compose --env-file .env.example --profile app config --quiet`와 `docker compose --env-file .env.example --profile app build backend`: 성공. Java 21 백엔드 이미지를 생성했습니다.
- 확장 후보 13건과 새 기본 시간 범위로 공개 테스트 좌표의 60분 왕복을 실제 Free TMAP에서 다시 확인했습니다. 응답은 `HTTP 200`, `mode=LIVE`, `mock=false`, 코스 1건이었고 이번 확인에는 외부 호출권 3건만 사용했습니다. TMAP의 24시간 데이터 사용 제한을 지키기 위해 세부 시간·거리와 원시 응답은 기록하지 않았습니다.
- 두 검증의 임시 백엔드와 `just-go-candidate-check`, `just-go-policy-check` PostgreSQL 컨테이너·네트워크·볼륨은 모두 종료·삭제했습니다. 앱 키·DB 비밀번호·개인 위치는 출력하거나 저장소에 기록하지 않았습니다.

## 39. 2026-11-07 TMAP 키·Free 쿼터 보호

- 실제 `backend/.env`의 TMAP 앱 키를 출력하지 않고 전체 Git 패치 이력과 대조했습니다. 실제 키 일치 항목은 없었고 `backend/.env`도 Git 추적 대상이 아님을 확인했습니다.
- 같은 점검에서 현재 로컬 DB 비밀번호가 Git에 공개된 예시용 약한 값과 같음을 확인했습니다. 실제 비밀번호가 과거 커밋된 것은 아니지만 로컬 파일과 PostgreSQL 사용자의 비밀번호 교체가 필요하며, 기존 볼륨과의 일치 문제 때문에 자동 변경하지 않았습니다.
- 호스트에서 직접 실행하는 Spring Boot는 기본 `127.0.0.1`에만 바인딩합니다. Docker Compose는 컨테이너 내부 `0.0.0.0`과 호스트 `127.0.0.1` 포트 바인딩을 함께 사용해 로컬 접근성과 외부 차단을 유지합니다.
- 공개 `production` 프로필은 TMAP 키와 별개인 32~128자 추천 API 접근 토큰을 필수화했습니다. 누락·약한 값·예시값이면 실제 토큰을 출력하지 않고 시작을 중단합니다.
- 추천 API에 직접 연결 주소 기준 분당 4회, 서버 전체 동시 2회, `Content-Length` 8KiB 제한을 추가했습니다. 위조 가능한 `X-Forwarded-For`는 기본적으로 무시하며 기존 PostgreSQL 일일 TMAP 90건과 요청당 최대 3건 제한을 유지합니다.
- API 캐시 차단, 콘텐츠 추론 차단, 프레임 차단, referrer·카메라·마이크·위치 브라우저 권한 제한 헤더를 추가했습니다. 접근 토큰·IP는 로그나 DB에 기록하지 않습니다.
- `production` 프로필의 실제 HTTP 검증 결과 health 200, 무토큰 추천 401, 정상 테스트 토큰 MOCK 200, 과대 본문 413, 분당 초과 429였고 traceId와 보안 헤더도 유지됐습니다. 이 검증은 MOCK만 사용해 TMAP 외부 호출권을 소비하지 않았습니다.
- `backend/scripts/Test-RouteRecommendation.ps1`은 선택적으로 `ROUTE_API_ACCESS_TOKEN`을 헤더에 넣되 출력하지 않도록 변경했고 PowerShell AST 구문 오류 0건을 확인했습니다.
- `.\gradlew.bat clean test build --no-daemon`: 전체 93개 테스트 통과, 실패·오류·건너뜀 0개이며 실행 JAR 생성 성공.
- `docker compose --env-file .env.example --profile app config --quiet`와 Java 21 백엔드 이미지 빌드가 성공했습니다. 별도 포트의 전체 Compose도 healthy 상태에서 host loopback의 health·정적 화면 HTTP 200과 보안 헤더를 확인했습니다. 검증용 `just-go-security-check`, `just-go-security-compose` 백엔드·PostgreSQL 컨테이너·네트워크·볼륨은 종료·삭제했습니다.
- 공개 배포 전 남은 조치는 HTTPS 프록시의 chunked 본문 제한, 고정 서버 송신 IP의 TMAP 콘솔 허용 목록 등록, APK 추출에 의존하지 않는 기기·사용자 인증 방식 결정입니다. 유료 서비스나 결제수단은 추가하지 않았습니다.

## 40. 2026-11-07 Android NaviHelper 경로 전달 구현

- 공식 NaviHelper 3.0.5 Maven 저장소와 의존성을 Android 프로젝트에 추가하고 실제 AAR의 공개 시그니처를 확인했습니다. 문서 예시만 추측해 사용하지 않고 `RouteInfo`, `RequestWaypointInfo`, `RouteWithWaypointsRequest`, `NavigationScreenRequest.Foreground` 생성자와 메서드를 로컬 아티팩트에서 재확인했습니다.
- 추천 응답에서 버리던 WGS84 지점 좌표와 `routeId`를 Android 모델에 보존했습니다. LIVE 코스만 사용자가 명시적으로 선택할 수 있고 MOCK 결과는 내비 시작을 허용하지 않습니다.
- `NavigationGateway`와 `PleosNavigationGateway`를 추가했습니다. 단일 목적지는 `requestRoute()`, 최대 두 경유지가 있으면 `requestRouteWithWaypoints()`를 사용하고 `onRoutingCompleted()` 뒤 Pleos Navigation 전면 화면을 요청합니다.
- `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP` 권한만 추가했으며 `APP_MAPS` 카테고리는 선언하지 않았습니다. 경로 지점 순서·좌표·단일 목적지·경유지 최대 두 곳 검증을 별도 순수 Java 경계로 분리하고 자동 테스트 5건을 추가했습니다.
- 앱 표시명과 웹 시안의 표시명을 확정 명칭 `just go`로 통일했습니다.
- `android-app`에서 `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon`: 성공. 단위 테스트, Lint, NaviHelper 포함 디버그 APK 생성이 모두 통과했습니다.
- 생성 APK를 연결된 `crp_avd` Pleos Connect Emulator에 재설치하고 콜드 스타트 성공, `just go` 표시, AndroidRuntime 치명 오류 없음까지 확인했습니다.
- 실제 LIVE 코스로 내비 목적지·경유지 전달을 확인하려 했으나 로컬 PostgreSQL의 `justgo` 사용자 비밀번호와 `backend/.env` 설정이 일치하지 않아 백엔드 기동이 실패했습니다. 비밀번호를 임의 변경하지 않았으며 실제 NaviHelper 경로 전달과 화면 전환은 `NOT VERIFIED`입니다. TMAP 외부 호출권은 사용하지 않았습니다.

## 41. 2026-11-07 Android 주행 규제 연동

- 공식 Pleos 주행 규제 가이드와 Android Automotive 문서에 따라 `android.car` 선택 라이브러리와 `CarUxRestrictionsManager`를 연결했습니다. 절대 속도나 기어 값을 직접 해석하지 않고 플랫폼이 전달하는 `isRequiresDistractionOptimization()`만 사용합니다.
- 주행 규제가 활성화되면 왕복·편도, 시간, 풍경, 직접 입력, 추천 검색, 코스 선택과 내비 시작을 비활성화하고 풍경 애니메이션을 중지합니다. 정차 상태로 돌아오면 현재 선택 상태를 유지한 채 다시 활성화합니다.
- 차량 서비스나 규제 값을 읽지 못한 Automotive 환경에서는 안전하게 입력을 잠그며, 비차량 Android에서는 제한하지 않습니다. 리스너는 Activity `onStart`/`onStop`에 맞춰 등록·해제합니다.
- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon`: 성공. `android.car`와 NaviHelper를 함께 포함한 디버그 APK를 생성했습니다.
- APK를 `crp_avd` Pleos Connect Emulator에 재설치해 정차 상태 콜드 스타트와 화면 표시를 확인했고 AndroidRuntime 치명 오류는 없었습니다. 실제 주행 상태로 전환했을 때 입력 잠금과 애니메이션 정지는 `NOT VERIFIED`입니다.

## 42. 2026-11-07 Spot Mapper 통합 테스트 격리

- 전체 백엔드 테스트에서 `SpotMapperIntegrationTest`의 다른 테스트가 먼저 삽입한 RIVER 후보가 남아 `findsOnlyEnabledAndVerifiedCandidates()`의 정확한 목록 검증에 섞이는 순서 의존 실패를 확인했습니다.
- 통합 테스트 클래스에 테스트별 트랜잭션 롤백을 적용해 각 테스트 데이터가 다음 테스트로 누출되지 않도록 했습니다. 운영 SQL과 마이그레이션은 변경하지 않았습니다.
- `.\gradlew.bat test --tests "com.example.justgo.backend.spot.SpotMapperIntegrationTest" --no-daemon`: 성공.
- `.\gradlew.bat test --no-daemon`: 전체 94개 테스트 통과, 실패·오류·건너뜀 0개. 외부 TMAP 호출은 실행하지 않았습니다.

## 43. 2026-11-08 로컬 LIVE 경로와 앱 시작 위치 검증

- 다른 로컬 프로젝트의 PostgreSQL이 호스트 `5432` 포트를 사용하고 있어 `just go` PostgreSQL을 Git 제외 로컬 설정에서 `55432`로 옮겼습니다. 기존 볼륨을 보존한 채 DB 사용자 비밀번호를 두 환경 파일과 동기화했고 PostgreSQL `healthy`, Flyway v7, 백엔드 health `UP`을 확인했습니다.
- 검수 후보 로컬 파일을 dry-run한 뒤 13건을 등록했습니다. 서울 출발 120분 편도 기준 도시 2건, 산 3건, 강 3건, 호수 3건, 바다 2건이 후보 거리 안에 있음을 확인했습니다.
- Pleos Emulator의 기본 GPS가 미국 위치라 서울·수도권 후보가 모두 거리 필터에서 제외되는 원인을 확인했습니다. 공개 테스트 위치를 서울로 설정한 뒤 `편도·120분·바다` 추천 API가 HTTP 200, LIVE 코스 1건을 반환했습니다. 이 검증은 TMAP Free 호출 3건을 사용했습니다.
- Android 앱은 시작 시 위치 권한을 확인하고 현재 기기 위치를 한 번 선확보해 최대 30초 동안 메모리에서만 재사용하도록 보강했습니다. 에뮬레이터에는 호스트 PC 위치가 아니라 설정된 가상 GPS가 전달되며, 위치를 미리 서버에 보내거나 이동 이력을 저장하지 않습니다.
- `.\gradlew.bat testDebugUnitTest assembleDebug`: 성공. 새 APK를 Pleos Emulator에 설치하고 앱 시작 직후 GPS 위치가 전달되는 것을 확인했습니다.
- 추천 API의 로컬 기본 제한은 클라이언트별 분당 4회입니다. 테마를 연속 시험해 발생한 HTTP 429는 TMAP 일일 한도가 아니라 이 요청 보호 제한이었으며, TMAP 쿼터 오류는 백엔드에서 HTTP 503으로 구분됩니다.
- 앱에서 반환된 LIVE 코스를 선택해 NaviHelper에 목적지·경유지를 전달하고 Navigation 화면으로 전환하는 최종 단계는 아직 `NOT VERIFIED`입니다.

## 44. 2026-11-08 앱 화면 LIVE 연동과 NaviHelper 단일 목적지 검증

- Pleos Emulator 화면에서 `편도·120분·강/호수`를 요청해 앱→백엔드→TMAP 연동과 실제 계산 시간 기반 대체 조건 반환을 확인했습니다. 제안된 90분으로 재검색했을 때 LIVE 코스의 시간·거리·목적지가 앱 화면에 표시됐습니다.
- 동적으로 생성한 LIVE 코스 `RadioButton`이 Pleos 환경에서 `checkable`이지만 `clickable=false`, `focusable=false`로 생성돼 코스를 선택할 수 없는 문제를 확인했습니다. 클릭·포커스 속성을 명시해 `checked=true`와 내비 시작 버튼 활성화를 실제 UI 계층에서 검증했습니다.
- 선택한 편도 LIVE 코스를 NaviHelper 3.0.5 `requestRoute()`로 전달했고 Pleos Navigation 화면 전환, 경로 안내 시작, 목적지와 예상 시간·거리 표시를 확인했습니다. 단일 목적지 실제 연동은 검증 완료했지만 최대 두 경유지의 `requestRouteWithWaypoints()`는 아직 `NOT VERIFIED`입니다.
- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon`: 성공. 수정 APK를 Pleos Emulator에 덮어 설치한 뒤 화면 흐름을 다시 확인했습니다.
- 이번 화면 검증을 마친 시점의 로컬 TMAP 일일 보호 카운터는 33/90입니다. 유료 전환이나 외부 응답 저장은 수행하지 않았습니다.
