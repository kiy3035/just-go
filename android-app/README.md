# just go Android

Java/XML + ViewBinding 앱입니다. 저장소명은 `just-go`, Gradle 프로젝트명과 화면·앱 표시명은 `just go`입니다.

## 구조
- `build.gradle`: 단일 앱 빌드 설정.
- `settings.gradle`, `gradle.properties`: 프로젝트·Gradle 실행 옵션.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`: 버전이 고정된 빌드 실행기.
- `src/main/java/.../ui/DriveActivity.java`: 선택 조건·출발지 입력·추천 결과·오류 표시.
- `src/main/java/.../ui/RiversideSceneView.java`: 풍경과 차·바퀴·차선 애니메이션.
- `src/main/java/.../data/DriveSelection.java`: 시간 및 WGS84 좌표 입력 검증.
- `src/main/java/.../data/RouteRecommendationClient.java`: 추천 HTTP 요청과 응답 해석.
- `../backend/src/main/resources/static/index.html`: 웹 화면 구조와 SVG 풍경.
- 같은 폴더의 `drive.css`, `drive.js`: 웹 스타일과 화면 동작.

## 빌드·실행
JDK 17 / API 34 / minSdk 28.
```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
adb install -r "build/outputs/apk/debug/just go-debug.apk"
```

## 동작
- 기본값: 왕복·60분·도시. 편도/왕복, 시간 직접 입력, 도시·산·강·호수·바다 중 한 가지 선택.
- 테마에 맞춰 풍경을 전환합니다. 강과 호수는 물가 일러스트를 공유합니다.
- 블루 그라데이션 선택 카드, 풍경 일러스트, 실시간 선택 조건 요약. 가로 화면의 풍경/조건 비율은 36:64.
- 소개: “시간과 목적지 고민 없이 / 가볍게 떠날 수 있어요”.
- 은은한 차체 반사광·접지 그림자, 구름·차선의 서로 다른 속도, 작은 차체 움직임과 회전 바퀴.
- 검색 버튼에서 WGS84 출발 좌표를 입력하고 추천을 요청합니다. 결과에는 시간·거리·경유 순서·추천 이유 또는 재검색 안내가 표시됩니다.
- Android는 화면이 숨겨지면 애니메이션을 멈춥니다. 웹은 `prefers-reduced-motion`을 존중합니다.
- Android 화면 변경 시 HTML·CSS·JS도 함께 반영합니다.

## 로컬 추천 API 연결

1. 저장소 [README](../README.md)의 PostgreSQL과 백엔드 실행 절차를 따라 서버를 18080 포트에서 시작합니다. 외부 경로 호출 없이 앱 연결을 확인하려면 서버 프로필로 `local,mock`을 사용합니다.
2. Pleos Emulator를 시작한 뒤 SDK의 `adb`로 `adb -s emulator-5554 reverse tcp:18080 tcp:18080`을 실행합니다. 기기 ID는 `adb devices`에서 확인합니다. 서버가 다른 포트에서 실행 중이면 두 번째 포트를 바꿉니다.
3. Debug 앱에서 조건 선택 → **이 조건으로 코스 찾기** → 위도·경도 입력 → **코스 찾기**를 누릅니다. MOCK 검증에는 위도와 경도에 각각 `0`을 넣어 테스트할 수 있습니다.
4. 앱이 `POST /api/v1/routes/recommendations`를 호출합니다. 서버의 `MOCK`, `LIVE`, `UNAVAILABLE` 상태를 구분해 표시합니다. 연결이 끊겼다면 서버와 reverse 설정을 확인한 뒤 다시 검색합니다.

Debug의 HTTP는 ADB로 전달하는 `127.0.0.1`에 한정합니다. Release 추천 서버는 HTTPS 주소 확정 후 구성합니다. 입력 좌표는 현재 화면 메모리에서 재검색용으로 유지하며 앱이 백그라운드로 이동하면 지웁니다. 좌표 입력란은 자동 완성과 상태 저장에서 제외하고 키보드에 개인화 학습 제외를 요청합니다. 자동 위치 획득·NaviHelper·운행 제한은 추후 검증이 필요합니다. `com.example.justgo`는 PROVISIONAL applicationId입니다.

## 검증 기준
단위 테스트와 Lint, 5개 테마 전환, 차·차선 움직임, 사용자 지정 시간 검증, 로딩 해제, 화면 상태 복원.
웹 스타일 정리는 데스크톱 1600px·좁은 화면 390px에서 정리 전후 위치·크기·색상과 캡처를 비교합니다.

Pleos 환경: Pleos_Connect_v2.1.0 / API 34.
공식 가이드 확인(2026-11-01): https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
앱 UI 실행 확인과 실제 NaviHelper·운행 제한 검증은 별개입니다.
