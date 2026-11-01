# just go 프로젝트 진척 현황

> 마지막 갱신: 2026-11-01
> 이 문서는 완료·보류·차단 항목을 한눈에 확인하는 현황판입니다. 상세 실행 기록은 [`setup-report.md`](setup-report.md), 제품 범위는 [`requirements.md`](requirements.md), API 계약은 [`api-contract.yaml`](api-contract.yaml)을 기준으로 합니다.

## 상태 기준

- `완료`: 코드 또는 환경 구성을 끝내고 가능한 검증을 실제로 통과함
- `진행 가능`: 선행 결정 없이 바로 이어서 작업할 수 있음
- `보류`: 사용자 지시로 현재 순서를 미룸
- `차단`: 장비·계정·비용 승인·제품 결정 등 선행 조건이 필요함
- `미착수`: 아직 작업하지 않음

## 전체 현황

| 영역 | 상태 | 현재 결과 | 검증 또는 남은 조건 |
|---|---|---|---|
| 초기 사전 점검 | 완료 | Java 21, Git, Docker, 가상화, 메모리·저장공간, GPU 확인 | Radeon 780M 전용 VRAM 0.5GB는 공식 Pleos GPU 최소 기준 미달 |
| 저장소 구조 | 완료 | `backend`, `infra`, `docs`, Android 자리 구성 | `main` 및 기존 `origin` 사용 |
| 백엔드 골격 | 완료 | Spring Boot 3.5.16, Gradle 8.14.6, MyBatis XML, Flyway, Actuator | 전체 자동 테스트 통과 |
| 로컬 DB·인프라 | 완료 | PostgreSQL 17.11 Compose, 볼륨, healthcheck, loopback 바인딩 | 실제 컨테이너·Flyway V1/V2 검증 완료 |
| 추천 REST 골격 | 완료 | 추천 요청 API, 교체 가능한 `DrivingRouteProvider`, `MOCK`/`UNAVAILABLE` 구분 | 실제 외부 자동차 경로는 아직 미연동 |
| 경관 장소 후보 | 완료 | VERIFIED·활성·좌표 보유 후보만 MyBatis XML로 조회 | 실제 장소 데이터는 사용권·수동 검수 전이라 없음 |
| 추천 후보 연결 | 완료 | 검수 완료 장소를 최대 20건 조회해 경로 제공자 요청 경계로 전달 | 외부 제공자 호출 없이 단위 테스트로 전달 구조 검증 |
| 개발용 웹 확인 화면 | 완료 | `/`에서 추천 요청과 장소 후보 조회 가능 | 화면은 Android 대체물이 아닌 로컬 API 확인용 |
| Android Studio | 완료 | 프로그램 설치 확인 | Android SDK/API 34와 프로젝트는 아직 없음 |
| Android SDK·프로젝트 | 보류 | 사용자 지시로 Java/Web 백엔드 작업을 우선함 | 재개 시 API 34 설치, Java/XML 프로젝트 생성, `assembleDebug` 필요 |
| Pleos Emulator·NaviHelper | 차단 | 실제 연동 코드를 만들지 않음 | 공식 최소 GPU를 만족하는 장비, SDK/계정, 에뮬레이터 검증 필요 |
| 실제 추천 알고리즘 | 진행 가능 | 후보 조회·Provider 경계까지 준비됨 | 목표 시간 정의·허용 오차 확정과 실제 경로 결과가 필요 |
| 외부 경로 API 조사 | 완료 | TMAP·카카오모빌리티·NAVER 공식 기능·가격·쿼터·제약 비교 | `routing-provider-comparison.md`에 확인일과 근거 기록 |
| 외부 경로 API 연동 | 차단 | TMAP Free를 PoC 1순위로 제안, 키·계정·결제 연동 없음 | 사용자 업체 선택과 무료 범위 사용 승인 필요 |
| 원격 HTTPS 백엔드 | 미착수 | 로컬 개발만 구성 | 제출/시연 방식 확정 후 비용·보안 검토 필요 |
| Release APK·서명 | 미착수 | 키 생성·커밋 없음 | Android 완성 후 고유 applicationId, 서명, 실제 실행 검증 필요 |

## 완료된 백엔드 흐름

```text
추천 HTTP 요청
  → RouteRecommendationService
  → VERIFIED 경관 장소 후보 조회
  → DrivingRouteProvider 요청 모델로 후보 전달
  → MOCK 또는 UNAVAILABLE 응답
```

현재 `MOCK` 결과는 화면·REST 연결 확인 전용 가짜 예시이며 실제 도로 경로, 시간, 거리로 간주하지 않습니다. 일반 프로필은 외부 제공자가 없으므로 실제 코스를 만들지 않습니다.

## 건너뛰거나 미룬 작업

1. Android SDK API 34 설치 및 라이선스 확인
2. Android Java/XML 프로젝트 생성과 `assembleDebug`
3. Pleos AVD 설치·Cold boot·화면 실행 검증
4. NaviHelper 단일 목적지·다중 경유지 실제 호출 검증
5. 실제 외부 자동차 경로 API 선정·계정·키 연동
6. 수동 검수된 실제 경관 장소/구간 데이터 등록
7. 실제 경로 시간·거리 기반 0~3개 추천 및 실패 대안
8. 원격 HTTPS 배포, 고유 applicationId, Release APK와 서명

## 바로 이어갈 다음 작업

1. 사용자가 TMAP Free 제안 또는 대안을 선택하고 무료 범위의 계정·앱 키 사용 여부를 승인한다.
2. 승인된 제공자의 Provider PoC를 만들고 timeout·호출량 상한·오류 분류·응답 검증을 추가한다.
3. Android 작업 재개 지시가 오면 SDK/API 34 구성부터 진행하되 Pleos 실제 성공 여부는 별도로 기록한다.

## 갱신 규칙

- 기능·환경 작업이 끝날 때 이 문서의 상태와 남은 조건을 함께 갱신합니다.
- 실제 실행 명령과 성공·실패 로그 요약은 `docs/setup-report.md`에 날짜순으로 남깁니다.
- API 요청·응답이 바뀌면 `docs/api-contract.yaml`을 같은 작업에서 갱신합니다.
- 개인정보·비밀값·실제 위치·API 키는 이 문서를 포함해 저장소에 기록하지 않습니다.
