# Just Go

> 목적지를 정하지 않아도, 원하는 시간과 풍경만 고르면 출발할 수 있는 드라이브 추천 서비스

`Just Go`는 현대자동차그룹 2026 차량용 앱 개발 공모전을 위해 개발한 Pleos Connect 기반 차량용 Android 앱입니다.

운전하고 싶은 마음은 있지만 목적지를 고르는 과정이 번거로운 순간에 주목했습니다.
사용자는 현재 위치에서 왕복·편도, 희망 주행 시간, 보고 싶은 풍경만 선택합니다. `Just Go`는 조건에 맞는 장소 후보와 실제 자동차 경로를 계산하고, 선택한 코스를 차량 내장 내비게이션으로 전달합니다.

<img width="818" height="696" alt="image" src="https://github.com/user-attachments/assets/7f438bc3-1cb5-4931-a033-8645bd99c059" />


## 로컬 시연 빠른 시작

### 준비 환경

- Windows 11, Docker Desktop
- Android Studio, Android SDK API 34, ADB
- Pleos Connect Emulator `Pleos_Connect_v2.1.0`
- 백엔드 직접 빌드 시 Java 21
- TMAP·공공데이터 API 키가 들어 있는 로컬 `backend/.env`

비밀값이 들어 있는 `.env`, `signing.properties`, 키스토어는 Git에 커밋하지 않습니다. 처음 실행하는 환경에서는 각 `.env.example`을 복사한 뒤 로컬 값으로 교체합니다.

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

제출 Release APK는 에뮬레이터 호스트 별칭 `http://10.0.2.2:18080`으로 로컬 백엔드에 연결합니다. 평문 HTTP는 해당 로컬 주소에만 허용하며 다른 주소의 평문 통신은 차단합니다. 따라서 APK만 단독 실행하면 화면은 열리지만, 장소·코스 추천과 오늘 행사 조회에는 같은 PC에서 실행 중인 백엔드와 API 키 설정이 필요합니다.

## 문제 정의

`Just Go`는 목적지를 정하기 전에 **쓸 수 있는 주행 시간**부터 묻습니다. 운전자가 희망 시간과 왕복·편도를 선택하면 현재 위치에서 출발하는 후보 코스의 예상 주행시간을 실제 도로 기준으로 계산하고, 목표 시간에 가까운 순으로 보여 줍니다. 왕복은 출발지 복귀까지 포함하며, 선택한 코스의 길 안내는 차량 내장 내비게이션이 이어갑니다.

이 흐름에 맞춰 다음과 같이 설계했습니다.

- 목적지를 직접 검색하는 대신 현재 위치를 출발점으로 사용합니다.
- 희망 주행 시간을 경로 추천의 핵심 조건으로 삼습니다.
- 도시·산·바다·강/호수·공원 등 풍경을 기준으로 후보를 탐색합니다.
- 하나의 정답을 강요하지 않고 비교 가능한 후보를 제공해 최종 선택은 운전자에게 맡깁니다.
- 선택 이후의 경로 안내는 별도 내비게이션을 구현하지 않고 Pleos 차량 내장 내비게이션에 연결합니다.

## 서비스 목표

`Just Go`가 해결하려는 목표는 명확합니다.

1. **결정 피로 줄이기** — 사용자가 장소를 몰라도 몇 번의 선택만으로 출발할 수 있게 합니다.
2. **시간 중심 추천** — 거리보다 사용자가 실제로 쓸 수 있는 주행 시간을 우선합니다.
3. **풍경 중심 탐색** — 관광지에 한정하지 않고 일상적인 공원과 자연·도시 장소까지 후보 범위를 넓힙니다.
4. **차량 경험 연결** — 추천에서 끝나지 않고 Pleos NaviHelper를 통해 실제 내비게이션 안내로 이어집니다.

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
- 주행 중 복잡한 조작을 제한하는 차량 UX 정책 적용

## 담당 역할

