# just go

> 목적지를 정하지 않아도, 원하는 시간과 풍경만 고르면 출발할 수 있는 드라이브 추천 서비스

`just go`는 현대자동차그룹 2026 차량용 앱 개발 공모전을 위해 만든 Pleos Connect 차량용 Android 앱입니다.

운전은 하고 싶은데 목적지를 고르느라 시간을 쓰고 싶지 않은 순간이 있습니다. 이 앱에서는 왕복·편도, 드라이브 시간, 보고 싶은 풍경만 고르면 됩니다. 현재 위치 주변의 장소를 찾고 실제 자동차 경로를 계산한 뒤, 선택한 코스를 차량 내장 내비게이션으로 넘겨줍니다.

<img width="818" height="696" alt="just go 메인 조건 선택 화면" src="https://github.com/user-attachments/assets/7f438bc3-1cb5-4931-a033-8645bd99c059" />

## 로컬 시연 빠른 시작

### 준비 환경

- Windows 11, Docker Desktop
- Android Studio, Android SDK API 34, ADB
- Pleos Connect Emulator `Pleos_Connect_v2.1.0`
- 백엔드 직접 빌드 시 Java 21
- TMAP·공공데이터 API 키가 들어 있는 로컬 `backend/.env`

`.env`, `signing.properties`, 키스토어에는 비밀값이 들어가므로 Git에 올리지 않습니다. 처음 실행할 때는 각 `.env.example`을 복사한 뒤 본인의 로컬 값으로 바꿔 주세요.

```powershell
Copy-Item .\infra\.env.example .\infra\.env
Copy-Item .\backend\.env.example .\backend\.env
```

### 1. PostgreSQL과 백엔드 실행

저장소 루트에서 다음 명령을 실행합니다.

```powershell
cd .\infra
docker compose --env-file .env --profile app up -d --build backend
```

정상 실행 여부는 다음 주소에서 확인합니다.

```text
http://127.0.0.1:18080/actuator/health
```

### 2. 제출 APK 설치

Pleos Connect Emulator를 실행한 뒤 저장소 루트에서 설치합니다.

```powershell
adb install -r ".\apk\공룡과참새_JustGo_release.apk"
```

제출용 Release APK는 에뮬레이터 호스트 별칭인 `http://10.0.2.2:18080`으로 로컬 백엔드에 연결합니다. HTTP 통신은 이 주소에만 열어 두었고, 다른 주소의 평문 통신은 막았습니다. APK만 설치해도 화면은 열리지만 장소·코스 추천과 오늘 행사 조회를 사용하려면 같은 PC에서 백엔드를 실행하고 API 키도 설정해야 합니다.

## 문제 정의

보통 내비게이션은 목적지를 먼저 물어봅니다. `just go`는 그 순서를 바꿨습니다. 어디로 갈지 정하지 못했더라도 지금 쓸 수 있는 시간과 원하는 풍경부터 고를 수 있습니다.

선택이 끝나면 현재 위치에서 출발하는 코스의 예상 주행시간을 실제 도로 기준으로 계산합니다. 왕복은 출발지로 돌아오는 시간까지 포함하고, 편도는 목적지에 도착하는 시간을 기준으로 삼습니다. 여러 후보를 비교해 코스를 고른 뒤에는 차량 내장 내비게이션이 길 안내를 이어받습니다.

기준은 다음과 같이 잡았습니다.

- 출발지는 따로 입력하지 않고 차량의 현재 위치를 사용
- 거리보다 사용자가 실제로 쓸 수 있는 시간을 우선
- 도시·산·바다·강/호수·공원처럼 보고 싶은 풍경으로 후보 탐색
- 한 곳을 정답처럼 제시하기보다 여러 코스를 비교할 수 있게 구성
- 코스 선택 이후의 안내는 Pleos 차량 내장 내비게이션에 맡김

## 서비스 목표

`just go`로 하고 싶은 일은 네 가지입니다.

