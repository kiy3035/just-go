'use strict';

const CANDIDATE_TIMEOUT_MS = 20000;
const ROUTE_TIMEOUT_MS = 45000;
const form = document.querySelector('#drive-form');
const scene = document.querySelector('.drive-scene');
const customTime = document.querySelector('#custom-time');
const customMinutes = document.querySelector('#customMinutes');
const timeHint = document.querySelector('#time-hint');
const searchButton = document.querySelector('.submit-button');
const buttonLabel = searchButton.querySelector('.button-label');
const buttonSummary = searchButton.querySelector('.button-summary');
const originalLabel = buttonLabel.textContent;
const selectedTrip = document.querySelector('#selected-trip');
const selectedTime = document.querySelector('#selected-time');
const selectedPlace = document.querySelector('#selected-place');
const resultDialog = document.querySelector('#result-dialog');
const resultTitle = document.querySelector('#result-title');
const resultSummary = document.querySelector('#result-summary');
const resultStatus = document.querySelector('#result-status');
const resultOptions = document.querySelector('#result-options');
const resultClose = document.querySelector('#result-close');
const resultCancel = document.querySelector('#result-cancel');
const resultConfirm = document.querySelector('#result-confirm');
let selectedCandidate = null;
let activeRequestId = 0;
let activeAbortController = null;


function syncForm() {
    const theme = form.querySelector('input[name="theme"]:checked');
    scene.dataset.theme = theme.id;
    selectedTrip.textContent = form.elements.tripType.value;
    const minutes = form.elements.duration.value === 'custom' ? customMinutes.value : form.elements.duration.value;
    selectedTime.textContent = minutes ? minutes + '분' : '시간 입력';
    selectedPlace.textContent = theme.value;
    const custom = form.elements.duration.value === 'custom';
    customTime.classList.toggle('is-visible', custom);
    customMinutes.disabled = !custom;
    timeHint.textContent = (form.elements.tripType.value === '왕복' ? '출발지 복귀' : '목적지 도착') + '까지 · 체류 시간 제외';
}

function setSearching(searching, selection = '') {
    searchButton.classList.toggle('is-searching', searching);
    searchButton.disabled = searching;
    if (searching) {
        searchButton.setAttribute('aria-busy', 'true');
    } else {
        searchButton.removeAttribute('aria-busy');
    }
    buttonLabel.textContent = searching ? '좋은 길을 찾고 있어요…' : originalLabel;
    buttonSummary.textContent = selection;
    buttonSummary.hidden = !searching;
}

function apiTheme() {
    return form.querySelector('input[name="theme"]:checked').id.toUpperCase();
}

function apiTripType() {
    return form.elements.tripType.value === '왕복' ? 'ROUND_TRIP' : 'ONE_WAY';
}

function selectedMinutes() {
    const value = form.elements.duration.value === 'custom' ? customMinutes.value : form.elements.duration.value;
    const minutes = Number(value);
    if (!Number.isInteger(minutes) || minutes < 10 || minutes > 240 || minutes % 5 !== 0) {
        customMinutes.reportValidity();
        return null;
    }
    return minutes;
}

function conditions(minutes) {
    return form.elements.tripType.value + ' · ' + minutes + '분 · ' + form.elements.theme.value;
}

function openResult(title, summary, status) {
    resultTitle.textContent = title;
    resultSummary.textContent = summary;
    resultStatus.textContent = status;
    resultOptions.replaceChildren();
    selectedCandidate = null;
    resultConfirm.hidden = true;
    resultConfirm.disabled = true;
    if (!resultDialog.open) resultDialog.showModal();
}

function closeResult() {
    activeRequestId++;
    cancelActiveRequest();
    if (resultDialog.open) resultDialog.close();
}

function cancelActiveRequest() {
    if (activeAbortController) activeAbortController.abort();
    activeAbortController = null;
}

function optionButton(primary, secondary, onSelect) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'result-option';
    button.textContent = primary;
    const detail = document.createElement('small');
    detail.textContent = secondary;
    button.append(detail);
    button.addEventListener('click', () => {
        resultOptions.querySelectorAll('.result-option').forEach(item => item.classList.remove('is-selected'));
        button.classList.add('is-selected');
        onSelect();
    });
    return button;
}

function currentPosition() {
    return new Promise((resolve, reject) => {
        if (!navigator.geolocation) {
            reject(new Error('이 브라우저에서는 현재 위치를 사용할 수 없습니다.'));
            return;
        }
        navigator.geolocation.getCurrentPosition(resolve, () => {
            reject(new Error('현재 위치를 확인하지 못했습니다. 브라우저 위치 권한을 확인해 주세요.'));
        }, {enableHighAccuracy: true, timeout: 10000, maximumAge: 30000});
    });
}

async function readResponse(response) {
    let body = null;
    try { body = await response.json(); } catch (ignored) { }
    if (!response.ok) {
        const error = new Error(body?.message || '서버 요청에 실패했습니다.');
        error.code = body?.code;
        throw error;
    }
    return body;
}