| 담당 | 역할 |
|---|---|
| 김인영 | 개발환경 구성, 백엔드, PostgreSQL, 외부 API 연동, 추천 로직, 인프라 |
| 정도혜 | UI/UX, Android Java/XML 앱, 프론트엔드 로직, Pleos 화면 연동 |
| 공동 | 제품 기획, API 계약, 통합 테스트, 추천 코스 검수, 공모전 제출물 |

## 전체 사용자 흐름

### 1. 조건 선택

앱은 현재 위치를 확인하고 사용자가 선택한 주행 방식, 희망 시간, 풍경 테마를 입력으로 받습니다. 정차 상태에서 탐색과 선택을 완료하고, 주행 중에는 내비게이션 안내에 집중하도록 설계했습니다.

### 2. 장소 후보 탐색

백엔드는 공공데이터와 TMAP POI를 우선해 하나의 후보 모델로 정규화하고, 자체 DB는 명시적 보정·제외·fallback에 사용합니다. 이후 좌표 유효성, 원천 공식 분류와 화면 테마의 매핑, 출발지와의 거리, 품질 우선순위를 적용하고 이름과 좌표가 겹치는 후보를 제거합니다.

### 3. 실제 경로 계산

백엔드는 선택한 조건에서 장소 후보를 찾고 TMAP 자동차 경로 API로 예상 시간과 거리를 계산합니다. 편도는 일반 자동차 경로 한 번으로 목적지까지 계산하고, 왕복은 `출발지 → 목적지 경유 → 출발지` 다중 경유지 경로 한 번으로 전체와 구간 시간을 계산합니다. 직선거리는 후보를 찾는 검색 반경에만 사용합니다. 유효 코스 3~5개 확보를 목표로 반경을 단계적으로 넓히고, 목표 시간 ±15% 안에서 차이가 작은 순서로 최대 10개를 반환합니다. 거리·시간 조합의 평균속도가 5km/h 미만이거나 130km/h를 초과하면 비정상 경로로 제외합니다.

### 4. 코스 선택

추천 카드에서 목적지, 예상 시간, 거리와 목표 시간 차이를 비교할 수 있습니다. 카드를 선택하면 구간별 예상 시간과 시작 버튼이 있는 상세 화면을 엽니다. 도시 테마에서는 운영시간과 좌표가 확인되고 예상 도착 시 관람 가능한 행사를 먼저 보여 줍니다. 일반 도시 명소는 별도로 표시합니다.

<img width="815" height="616" alt="image" src="https://github.com/user-attachments/assets/e469bb59-489f-484b-8b20-8ba2aa216d5f" />
<img width="805" height="575" alt="image" src="https://github.com/user-attachments/assets/d6fa68ae-cd57-4bcc-93d0-6ca725be2960" />


### 5. 차량 내비게이션 전달

선택한 경유지와 목적지를 Pleos NaviHelper에 전달합니다. 이후 경로 안내와 재탐색은 차량 내장 내비게이션이 담당합니다.

<img width="818" height="618" alt="image" src="https://github.com/user-attachments/assets/9bfc48a1-932d-4686-9859-4a61ab181e81" />


## 아키텍처

`Just Go`는 차량 앱, 추천 백엔드, 장소 데이터, 외부 경로 서비스의 책임을 분리했습니다.

| 영역 | 책임 |
|---|---|
| Android App | 현재 위치와 사용자 조건 수집, 후보·코스 표시, 사용자 선택, NaviHelper 전달 |
| Recommendation Backend | 입력 검증, 후보 통합·분류·중복 제거, 추천 순위 계산, 외부 API 경계 관리 |
| PostgreSQL | 수동 검수된 장소와 출처·품질 정보, 외부 경로 API 호출량 보호 정보 관리 |
| Public Data Sources | 도시공원과 관광·자연·문화 장소 후보 제공 |
| TMAP | 선택한 후보의 실제 자동차 경로, 예상 시간, 거리 계산 |
| Pleos Navigation | 최종 경로 안내와 주행 중 내비게이션 경험 제공 |

> 최종 아키텍처 이미지는 전체 기능과 통합 검증이 완료된 뒤 실제 구현 구조를 기준으로 추가할 예정입니다.

## 목적지 후보를 만드는 방법