1. **목적지 고민 줄이기** — 장소를 잘 몰라도 몇 번의 선택으로 출발할 수 있게 만들기
2. **시간에 맞는 코스 찾기** — 거리보다 실제로 쓸 수 있는 드라이브 시간을 먼저 보기
3. **풍경으로 고르기** — 유명 관광지뿐 아니라 일상적인 공원과 자연·도시 장소까지 살펴보기
4. **내비게이션까지 자연스럽게 연결하기** — 추천한 코스를 다시 검색하지 않고 바로 길 안내로 넘기기

## 주요 기능

- 현재 위치 기반 출발지 설정
- 왕복·편도 및 희망 주행 시간 선택
- `어디든`, `도시`, `산`, `바다`, `강/호수`, `공원` 풍경 선택
- 검수 DB와 공공데이터를 결합한 목적지 후보 탐색
- 실제 도로 기준 예상 시간·거리 계산
- 목표 주행 시간에 가까운 추천 코스 최대 10개 비교 및 선택
- 도시 테마에서 예상 도착 시각에 관람 가능한 행사 우선 표시
- 메인 화면에서 오늘 열리는 서울·경기 행사 최대 10개 탐색 및 행사장 길 안내
- Pleos NaviHelper를 통한 차량 내장 내비게이션 연동
- 정차 중 코스를 고르고 주행 중에는 내비게이션 안내에 집중하는 흐름

## 담당 역할

| 담당 | 역할 |
|---|---|
| 김인영 | 개발환경 구성, 백엔드, PostgreSQL, 외부 API 연동, 추천 로직, 인프라 |
| 정도혜 | UI/UX, Android Java/XML 앱, 프론트엔드 로직, Pleos 화면 연동 |
| 공동 | 제품 기획, API 계약, 통합 테스트, 추천 코스 검수, 공모전 제출물 |

## 전체 사용자 흐름

### 1. 조건 선택

앱을 열면 먼저 현재 위치를 확인합니다. 사용자는 차량이 정차한 상태에서 왕복·편도, 드라이브 시간, 풍경을 고릅니다. 선택을 마친 뒤에는 내비게이션 안내에 집중하는 흐름입니다.

### 2. 장소 후보 탐색

장소 후보는 공공데이터, TourAPI, TMAP POI에서 가져옵니다. 서로 다른 형식은 백엔드에서 하나로 맞추고, 자체 DB는 분류를 바로잡거나 부적절한 장소를 제외할 때 사용합니다. 이름과 좌표가 겹치는 곳도 이 단계에서 정리합니다.

### 3. 실제 경로 계산

후보가 정리되면 TMAP 자동차 경로 API로 예상 시간과 거리를 계산합니다. 편도는 현재 위치에서 목적지까지, 왕복은 `출발지 → 목적지 경유 → 출발지` 전체를 한 번에 확인합니다.

직선거리는 후보 범위를 좁힐 때만 씁니다. 화면에 표시하는 시간과 거리는 실제 도로 경로 결과입니다. 처음부터 너무 많은 장소를 호출하지 않도록 검색 범위를 단계적으로 넓히며, 목표 시간의 ±15% 안에 들어오는 코스를 시간 차이가 작은 순서로 최대 10개까지 보여 줍니다. 계산 결과가 비정상적인 경로는 추천에서 뺍니다.

### 4. 코스 선택

추천 카드에는 목적지, 예상 시간과 거리, 목표 시간과의 차이가 표시됩니다. 카드를 누르면 구간별 시간과 내비게이션 시작 버튼을 확인할 수 있습니다. 도시를 선택한 경우에는 예상 도착 시각에 관람할 수 있는 행사를 먼저 보여 주고, 일반 도시 명소는 따로 구분합니다.

<img width="815" height="616" alt="추천 코스 목록 화면" src="https://github.com/user-attachments/assets/e469bb59-489f-484b-8b20-8ba2aa216d5f" />
<img width="805" height="575" alt="추천 코스 상세 화면" src="https://github.com/user-attachments/assets/d6fa68ae-cd57-4bcc-93d0-6ca725be2960" />

