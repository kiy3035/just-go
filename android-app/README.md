# just go Android

Java/XML + ViewBinding 앱입니다. 저장소명은 `just-go`, Gradle 프로젝트명과 화면·앱 표시명은 `just go`입니다.

## 구조
- `build.gradle`: 단일 앱 빌드 설정.
- `settings.gradle`, `gradle.properties`: 프로젝트·Gradle 실행 옵션.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`: 버전이 고정된 빌드 실행기.
- `src/main/java/.../ui/DriveActivity.java`: 선택 조건·로딩 표시·화면 상태 복원.
- `src/main/java/.../ui/RiversideSceneView.java`: 6개 풍경과 차·바퀴·차선 애니메이션.
- `src/main/java/.../data/DriveSelection.java`: 10~240분, 5분 단위 입력 검증.
- `src/main/java/.../data/RouteRecommendationClient.java`: 추천 REST 요청과 응답 검증.
- `src/main/java/.../pleos/NavigationGateway.java`: UI와 차량 내비 연동 사이의 추상화.
- `src/main/java/.../pleos/PleosNavigationGateway.java`: NaviHelper 3.0.5 목적지·경유지·내비 화면 요청 구현.
- `src/main/java/.../pleos/DrivingRestrictionsMonitor.java`: `CarUxRestrictions` 변경 감시와 주행 중 입력 제한.
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
- 검색 버튼은 기기 현재 위치와 선택한 시간·왕복/편도·6개 테마를 추천 API에 전달하고 결과를 표시합니다.
- 앱 시작 시 기기 현재 위치를 한 번 선확보해 최대 30초 동안 메모리에서만 재사용합니다. 위치는 코스 찾기를 누르기 전에는 서버로 보내지 않습니다.
- LIVE 추천 코스를 명시적으로 선택하면 목적지와 최대 두 경유지를 Pleos Navigation에 전달합니다. 탐색 완료 뒤 Navigation 화면 전환도 요청합니다.
- `CarUxRestrictions`가 주의 분산 최적화를 요구하면 조건 입력·검색·내비 시작과 풍경 애니메이션을 중지합니다.
- Android는 화면이 숨겨지면 애니메이션을 멈춥니다. 웹은 `prefers-reduced-motion`을 존중합니다.
- Android 화면 변경 시 HTML·CSS·JS도 함께 반영합니다.

## 연결 상태
위치 권한 허용 후 기기 현재 위치를 위도·경도로 전송합니다. 좌표 수동 입력이나 테마 재선택 팝업은 없습니다.
디버그 앱은 에뮬레이터 호스트 주소 `http://10.0.2.2:18080`의 추천 API를 사용합니다. 서버를 해당 포트에서 실행해야 합니다.
에뮬레이터는 호스트 PC의 실제 위치가 아니라 에뮬레이터에 설정된 가상 GPS를 제공합니다. 위치를 수신하지 못하면 안내하며 임의 좌표로 대체하지 않습니다.
어디든은 전체 후보, 강/호수는 강·호수 후보, 공원은 공원 후보를 조회합니다. 검수된 공원 후보가 없으면 빈 결과가 반환됩니다.
MOCK 응답은 화면에 표시하지만 내비 시작은 허용하지 않습니다. NaviHelper 3.0.5 연동 코드는 빌드 검증했으나 Pleos Emulator의 실제 목적지·경유지 전달과 화면 전환은 `NOT VERIFIED`입니다. 정차 상태의 `CarUxRestrictions` 연결은 앱 기동으로 확인했지만 주행 상태 전환은 `NOT VERIFIED`입니다.
`com.example.justgo`는 PROVISIONAL applicationId입니다. Release 서명키는 생성하지 않았습니다.
백엔드의 API 계약과 추천 로직은 이번 UI 정리 범위에 포함하지 않습니다.

## 검증 기준
단위 테스트와 Lint, 6개 테마 전환, 차·차선 움직임, 사용자 지정 시간 검증, 로딩 해제, 화면 상태 복원.
웹 스타일 정리는 데스크톱 1600px·좁은 화면 390px에서 정리 전후 위치·크기·색상과 캡처를 비교합니다.

Pleos 환경: Pleos_Connect_v2.1.0 / API 34, NaviHelper 3.0.5.
공식 가이드 확인(2026-11-07): https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
앱 UI 실행 확인과 실제 NaviHelper·운행 제한 검증은 별개입니다.
