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
