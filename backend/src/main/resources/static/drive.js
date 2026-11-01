'use strict';

const PREVIEW_DURATION_MS = 1800;
const form = document.querySelector('#drive-form');
const scene = document.querySelector('.drive-scene');
const customTime = document.querySelector('#custom-time');
const customMinutes = document.querySelector('#customMinutes');
const timeHint = document.querySelector('#time-hint');
const result = document.querySelector('#selection-result');
const summary = document.querySelector('#selection-summary');
const searchButton = document.querySelector('.submit-button');
const buttonLabel = searchButton.querySelector('.button-label');
const buttonSummary = searchButton.querySelector('.button-summary');
const originalLabel = buttonLabel.textContent;
let searchTimer;
const selectedTrip = document.querySelector('#selected-trip');
const selectedTime = document.querySelector('#selected-time');
const selectedPlace = document.querySelector('#selected-place');


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

function showSelection(event) {
    event.preventDefault();
    const value = form.elements.duration.value === 'custom' ? customMinutes.value : form.elements.duration.value;
    const minutes = Number(value);
    if (!Number.isInteger(minutes) || minutes < 10 || minutes > 240 || minutes % 5 !== 0) {
        customMinutes.reportValidity();
        return;
    }

    // Local UI feedback only; no location lookup or route API is invoked.
    summary.textContent = form.elements.tripType.value + ' · ' + minutes + '분 · ' + form.elements.theme.value;
    result.classList.remove('is-visible');
    clearTimeout(searchTimer);
    setSearching(true, summary.textContent);
    searchTimer = setTimeout(() => {
        setSearching(false);
        result.classList.add('is-visible');
    }, PREVIEW_DURATION_MS);
}

form.addEventListener('change', syncForm);
form.addEventListener('submit', showSelection);
customMinutes.addEventListener('input', syncForm);
syncForm();