장소를 한 데이터 원천에만 의존하지 않습니다. 데이터 특성에 따라 역할을 나누고 백엔드에서 공통 후보 형식으로 합칩니다.

| 데이터 원천 | 역할 |
|---|---|
| 검수 DB | 원천 분류 보정, 제외, 품질 가중치와 외부 API 장애 시 fallback 제공 |
| 전국도시공원정보표준데이터 | 관광지에 한정되지 않은 생활권 공원 후보 확장 |
| 전국공연행사정보표준데이터 | 도시 테마의 행사명, 행사장 좌표, 운영시간 제공 |
| 한국관광공사 TourAPI | 도시 랜드마크, 자연, 문화, 관광 장소를 공식 분류 기반으로 탐색 |
| TMAP POI | 공식 주변 카테고리의 공원·주요시설물·문화시설·공연장 후보 보강 |
| TMAP 자동차 경로 API | 목적지 검색이 아닌, 선택된 후보까지의 실제 도로 경로 계산 |

행사 정보 출처 · 지방자치단체 제공 「전국공연행사정보표준데이터」 (공공데이터포털). [원본 데이터 페이지](https://www.data.go.kr/data/15013106/standard.do)

외부 API 장소를 DB에 복제하지 않습니다. 원천 분류를 그대로 보존해 화면 테마로 명시적으로 매핑하고, 분류가 불명확한 항목은 이름으로 추측하지 않습니다. DB는 수동 보정과 fallback에만 사용합니다. 외부 경로 응답이나 사용자의 이동 이력은 영구 저장하지 않습니다.

## 메인 화면의 오늘 행사

<img width="640" height="360" alt="배너_경로탐색" src="https://github.com/user-attachments/assets/5cc5a6df-23e4-481b-bd7a-5c12aba9b69a" />

오늘 열리는 행사를 서울과 경기에서 번갈아 최대 10개 가져와 한 장씩 보여 줍니다. 대표 이미지를 불러올 수 있으면 포스터를 크게 표시하고, 없으면 공연·전시·축제 그림을 사용합니다. 행사 1개는 고정하고 2개 이상이면 자동 전환과 **다음 행사 보기** 버튼으로 탐색합니다. 두 지역을 정상 조회했는데 결과가 없으면 **오늘은 행사가 없어요**를, 조회에 실패해 보여 줄 행사가 없으면 **행사를 불러오지 못했어요**를 표시합니다.



정차 중 **행사 찾아가기**를 누르면 행사장 좌표를 Pleos 차량 내비게이션에 전달합니다.

| 지역 | 데이터 원천 | 백엔드 환경변수 |
|---|---|---|
| 서울 | [서울시 문화행사 정보 API](https://data.seoul.go.kr/dataList/OA-15486/A/1/datasetView.do) | `SEOUL_EVENT_API_KEY` |
| 경기 | [한국관광공사 TourAPI 행사정보 조회](https://www.data.go.kr/data/15101578/openapi.do) | `TOUR_API_KEY` |

로컬에서 백엔드를 실행하는 PC의 `backend/.env`에 키를 설정합니다. 파일이 없으면 `backend/.env.example`을 복사해 만듭니다. Docker Compose로 백엔드를 실행해도 같은 파일을 읽습니다. 팀원이 같은 백엔드에 연결하면 키는 그 서버에 한 번만 설정하면 됩니다. 배포 시에는 서버의 비밀 환경변수를 사용합니다.

서울 키는 서울 열린데이터광장에서 발급하고, 경기 행사는 해당 서비스 이용 승인을 받은 기존 TourAPI 키를 재사용합니다. 실제 키는 서버에만 보관합니다.

## 풍경 테마 분류 기준

화면의 `어디든`과 `강/호수`는 조회 조건입니다. 실제 장소 후보는 `CITY`, `MOUNTAIN`, `RIVER`, `LAKE`, `SEA`, `PARK`로 정규화합니다.

| 화면 테마 | 분류 방식 | 주요 데이터 원천 |
|---|---|---|
| 어디든 | 전체 실제 테마를 번갈아 구성해 특정 테마 편중 방지 | 전체 원천 데이터 |
| 도시 | 도착 시 관람 가능한 행사와 일반 도시·역사·문화 명소 | 전국공연행사정보표준데이터 + TourAPI + TMAP POI |
| 산 | 산, 숲, 계곡, 고개, 자연생태·자연공원 계열 장소 | 검수 DB + TourAPI |
| 바다 | 해변, 해안, 섬, 항구·포구, 등대, 갯벌 계열을 먼저 보여 주고 바다 레저도 선택지로 유지 | 검수 DB + TourAPI |
| 강/호수 | 강·폭포와 호수·저수지·습지·댐을 먼저 보여 주고 민물 레저도 선택지로 유지 | 검수 DB + TourAPI |
| 공원 | 도시공원 데이터의 공식 공원구분, TourAPI 공원 분류, TMAP 공원 카테고리 | 도시공원 데이터 + TourAPI + TMAP POI |

예를 들어 이름이 `○○호수공원`이어도 원천이 도시공원으로 제공하면 `PARK`로 사용합니다. TourAPI 공식 분류가 없거나 제품 테마와 명확히 대응하지 않는 항목은 이름으로 추측하지 않고 후보에서 제외합니다.

## 추천 설계의 핵심

### 후보 탐색과 경로 계산을 분리했습니다

장소 후보를 먼저 조회한 뒤 거리 구간별로 선별한 후보의 자동차 경로를 계산합니다. Android는 계산이 끝난 코스를 받아 표시하며, 운전자가 상세 화면에서 원하는 코스를 시작합니다.

### 직선거리와 도로거리를 구분합니다

직선거리는 후보 수를 줄이기 위한 내부 필터로만 사용합니다. 사용자에게 보여 주는 예상 시간과 거리는 TMAP이 계산한 실제 도로 경로 결과를 사용합니다.

### 추천 품질을 설명 가능한 값으로 관리합니다

검수 장소는 테마 적합성, 경관 품질, 차량 경유지 적합성을 각각 평가합니다. 출처와 검수 근거도 함께 관리해 단순한 거리순 추천보다 품질을 우선할 수 있도록 설계했습니다.

### 외부 서비스와 차량 SDK를 교체 가능한 경계로 감쌌습니다

자동차 경로 제공자는 `DrivingRouteProvider`, 차량 내비게이션은 `NavigationGateway` 뒤에 분리했습니다. 특정 외부 API나 SDK의 세부 구현이 추천 도메인과 화면 로직에 직접 퍼지지 않도록 구성했습니다.

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
| 파일 경로 | `apk/공룡과참새_JustGo_release.apk` |
| applicationId | `com.kiy3035.justgo` |
| 버전 | `1.0.0` (`versionCode 1`) |
| 빌드 종류 | 서명된 Android Release APK |
| 실행 방식 | Pleos Emulator와 같은 PC에서 로컬 Docker 백엔드 실행 |

Release 서명키와 비밀번호는 저장소에 포함하지 않습니다. 로컬 `android-app/signing.properties`가 설정된 환경에서만 서명된 APK가 만들어집니다. 템플릿은 `android-app/signing.properties.example`을 참고합니다.

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

`assembleRelease`는 `signing.properties`가 없으면 서명되지 않은 APK를 만듭니다. 공모전 제출에는 로컬 서명 설정으로 생성한 실행 가능한 Release APK만 사용합니다.

## 현재 구현 범위

- Android에서 현재 위치와 주행 조건으로 추천 API를 호출하고 계산된 코스를 최대 10개 표시
- 도시공원 데이터, TourAPI, TMAP POI와 수동 보정 DB를 결합한 장소 후보 백엔드
- TMAP 실제 자동차 경로와 추천 순위 계산
- 추천 결과 선택 UI와 Pleos NaviHelper 단일 목적지 안내 연결
- 추천 목록·코스 상세 팝업과 도시 행사 데이터 연동
- 서울·경기 오늘 행사 최대 10건 배너와 행사장 내비 직접 전달
- Pleos Emulator에서 단일 목적지 NaviHelper 전달과 Navigation 화면 전환 검증 완료
- 최대 두 경유지 전달과 실제 주행 상태의 제한 전환은 `NOT VERIFIED`