async function requestJson(url, options, timeoutMillis) {
    cancelActiveRequest();
    const controller = new AbortController();
    activeAbortController = controller;
    const timeout = setTimeout(() => controller.abort(), timeoutMillis);
    try {
        return await readResponse(await fetch(url, {...options, signal: controller.signal}));
    } catch (error) {
        if (error.name === 'AbortError') {
            throw new Error('서버 응답 시간이 초과됐습니다. 다시 시도해 주세요.');
        }
        throw error;
    } finally {
        clearTimeout(timeout);
        if (activeAbortController === controller) activeAbortController = null;
    }
}

async function loadCandidates(search) {
    const query = new URLSearchParams({
        latitude: search.latitude,
        longitude: search.longitude,
        desiredMinutes: search.minutes,
        tripType: search.tripType,
        theme: search.theme,
        limit: 50
    });
    return requestJson('/api/v1/places/candidates?' + query, {
        headers: {'Accept': 'application/json'},
        cache: 'no-store'
    }, CANDIDATE_TIMEOUT_MS);
}

async function loadRoutes(search, candidateToken) {
    return requestJson('/api/v1/routes/recommendations', {
        method: 'POST',
        headers: {'Accept': 'application/json', 'Content-Type': 'application/json'},
        cache: 'no-store',
        body: JSON.stringify({
            latitude: search.latitude,
            longitude: search.longitude,
            desiredMinutes: search.minutes,
            tripType: search.tripType,
            theme: search.theme,
            candidateToken
        })
    }, ROUTE_TIMEOUT_MS);
}

function themeLabel(theme) {
    return {
        ANYWHERE: '어디든', CITY: '도시', MOUNTAIN: '산', SEA: '바다',
        WATER: '강/호수', RIVER: '강', LAKE: '호수', PARK: '공원'
    }[theme] || theme;
}

function renderCandidates(search, response) {
    if (!Array.isArray(response.candidates) || response.candidates.length === 0) {
        openResult('주변 장소를 찾지 못했어요', search.summary, '시간이나 풍경을 바꿔 다시 검색해 주세요.');
        return;
    }
    openResult('어디로 달릴까요?', search.summary,
        response.partial ? '일부 장소 데이터 연결이 지연되어 현재 확인된 후보만 보여드려요.'
            : '장소를 선택하면 실제 도로 시간과 거리를 계산합니다.');
    resultConfirm.hidden = false;
    resultConfirm.textContent = '이 장소로 코스 찾기';
    for (const candidate of response.candidates) {
        const distance = (candidate.straightLineDistanceMeters / 1000).toFixed(1) + ' km';
        resultOptions.append(optionButton(candidate.name,
            themeLabel(candidate.theme) + ' · 직선거리 ' + distance, () => {
                selectedCandidate = candidate;
                resultConfirm.disabled = false;
            }));
    }
    resultConfirm.onclick = async () => {
        if (!selectedCandidate) return;
        const requestId = search.requestId;
        resultConfirm.disabled = true;
        resultStatus.textContent = '선택한 장소의 실제 코스를 계산하고 있어요…';
        try {
            const response = await loadRoutes(search, selectedCandidate.candidateToken);
            if (requestId === activeRequestId && resultDialog.open) {
                renderRoutes(search, selectedCandidate, response);
            }
        } catch (error) {
            if (requestId !== activeRequestId || !resultDialog.open) return;
            resultStatus.textContent = error.code === 'INVALID_CANDIDATE_TOKEN'
                ? '선택한 장소가 만료됐습니다. 장소를 다시 검색해 주세요.' : error.message;
            resultConfirm.disabled = false;
        }
    };
}

function renderRoutes(search, candidate, response) {
    const routes = response.routes?.length ? response.routes : response.alternativeRoutes || [];
    openResult('추천 코스', search.summary + ' · ' + candidate.name,
        routes.length ? '웹에서는 경로 결과를 확인할 수 있습니다. 내비 시작은 Pleos 앱에서 진행해 주세요.'
            : '계산된 코스가 없습니다. 다른 장소를 선택해 주세요.');
    for (const route of routes) {
        resultOptions.append(optionButton(route.title,
            route.estimatedMinutes + '분 · ' + (route.distanceMeters / 1000).toFixed(1) + ' km', () => {}));
    }
}

async function showSelection(event) {
    event.preventDefault();
    const minutes = selectedMinutes();
    if (minutes === null) return;
    const summary = conditions(minutes);
    const requestId = ++activeRequestId;
    cancelActiveRequest();
    setSearching(true, summary);
    try {
        const position = await currentPosition();
        const search = {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            minutes,
            tripType: apiTripType(),
            theme: apiTheme(),
            summary,
            requestId
        };
        const response = await loadCandidates(search);
        if (requestId === activeRequestId) renderCandidates(search, response);
    } catch (error) {
        if (requestId === activeRequestId) openResult('추천 요청 실패', summary, error.message);
    } finally {
        if (requestId === activeRequestId) setSearching(false);
    }
}

form.addEventListener('change', syncForm);
form.addEventListener('submit', showSelection);
customMinutes.addEventListener('input', syncForm);
resultClose.addEventListener('click', closeResult);
resultCancel.addEventListener('click', closeResult);
resultDialog.addEventListener('cancel', event => {
    event.preventDefault();
    closeResult();
});
syncForm();

