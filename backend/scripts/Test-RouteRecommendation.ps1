[CmdletBinding()]
param(
    [ValidatePattern('^https?://')]
    [string]$BaseUrl = 'http://127.0.0.1:18080',

    [ValidateSet('MOCK', 'LIVE')]
    [string]$ExpectedMode = 'MOCK',

    [ValidateSet('ROUND_TRIP', 'ONE_WAY')]
    [string]$TripType = 'ROUND_TRIP',

    [ValidateSet('CITY', 'MOUNTAIN', 'RIVER', 'LAKE', 'SEA')]
    [string]$Theme = 'RIVER',

    [ValidateRange(10, 240)]
    [int]$DesiredMinutes = 60,

    [string]$AccessToken = $env:ROUTE_API_ACCESS_TOKEN,

    [switch]$ConfirmExternalCall
)

$ErrorActionPreference = 'Stop'

# LIVE 검증은 무료 쿼터를 사용하므로 호출 의사를 명시한 경우에만 진행합니다.
if ($ExpectedMode -eq 'LIVE' -and -not $ConfirmExternalCall) {
    throw 'LIVE 검증은 무료 쿼터를 사용합니다. -ConfirmExternalCall을 함께 지정해 주세요.'
}

if ($DesiredMinutes % 5 -ne 0) {
    throw 'DesiredMinutes는 10~240분 범위의 5분 단위여야 합니다.'
}

$normalizedBaseUrl = $BaseUrl.TrimEnd('/')
$requestUri = "$normalizedBaseUrl/api/v1/routes/recommendations"
$traceId = "local-smoke-$([guid]::NewGuid().ToString('N'))"

# 실제 사용자의 위치 대신 서울시청 공개 좌표를 고정 테스트값으로 사용합니다.
$requestBody = @{
    latitude = 37.5665
    longitude = 126.9780
    desiredMinutes = $DesiredMinutes
    tripType = $TripType
    theme = $Theme
} | ConvertTo-Json

try {
    $requestHeaders = @{ 'X-Trace-Id' = $traceId }
    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        # 접근 토큰은 요청 헤더에만 넣고 콘솔이나 예외 메시지에는 출력하지 않습니다.
        $requestHeaders['X-Just-Go-Client-Token'] = $AccessToken
    }
    $response = Invoke-RestMethod `
        -Uri $requestUri `
        -Method Post `
        -ContentType 'application/json' `
        -Headers $requestHeaders `
        -Body $requestBody `
        -TimeoutSec 30
} catch {
    $statusCode = if ($_.Exception.Response) {
        [int]$_.Exception.Response.StatusCode
    } else {
        0
    }
    throw "추천 API 스모크 요청이 실패했습니다. status=$statusCode traceId=$traceId"
}

$routeCount = @($response.routes).Count
$suggestionCount = @($response.suggestions).Count
Write-Output "mode=$($response.mode)"
Write-Output "mock=$($response.mock)"
Write-Output "routeCount=$routeCount"
Write-Output "suggestionCount=$suggestionCount"
Write-Output "traceId=$traceId"

# 지점명·좌표·원시 외부 응답은 출력하지 않고 추천 요약 단위만 확인합니다.
foreach ($route in @($response.routes)) {
    Write-Output (
        'route estimatedMinutes={0} distanceMeters={1} repeatedSegmentPercent={2} stopCount={3}' -f `
            $route.estimatedMinutes,
            $route.distanceMeters,
            $route.repeatedSegmentPercent,
            @($route.stops).Count
    )
}

if ($response.mode -ne $ExpectedMode) {
    throw "예상 모드와 다릅니다. expected=$ExpectedMode actual=$($response.mode) traceId=$traceId"
}

if ($ExpectedMode -eq 'MOCK' -and -not $response.mock) {
    throw "MOCK 응답에 mock=true가 없습니다. traceId=$traceId"
}

if ($ExpectedMode -eq 'LIVE' -and $response.mock) {
    throw "LIVE 응답이 mock=true로 표시됐습니다. traceId=$traceId"
}

if ($ExpectedMode -eq 'LIVE') {
    $calculatedSuggestionCount = @(
        $response.suggestions | Where-Object {
            $_.theme -eq $Theme -and $_.tripType -eq $TripType
        }
    ).Count
    if ($routeCount -eq 0 -and $calculatedSuggestionCount -eq 0) {
        throw "실제 계산 코스나 같은 조건의 대체 시간 제안이 없습니다. traceId=$traceId"
    }
}

Write-Output 'result=PASS'