### 5. 차량 내비게이션 전달

코스를 고르면 경유지와 목적지를 Pleos NaviHelper에 전달합니다. 그다음 길 안내와 재탐색은 차량 내장 내비게이션이 맡습니다.

<img width="818" height="618" alt="Pleos 차량 내비게이션 전환 화면" src="https://github.com/user-attachments/assets/9bfc48a1-932d-4686-9859-4a61ab181e81" />

## 아키텍처

차량 앱과 추천 서버, 장소 데이터, 외부 경로 서비스를 한곳에 몰아넣지 않고 역할을 나눴습니다.

| 영역 | 책임 |
|---|---|
| Android App | 현재 위치와 사용자 조건 수집, 후보·코스 표시, 사용자 선택, NaviHelper 전달 |
| Recommendation Backend | 입력 검증, 후보 통합·분류·중복 제거, 추천 순위 계산, 외부 API 경계 관리 |
| PostgreSQL | 수동 검수된 장소와 출처·품질 정보, 외부 경로 API 호출량 보호 정보 관리 |
| Public Data Sources | 도시공원과 관광·자연·문화 장소 후보 제공 |
| TMAP | 선택한 후보의 실제 자동차 경로, 예상 시간, 거리 계산 |
| Pleos Navigation | 최종 경로 안내와 주행 중 내비게이션 경험 제공 |

## 목적지 후보를 만드는 방법

장소 목록은 한 군데 데이터만 믿지 않습니다. 각 데이터의 장점을 살려 후보를 모은 뒤 백엔드에서 같은 형식으로 합칩니다.

| 데이터 원천 | 역할 |
|---|---|
| 검수 DB | 잘못된 분류 보정, 부적절한 장소 제외, 외부 API 장애 시 대체 후보 제공 |
| 전국도시공원정보표준데이터 | 관광지에 한정되지 않은 생활권 공원 후보 확장 |
| 전국공연행사정보표준데이터 | 도시 테마의 행사명, 행사장 좌표, 운영시간 제공 |
| 한국관광공사 TourAPI | 도시 랜드마크, 자연, 문화, 관광 장소를 공식 분류 기반으로 탐색 |
| TMAP POI | 공식 주변 카테고리의 공원·주요시설물·문화시설·공연장 후보 보강 |
| TMAP 자동차 경로 API | 목적지 검색이 아닌, 선택된 후보까지의 실제 도로 경로 계산 |

