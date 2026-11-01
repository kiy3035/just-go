# 그냥 고! Android

Java/XML + ViewBinding 앱입니다. 저장소명은 `just-go`, Gradle 프로젝트명은 `just go`, 화면·앱 표시명은 `그냥 고!`입니다.

## 구조
- `build.gradle`: 단일 앱 빌드 설정.
- `settings.gradle`, `gradle.properties`: 프로젝트·Gradle 실행 옵션.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`: 버전이 고정된 빌드 실행기.
- `src/main/java/.../ui/DriveActivity.java`: 선택 조건·로딩 표시·화면 상태 복원.
- `src/main/java/.../ui/RiversideSceneView.java`: 6개 풍경과 차·바퀴·차선 애니메이션.
- `src/main/java/.../data/DriveSelection.java`: 10~240분, 5분 단위 입력 검증.
- `../backend/src/main/resources/static/index.html`: 웹 화면 구조와 SVG 풍경.
- 같은 폴더의 `drive.css`, `drive.js`: 웹 스타일과 화면 동작.

## 빌드·실행
JDK 17 / API 34 / minSdk 28.
```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
android run --device=emulator-5554 --apks="build/outputs/apk/debug/just go-debug.apk"
```

## 동작
- 기본값: 왕복·60분·어디든. 편도/왕복, 시간 직접 입력, 6개 장소 선택.
- 장소에 따라 초원·빌딩·초록 능선·해안·물가·공원으로 풍경 전환.
- 블루 그라데이션 선택 카드, 6개 풍경 일러스트, 실시간 선택 조건 요약. 가로 화면의 풍경/조건 비율은 36:64.
- 소개: “시간과 목적지 고민 없이 / 가볍게 떠날 수 있어요”.
- 은은한 차체 반사광·접지 그림자, 구름·차선의 서로 다른 속도, 작은 차체 움직임과 회전 바퀴.
- 검색 버튼은 선택 조건과 함께 1.8초간 파란 로딩 상태를 표시한 뒤 선택 조건을 확인.
- Android는 화면이 숨겨지면 애니메이션을 멈춥니다. 웹은 `prefers-reduced-motion`을 존중합니다.
- Android 화면 변경 시 HTML·CSS·JS도 함께 반영합니다.

## 연결 상태
현재 위치·교통·추천 API·NaviHelper·운행 상태 제한은 미구현입니다.
화면은 MOCK 동작으로 선택 조건만 확인하며 가상 경로나 실제 차량값을 생성하지 않습니다.
`com.example.justgo`는 PROVISIONAL applicationId입니다. Release 서명키는 생성하지 않았습니다.
백엔드의 API 계약과 추천 로직은 이번 UI 정리 범위에 포함하지 않습니다.

## 검증 기준
단위 테스트와 Lint, 6개 테마 전환, 차·차선 움직임, 사용자 지정 시간 검증, 로딩 해제, 화면 상태 복원.
웹 스타일 정리는 데스크톱 1600px·좁은 화면 390px에서 정리 전후 위치·크기·색상과 캡처를 비교합니다.

Pleos 환경: Pleos_Connect_v2.1.0 / API 34.
공식 가이드 확인(2026-11-01): https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
앱 UI 실행 확인과 실제 NaviHelper·운행 제한 검증은 별개입니다.
