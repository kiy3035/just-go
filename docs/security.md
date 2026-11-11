# 백엔드·TMAP 보안 기준

> 갱신일: 2026-11-11
>
> 범위: 현재 로컬 개발과 향후 HTTPS 시연 백엔드. 실제 사용자 계정·결제·주행 이력은 아직 범위 밖입니다.

## 1. 보호 대상과 현재 방어선

- `TMAP_APP_KEY`는 `backend/.env`와 서버 환경변수에만 둡니다. Android, 웹 JavaScript, Git, Docker 이미지와 응답에 넣지 않습니다.
- 로컬 Spring Boot는 기본 `127.0.0.1`에만 바인딩합니다. Docker Compose는 컨테이너 안에서만 `0.0.0.0`으로 듣고 호스트 포트는 계속 `127.0.0.1`에 바인딩합니다. 외부 배포에서만 공개 주소를 명시적으로 변경합니다.
- `production` 프로필은 추천 API 접근 토큰이 없거나 예시값·약한 형식이면 서버 시작을 거부합니다.
- `POST /api/v1/routes/recommendations`는 기본적으로 직접 연결 주소당 분당 4회, 서버 전체 동시 2회, 실제 요청 본문 8KiB로 제한합니다. 길이 헤더가 없는 chunked 본문도 같은 상한을 적용합니다.
- TMAP 외부 호출은 추천 1회당 최대 3건이며 PostgreSQL의 서울 날짜 기준 일일 90건 상한을 별도로 적용합니다. 애플리케이션 재시작으로 이 상한은 초기화되지 않습니다.
- `X-Forwarded-For`를 기본 신뢰하지 않습니다. 신뢰할 프록시가 확정되기 전까지 `server.forward-headers-strategy=none`을 유지합니다.
- API 응답은 `Cache-Control: no-store`이며 콘텐츠 추론·프레임 삽입·불필요한 브라우저 권한을 제한하는 보안 헤더를 반환합니다.

## 2. 로컬과 공개 배포의 차이

로컬 개발은 loopback에서만 접근하므로 `ROUTE_API_ACCESS_TOKEN_REQUIRED=false`가 기본입니다. 공개 HTTPS 배포는 다음처럼 `production` 프로필과 별도 접근 토큰을 반드시 사용합니다.

```powershell
# 화면에 생성된 값을 backend/.env 또는 배포 서비스의 비밀 환경변수에만 저장합니다.
[Convert]::ToHexString(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
).ToLowerInvariant()
```

```properties
ROUTE_API_ACCESS_TOKEN=<위에서 생성한 64자리 값>
```

```powershell
$env:SPRING_PROFILES_ACTIVE = "local,tmap,production"
```

클라이언트는 추천 요청에 `X-Just-Go-Client-Token` 헤더를 보냅니다. 이 값은 TMAP 앱 키와 완전히 다른 폐기·교체 가능한 토큰입니다. 저장소, 로그, 화면 캡처에 기록하지 않습니다.

## 3. 중요한 한계와 배포 전 필수 조치

- 모바일 APK에 고정한 접근 토큰은 역공학으로 추출될 수 있으므로 최종 사용자 인증 수단이 아닙니다. 공모전 제한 시연의 1차 방어선으로만 사용하고, 공개 서비스 전에는 사용자 인증 또는 기기 증명과 서버 측 토큰 교환 방식을 별도 설계합니다.
- 현재 속도 제한은 단일 서버 메모리 기준입니다. 서버가 여러 대면 HTTPS 프록시/API 게이트웨이의 공유 제한 또는 승인된 공용 저장소가 필요합니다. Redis나 유료 게이트웨이는 사용자 승인 전 추가하지 않습니다.
- 애플리케이션 필터가 길이 헤더와 실제 수신 바이트를 모두 제한하지만, 공개 배포 시 신뢰할 HTTPS 프록시에도 요청 본문 8KiB 상한을 적용해 느린 업로드와 애플리케이션 도달 전 자원 소비를 이중으로 제한합니다.
- 프록시가 `X-Forwarded-For`를 덮어쓰고 신뢰 프록시 주소를 제한하는 구성이 확인되기 전에는 전달 헤더 기반 IP 제한을 켜지 않습니다.
- 고정된 서버 송신 IP를 확보하면 TMAP 개발자 콘솔의 `Any IP allowed`를 해제하고 해당 IP만 허용합니다. 로컬 개발 중에는 고정 송신 IP가 없어 이 조치를 적용할 수 없습니다.
- HTTPS 종료 지점에서 TLS와 HSTS를 적용합니다. 현재 loopback HTTP 개발 서버에 HSTS를 잘못 적용하지 않습니다.

## 4. 키 노출 의심 시 대응

1. TMAP 개발자 콘솔에서 앱 키를 즉시 재발급합니다.
2. `backend/.env`와 배포 환경변수를 새 키로 교체하고 서버를 재시작합니다.
3. Git 이력·CI 로그·화면 캡처·APK에 기존 키가 포함됐는지 확인합니다.
4. 사용량 통계를 확인하고 의심 호출 시각과 서버 traceId만 조사합니다. 원시 위치나 경로는 새로 저장하지 않습니다.
5. 접근 토큰 노출이면 TMAP 키와 별도로 `ROUTE_API_ACCESS_TOKEN`만 교체합니다.

## 5. 근거와 후속 검증

- [OWASP API4:2023](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/)의 무제한 자원 소비 방지를 기준으로 요청 빈도, 동시 실행, 본문 크기와 제3자 서비스 일일 한도를 겹쳐 적용합니다.
- [Spring Boot 3.5 전달 헤더 전략](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/web/ServerProperties.ForwardHeadersStrategy.html)의 `NONE`을 기본값으로 명시해 위조 가능한 전달 헤더를 신뢰하지 않습니다.
- 공개 배포 전에는 무토큰 401, 분당 제한 429, 과대 본문 413, 일일 TMAP 한도 503, 키·토큰 비노출을 실제 HTTPS 주소에서 재검증합니다.