행사 정보는 지방자치단체가 제공하는 「전국공연행사정보표준데이터」를 사용합니다. [공공데이터포털 원본 보기](https://www.data.go.kr/data/15013106/standard.do)

외부 API에서 받은 장소를 DB에 그대로 쌓지는 않습니다. 제공처의 공식 분류를 화면 테마에 연결하고, 분류가 모호한 장소는 이름만 보고 억지로 끼워 넣지 않습니다. DB에는 꼭 필요한 보정 정보와 대체 후보만 둡니다. 외부 경로 응답과 사용자의 이동 이력도 영구 저장하지 않습니다.

## 메인 화면의 오늘 행사

<img width="640" height="360" alt="오늘 행사 배너와 행사장 길 안내 시연" src="https://github.com/user-attachments/assets/5cc5a6df-23e4-481b-bd7a-5c12aba9b69a" />

서울과 경기에서 오늘 열리는 행사를 번갈아 최대 10개까지 가져와 한 장씩 보여 줍니다. 대표 이미지가 있으면 포스터를 크게 쓰고, 없을 때는 공연·전시·축제에 맞는 기본 그림을 대신 표시합니다.

행사가 하나뿐이면 화면을 그대로 유지합니다. 두 개 이상일 때는 자동으로 넘어가며, **다음 행사 보기**를 눌러 직접 넘길 수도 있습니다. 조회는 성공했지만 행사가 없으면 **오늘은 행사가 없어요**, 데이터를 가져오지 못했다면 **행사를 불러오지 못했어요**라고 안내합니다.

정차 중 **행사 찾아가기**를 누르면 행사장 좌표를 Pleos 차량 내비게이션에 전달합니다.

| 지역 | 데이터 원천 | 백엔드 환경변수 |
|---|---|---|
| 서울 | [서울시 문화행사 정보 API](https://data.seoul.go.kr/dataList/OA-15486/A/1/datasetView.do) | `SEOUL_EVENT_API_KEY` |
| 경기 | [한국관광공사 TourAPI 행사정보 조회](https://www.data.go.kr/data/15101578/openapi.do) | `TOUR_API_KEY` |

API 키는 백엔드를 실행하는 PC의 `backend/.env`에 넣습니다. 파일이 없다면 `backend/.env.example`을 복사해 만들면 됩니다. Docker Compose도 같은 파일을 읽습니다. 여러 사람이 같은 백엔드에 연결할 때는 서버 한 곳에만 키를 설정하면 되고, 앱 안에는 키를 넣지 않습니다.

서울 키는 서울 열린데이터광장에서 발급받습니다. 경기 행사는 이용 승인을 받은 TourAPI 키를 사용하며, 실제 값은 서버에만 보관합니다.

## 풍경 테마 분류 기준

화면에서는 `어디든`과 `강/호수`를 하나의 선택지처럼 보여 주지만, 내부 장소 분류는 `CITY`, `MOUNTAIN`, `RIVER`, `LAKE`, `SEA`, `PARK`로 나눠 관리합니다.

| 화면 테마 | 분류 방식 | 주요 데이터 원천 |
|---|---|---|
| 어디든 | 전체 실제 테마를 번갈아 구성해 특정 테마 편중 방지 | 전체 원천 데이터 |
| 도시 | 도착 시 관람 가능한 행사와 일반 도시·역사·문화 명소 | 전국공연행사정보표준데이터 + TourAPI + TMAP POI |
| 산 | 산, 숲, 계곡, 고개, 자연생태·자연공원 계열 장소 | 검수 DB + TourAPI |
| 바다 | 해변, 해안, 섬, 항구·포구, 등대, 갯벌 계열을 먼저 보여 주고 바다 레저도 선택지로 유지 | 검수 DB + TourAPI |
| 강/호수 | 강·폭포와 호수·저수지·습지·댐을 먼저 보여 주고 민물 레저도 선택지로 유지 | 검수 DB + TourAPI |
| 공원 | 도시공원 데이터의 공식 공원구분, TourAPI 공원 분류, TMAP 공원 카테고리 | 도시공원 데이터 + TourAPI + TMAP POI |

예를 들어 이름이 `○○호수공원`이더라도 제공처에서 도시공원으로 분류했다면 `PARK`로 봅니다. TourAPI의 공식 분류가 없거나 어느 테마인지 확실하지 않은 장소는 이름만 보고 판단하지 않고 후보에서 제외합니다.

## 추천 설계의 핵심

### 후보 찾기와 경로 계산은 따로 합니다

먼저 갈 만한 장소를 찾고, 그중 출발지와의 거리가 알맞은 후보만 자동차 경로를 계산합니다. Android 앱은 계산이 끝난 코스를 받아 보여 주며, 운전자가 상세 화면에서 원하는 코스를 고릅니다.

### 직선거리는 필터에만 사용합니다

직선거리는 후보 수를 빠르게 줄이기 위한 내부 기준일 뿐입니다. 화면에 표시되는 예상 시간과 거리는 TMAP이 계산한 실제 도로 경로를 사용합니다.

### 추천 이유를 확인할 수 있게 했습니다

직접 검수한 장소는 테마에 잘 맞는지, 풍경이 괜찮은지, 차량으로 들르기 좋은지를 각각 평가합니다. 출처와 검수 근거도 함께 남겨 단순히 가까운 곳만 추천하지 않도록 했습니다.

### 외부 서비스가 바뀌어도 핵심 로직은 유지됩니다

자동차 경로 제공자는 `DrivingRouteProvider`, 차량 내비게이션은 `NavigationGateway`로 분리했습니다. 나중에 API나 SDK가 바뀌더라도 추천 로직과 화면을 크게 고치지 않기 위한 구조입니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| Vehicle Platform | Android Automotive OS, Pleos Connect, NaviHelper |
| Android | Java, XML, ViewBinding |
| Backend | Java 21, Spring Boot 3.5 |
| Persistence | MyBatis Mapper XML, PostgreSQL 17, Flyway |
| External Data | 공공데이터포털, 서울 열린데이터광장, 한국관광공사 TourAPI, TMAP API |
| Test | JUnit 5, Spring Boot Test, Testcontainers |
| Infrastructure | Docker Compose |

## 저장소 구성

```text
apk/           제출용 서명 Release APK
android-app/   Pleos Connect 차량용 Android 앱
backend/       추천 API, 장소 후보 통합, 외부 서비스 연동
infra/         PostgreSQL 기반 로컬 인프라
docs/          요구사항, API 계약, 데이터·보안·품질 정책
```

## 제출 APK

| 항목 | 값 |
|---|---|
| 제출 예정 경로 | `apk/공룡과참새_JustGo_release.apk` |
| applicationId | `com.kiy3035.justgo` |
| 버전 | `1.0.0` (`versionCode 1`) |
| 빌드 종류 | 서명된 Android Release APK |
| 실행 방식 | Pleos Emulator와 같은 PC에서 로컬 Docker 백엔드 실행 |

Release 서명키와 비밀번호는 저장소에 넣지 않습니다. 로컬의 `android-app/signing.properties`가 설정된 환경에서만 서명 APK를 만들 수 있습니다. 필요한 항목은 `android-app/signing.properties.example`에서 확인할 수 있습니다.

## 테스트 환경

| 항목 | 검증 환경 |
|---|---|
| 운영체제 | Windows 11 |
| Pleos Emulator | `Pleos_Connect_v2.1.0` |
| 에뮬레이터 모델 | `Connect on x86_64 emulator` |
| 화면 규격 | 가로 `2560 × 1440`, `160 dpi` |
| Android | API 34, `minSdk 28`, Java 17 바이트코드 |
| 차량 내비 연동 | NaviHelper `3.0.5` |
| Backend | Java 21, Spring Boot `3.5.16` |
| Database | PostgreSQL `17.11`, Flyway |
| 로컬 인프라 | Docker Compose |

### 소스에서 검증

```powershell
cd .\backend
.\gradlew.bat test --no-daemon

cd ..\android-app
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
.\gradlew.bat testReleaseUnitTest lintRelease assembleRelease --no-daemon
```

`signing.properties` 없이 `assembleRelease`를 실행하면 서명되지 않은 APK가 만들어집니다. 공모전에는 로컬 서명 설정으로 만든, 실제 설치 가능한 Release APK만 제출합니다.

## 현재 구현 범위

- Android에서 현재 위치와 주행 조건으로 추천 API를 호출하고 계산된 코스를 최대 10개 표시
- 도시공원 데이터, TourAPI, TMAP POI와 수동 보정 DB를 결합한 장소 후보 백엔드
- TMAP 실제 자동차 경로와 추천 순위 계산
- 추천 결과 선택 UI와 Pleos NaviHelper 단일 목적지 안내 연결
- 추천 목록·코스 상세 팝업과 도시 행사 데이터 연동
- 서울·경기 오늘 행사 최대 10건 배너와 행사장 내비 직접 전달
- Pleos Emulator에서 단일 목적지 NaviHelper 전달과 내비게이션 화면 전환 검증 완료
- 최대 두 경유지 전달과 실제 주행 상태에서의 조작 제한 전환은 추가 검증 필요
