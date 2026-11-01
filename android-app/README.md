# 그냥 고! Android

사용자 요청에 따라 화면·앱 표시명을 `그냥 고!`로 변경했습니다. 저장소와 Gradle 프로젝트명은 `just-go` / `just go`를 유지합니다.
선 형태 벡터 아이콘을 Android와 HTML에 동일하게 적용했습니다. 화면은 선택 조건만 표시하며 개발 상태 문구는 제거했습니다.
공식 Pleos 설치 가이드 확인(2026-11-01): https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
로컬 실행 기기: Pleos_Connect_v2.1.0, Connect on x86_64 emulator, API 34. 앱 UI 실행 확인과 NaviHelper/운행 제한 검증은 별개입니다.

Java/XML + ViewBinding 앱입니다. Android Studio에서는 이 `android-app` 폴더를 엽니다.

## 구조
- `build.gradle`: 단일 Android 앱 빌드 설정. 하위 앱 모듈 없음.
- `settings.gradle`: 프로젝트명과 저장소 설정.
- `gradle.properties`: Gradle 실행 옵션.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`: 버전이 고정된 빌드 실행기.
- `src/main/`: 화면과 앱 코드. `src/test/`: 단위 테스트.
백엔드는 별도 Java 서버이므로 자체 Gradle Wrapper를 유지합니다.

## 빌드와 실행
JDK 17 / API 34 / minSdk 28.
```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
android run --device=emulator-5554 --apks="build/outputs/apk/debug/just go-debug.apk"
```
APK 실제 이름은 프로젝트명에 따라 다르므로 `build/outputs/apk/debug/output-metadata.json`을 확인합니다.

## 화면
`backend/src/main/resources/static/index.html`을 기준으로 네이티브 화면을 구성했습니다.
화이트·블루 화면. 왼쪽 풍경 카드 36%, 오른쪽 조작 영역 64%. 세 항목 제목은 굵게 표시합니다.
장소 선택 즉시 초원(어디든 좋아), 빌딩(도시), 능선·숲(산), 해안(바다), 물가(강/호수), 나무(공원)로 바뀝니다.
파란 하늘, 둥근 카드, 시간 선택 막대, 남색 실행 버튼을 Android와 HTML에 함께 적용했습니다.
장식 애니메이션은 자동차의 느린 이동·바퀴 회전·차선 흐름을 보여주며 실제 차량값을 사용하지 않습니다.
Android는 화면이 숨겨지면 애니메이션을 멈추고 배경 이미지를 테마별로 재생성합니다. HTML은 prefers-reduced-motion을 존중합니다.
브랜드 표시는 48sp로 확대하고, 주제목은 문장부호 없이 “정하지 않아도 / 좋은 길로”로 표시합니다. 구름은 제목 아래로 내려 겹침을 줄였으며, 실행 버튼의 높이와 상하 여백을 늘렸습니다.
주제목은 하늘의 상단에 배치하고, 자동차는 앞쪽의 긴 보닛·헤드램프·그릴과 뒤쪽 빨간 테일램프를 구분한 현대차 감성의 세단 일러스트입니다(특정 모델의 공식 렌더링 아님). 산은 눈 없는 초록 능선입니다.
검색 버튼은 선택 조건과 함께 1.8초간 파란 로딩 상태를 표시한 뒤 선택 조건을 확인합니다. 실제 경로 API 호출·성공을 모의하지 않습니다.
하단 문구는 모든 풍경에서 “시간과 가고싶은 곳만 고르면 / 가볍게 떠날 수 있어요.”를 유지합니다. 32sp 선택 카드로 가독성을 높였습니다.
왕복/편도, 30/60/120분/직접 입력, 어디든 좋아/도시/산/바다/강·호수/공원.
기본값은 왕복·60분·어디든 좋아입니다. 강/호수는 합쳐진 UI 선택지이며 서버 매핑은 아직 구현하지 않았습니다. 직접 입력은 10~240분, 5분 단위입니다.
제출하면 선택 조건을 표시하고 화면 재생성 시 선택값과 결과를 복원합니다.

## 연결 상태
MOCK 화면 체험으로 실제 위치·교통·추천 API는 호출하지 않습니다.
NaviHelper와 운행 상태 제한은 미구현이므로 정차 중 개발 검토에만 사용합니다.
가상 코스 결과를 생성하지 않으며 실제 경로 연결 전 선택 조건만 확인합니다.
applicationId `com.example.justgo`는 PROVISIONAL입니다. Release 키는 생성하지 않았습니다.
웹 기준 화면 및 백엔드 API 계약은 변경하지 않았습니다.

## 검토
왕복/편도·풍경 변경, 시간 직접 입력 표시, 10/240분 허용, 9/241/31분 거부, 선택 요약 표시 및 화면 재생성 복원.
