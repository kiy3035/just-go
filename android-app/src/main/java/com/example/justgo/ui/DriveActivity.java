package com.example.justgo.ui;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import com.example.justgo.data.CurrentLocationProvider;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.example.justgo.BuildConfig;
import com.example.justgo.R;
import com.example.justgo.data.DriveSelection;
import com.example.justgo.data.RouteRecommendationClient;
import com.example.justgo.data.RouteRecommendationClient.Recommendation;
import com.example.justgo.databinding.ActivityDriveBinding;
import com.example.justgo.pleos.NavigationGateway;
import com.example.justgo.pleos.PleosNavigationGateway;
import com.example.justgo.pleos.DrivingRestrictionsMonitor;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DriveActivity extends Activity {
    private static final String SEARCH_PROMPT = "이 조건으로 코스 찾기 →";
    private static final int LOCATION_PERMISSION_REQUEST = 1;
    private static final long PREFETCHED_LOCATION_MAX_AGE_NANOS = 30_000_000_000L;
    private final ExecutorService routeExecutor = Executors.newSingleThreadExecutor();
    private final RouteRecommendationClient routeClient =
            new RouteRecommendationClient(BuildConfig.ROUTE_API_BASE_URL);
    private ActivityDriveBinding binding;
    private CurrentLocationProvider locationProvider;
    private NavigationGateway navigationGateway;
    private DrivingRestrictionsMonitor restrictionsMonitor;
    private RouteRecommendationClient.Route selectedRoute;
    private boolean navigationConnectionFailed;
    private boolean drivingRestricted;
    private boolean searchInProgress;
    private boolean liveRoutesDisplayed;
    private boolean searchAfterLocationPermission;
    private Location prefetchedLocation;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        locationProvider = new CurrentLocationProvider(this);
        navigationGateway = new PleosNavigationGateway(this);
        restrictionsMonitor = new DrivingRestrictionsMonitor(this);
        binding = ActivityDriveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.findRoute.setOnClickListener(v -> chooseOrigin());
        binding.startNavigation.setOnClickListener(v -> startNavigation());
        binding.routeChoices.setOnCheckedChangeListener((group, checkedId) -> {
            View checked = group.findViewById(checkedId);
            selectedRoute = checked == null ? null : (RouteRecommendationClient.Route) checked.getTag();
            refreshNavigationControl();
        });
        binding.theme.setOnCheckedChangeListener((group, id) -> { syncScenery(); syncConditions(); });
        binding.duration.setOnCheckedChangeListener((g, id) -> { syncCustomInput(); syncConditions(); });
        binding.tripMode.setOnCheckedChangeListener((g, id) -> { updateTimeHint(); syncConditions(); });
        binding.customMinutes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { syncConditions(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        if (saved != null) {
            binding.tripMode.check(saved.getInt("trip", R.id.roundTrip));
            binding.duration.check(saved.getInt("duration", R.id.time60));
            binding.theme.check(saved.getInt("theme", R.id.anywhere));
            binding.customMinutes.setText(saved.getString("minutes", "90"));
        }
        syncCustomInput();
        updateTimeHint();
        syncScenery();
        syncConditions();
        initializeNavigation();
        requestLocationPermissionOnStartup();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissionOnStartup() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        }
    }

    private void prefetchCurrentLocation() {
        if (!hasLocationPermission()) return;
        locationProvider.request(new CurrentLocationProvider.Callback() {
            @Override public void onLocation(Location location) {
                prefetchedLocation = location;
            }

            @Override public void onError(String message) {
                prefetchedLocation = null;
            }
        });
    }

    private Location freshPrefetchedLocation() {
        if (prefetchedLocation == null) return null;
        long age = SystemClock.elapsedRealtimeNanos()
                - prefetchedLocation.getElapsedRealtimeNanos();
        if (age < 0 || age > PREFETCHED_LOCATION_MAX_AGE_NANOS) {
            prefetchedLocation = null;
        }
        return prefetchedLocation;
    }

    private void initializeNavigation() {
        navigationGateway.initialize(new NavigationGateway.Listener() {
            @Override public void onConnected() {
                runOnUiThread(() -> {
                    navigationConnectionFailed = false;
                    refreshNavigationControl();
                });
            }

            @Override public void onConnectionFailed() {
                runOnUiThread(() -> {
                    navigationConnectionFailed = true;
                    refreshNavigationControl();
                });
            }

            @Override public void onRouteStarted() {
                showNavigationStatus("차량 내비가 경로 탐색을 시작했습니다.");
            }

            @Override public void onRoutingCompleted() {
                runOnUiThread(() -> {
                    showNavigationStatusOnUi("차량 내비에서 경로 탐색을 완료했습니다.");
                    if (binding != null) {
                        binding.startNavigation.setEnabled(!drivingRestricted && selectedRoute != null);
                    }
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    showNavigationStatusOnUi(message);
                    if (binding != null) {
                        binding.startNavigation.setEnabled(
                                !drivingRestricted && navigationGateway.isConnected()
                                        && selectedRoute != null);
                    }
                });
            }
        });
    }
    private RiversideSceneView.Theme selectedTheme() {
        if (binding.city.isChecked()) return RiversideSceneView.Theme.CITY;
        if (binding.mountain.isChecked()) return RiversideSceneView.Theme.MOUNTAIN;
        if (binding.sea.isChecked()) return RiversideSceneView.Theme.SEA;
        if (binding.water.isChecked()) return RiversideSceneView.Theme.WATER;
        if (binding.park.isChecked()) return RiversideSceneView.Theme.PARK;
        return RiversideSceneView.Theme.ANYWHERE;
    }

    private void syncScenery() {
        binding.scenery.setTheme(selectedTheme());
    }
    // 클릭 전 요약과 결과 요약은 구분한다. 조건 변경은 화면 표시만 갱신하며 API를 호출하지 않는다.
    private void syncConditions() {
        binding.selectedTrip.setText(binding.roundTrip.isChecked() ? "왕복" : "편도");
        String minutes = binding.timeCustom.isChecked() ? binding.customMinutes.getText().toString()
            : binding.time30.isChecked() ? "30" : binding.time120.isChecked() ? "120" : "60";
        binding.selectedTime.setText(minutes.isEmpty() ? "시간 입력" : minutes + "분");
        binding.selectedPlace.setText(selectedTheme().getLabel());
    }

    private void syncCustomInput() {
        boolean custom = binding.timeCustom.isChecked();
        binding.customTime.setVisibility(custom ? View.VISIBLE : View.GONE);
        binding.customMinutes.setEnabled(custom && !drivingRestricted);
    }
    private void updateTimeHint() {
        binding.timeHint.setText((binding.roundTrip.isChecked() ? "출발지 복귀" : "목적지 도착") + "까지 · 체류 시간 제외");
    }
    private void chooseOrigin() {
        if (drivingRestricted) return;
        if (!hasLocationPermission()) {
            searchAfterLocationPermission = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        if (BuildConfig.ROUTE_API_BASE_URL.isEmpty()) {
            renderResult("추천 서비스 연결 필요", "이 빌드에 추천 서버 주소가 설정되지 않았습니다.");
            return;
        }
        int minutes;
        try {
            minutes = DriveSelection.parseMinutes(binding.timeCustom.isChecked()
                ? binding.customMinutes.getText().toString()
                : binding.time30.isChecked() ? "30" : binding.time120.isChecked() ? "120" : "60");
        } catch (IllegalArgumentException e) {
            binding.customMinutes.setError(e.getMessage());
            binding.customMinutes.requestFocus();
            return;
        }
        enterOrigin(minutes, binding.roundTrip.isChecked(), selectedTheme().name());
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != LOCATION_PERMISSION_REQUEST) return;
        boolean continueSearch = searchAfterLocationPermission;
        searchAfterLocationPermission = false;
        if (hasLocationPermission()) {
            if (continueSearch) chooseOrigin();
            else prefetchCurrentLocation();
        } else if (continueSearch) {
            renderResult("위치 권한 필요", "현재 위치에서 코스를 찾으려면 위치 권한을 허용해 주세요.");
        }
    }

    private void enterOrigin(int minutes, boolean roundTrip, String theme) {
        clearRouteResults();
        searchInProgress = true;
        binding.selectionResult.setVisibility(View.GONE);
        binding.findRoute.setEnabled(false);
        binding.findRoute.setText("현재 위치를 확인하고 있어요…");
        Location location = freshPrefetchedLocation();
        if (location != null) {
            requestRecommendations(location.getLatitude(), location.getLongitude(),
                    minutes, roundTrip, theme);
            return;
        }
        locationProvider.request(new CurrentLocationProvider.Callback() {
            @Override public void onLocation(Location location) {
                if (binding == null) return;
                prefetchedLocation = location;
                requestRecommendations(location.getLatitude(), location.getLongitude(),
                        minutes, roundTrip, theme);
            }

            @Override public void onError(String message) {
                renderResult("현재 위치 확인 필요", message);
            }
        });
    }

    private void requestRecommendations(double latitude, double longitude, int minutes,
                                        boolean roundTrip, String theme) {
        String tripType = roundTrip ? "ROUND_TRIP" : "ONE_WAY";
        String conditions = (roundTrip ? "왕복" : "편도") + " · " + minutes + "분 · " + themeLabel(theme);
        binding.selectionResult.setVisibility(View.GONE);
        binding.findRoute.setActivated(true);
        binding.findRoute.setEnabled(false);
        String prefix = "좋은 길을 찾고 있어요…   ";
        SpannableString label = new SpannableString(prefix + conditions);
        // 로딩 중에도 선택 조건의 굵기를 유지하고 크기와 색상만 낮춘다.
        label.setSpan(new StyleSpan(Typeface.BOLD), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new RelativeSizeSpan(.65f), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(0xFFD8E8FF), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.findRoute.setText(label);
        binding.findRoute.setContentDescription(prefix + conditions);
        routeExecutor.execute(() -> {
            try {
                Recommendation result = routeClient.recommend(latitude, longitude, minutes,
                        tripType, theme);
                runOnUiThread(() -> {
                    if (binding != null) showRecommendation(conditions, result);
                });
            } catch (IOException error) {
                String message = describeError(error);
                runOnUiThread(() -> {
                    if (binding != null) {
                        renderResult("추천 요청 실패", conditions + "\n\n" + message);
                    }
                });
            }
        });
    }
    private void resetSearchButton() {
        searchInProgress = false;
        binding.findRoute.setActivated(false);
        binding.findRoute.setEnabled(!drivingRestricted);
        binding.findRoute.setText(SEARCH_PROMPT);
        binding.findRoute.setContentDescription(null);
    }
    @Override protected void onStart() {
        super.onStart();
        if (hasLocationPermission()) prefetchCurrentLocation();
        restrictionsMonitor.start(restricted -> runOnUiThread(
                () -> applyDrivingRestrictions(restricted)));
    }

    @Override protected void onStop() {
        restrictionsMonitor.stop();
        if (locationProvider.cancel()) resetSearchButton();
        super.onStop();
    }

    @Override protected void onDestroy() {
        locationProvider.cancel();
        routeExecutor.shutdownNow();
        restrictionsMonitor.stop();
        navigationGateway.release();
        binding = null;
        super.onDestroy();
    }

    private void showRecommendation(String conditions, Recommendation result) {
        clearRouteResults();
        liveRoutesDisplayed = result.mode() == RouteRecommendationClient.Mode.LIVE;
        String heading = switch (result.mode()) {
            case MOCK -> "MOCK · 개발용 추천 예시";
            case LIVE -> result.routes().isEmpty() ? "추천 코스 없음" : "추천 코스";
            case UNAVAILABLE -> "추천 서비스 사용 불가";
        };
        StringBuilder details = new StringBuilder(conditions);
        for (RouteRecommendationClient.Route route : result.routes()) {
            RadioButton choice = new RadioButton(this, null, 0, R.style.RouteChoice);
            choice.setId(View.generateViewId());
            choice.setTag(route);
            choice.setText(routeSummary(route));
            choice.setEnabled(result.mode() == RouteRecommendationClient.Mode.LIVE
                    && !route.stops().isEmpty());
            choice.setLayoutParams(new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT));
            binding.routeChoices.addView(choice);
        }
        binding.routeChoices.setVisibility(result.routes().isEmpty() ? View.GONE : View.VISIBLE);
        refreshRouteChoiceAvailability();
        binding.startNavigation.setVisibility(
                result.mode() == RouteRecommendationClient.Mode.LIVE && !result.routes().isEmpty()
                        ? View.VISIBLE : View.GONE);
        if (!result.suggestions().isEmpty()) {
            details.append("\n\n다시 검색할 조건");
            for (RouteRecommendationClient.Suggestion suggestion : result.suggestions()) {
                details.append("\n• ").append(suggestion.tripType().equals("ROUND_TRIP") ? "왕복" : "편도")
                        .append(" · ").append(suggestion.minutes()).append("분 · ").append(themeLabel(suggestion.theme()))
                        .append("\n").append(suggestion.reason());
            }
        }
        if (!result.notice().trim().isEmpty()) {
            details.append("\n\n").append(result.notice());
        }
        renderResult(heading, details.toString());
        refreshNavigationControl();
    }

    private String routeSummary(RouteRecommendationClient.Route route) {
        StringBuilder summary = new StringBuilder(route.title());
        summary.append(String.format(Locale.KOREA, "\n%d분 · %.1f km",
                route.minutes(), route.meters() / 1000.0));
        for (RouteRecommendationClient.Stop stop : route.stops()) {
            summary.append("\n").append(stop.sequence()).append(". ")
                    .append(stop.type() == RouteRecommendationClient.StopType.WAYPOINT
                            ? "경유 · " : "도착 · ")
                    .append(stop.name());
        }
        summary.append("\n").append(route.reason());
        return summary.toString();
    }

    private void startNavigation() {
        if (drivingRestricted) return;
        if (selectedRoute == null) {
            showNavigationStatusOnUi("먼저 출발할 코스를 선택해 주세요.");
            return;
        }
        List<NavigationGateway.Stop> stops = new ArrayList<>();
        for (RouteRecommendationClient.Stop stop : selectedRoute.stops()) {
            stops.add(new NavigationGateway.Stop(
                    stop.sequence(),
                    stop.type() == RouteRecommendationClient.StopType.WAYPOINT
                            ? NavigationGateway.StopType.WAYPOINT
                            : NavigationGateway.StopType.DESTINATION,
                    stop.name(), stop.latitude(), stop.longitude()));
        }
        binding.startNavigation.setEnabled(false);
        showNavigationStatusOnUi("선택한 코스를 차량 내비로 전달하고 있어요…");
        navigationGateway.startRoute(stops);
    }

    private void refreshNavigationControl() {
        if (binding == null || binding.startNavigation.getVisibility() != View.VISIBLE) return;
        boolean connected = navigationGateway.isConnected();
        binding.startNavigation.setEnabled(!drivingRestricted && connected && selectedRoute != null);
        if (navigationConnectionFailed) {
            showNavigationStatusOnUi("Pleos Navigation 서비스에 연결하지 못했습니다. 에뮬레이터와 SDK 권한을 확인해 주세요.");
        } else if (!connected) {
            showNavigationStatusOnUi("차량 내비 연결을 확인하고 있습니다.");
        } else if (selectedRoute == null) {
            showNavigationStatusOnUi("내비로 출발할 코스를 하나 선택해 주세요.");
        } else {
            binding.navigationStatus.setVisibility(View.GONE);
        }
    }

    private void showNavigationStatus(String message) {
        runOnUiThread(() -> showNavigationStatusOnUi(message));
    }

    private void showNavigationStatusOnUi(String message) {
        if (binding == null) return;
        binding.navigationStatus.setText(message);
        binding.navigationStatus.setVisibility(View.VISIBLE);
    }

    private void clearRouteResults() {
        if (binding == null) return;
        liveRoutesDisplayed = false;
        selectedRoute = null;
        binding.routeChoices.clearCheck();
        binding.routeChoices.removeAllViews();
        binding.routeChoices.setVisibility(View.GONE);
        binding.startNavigation.setVisibility(View.GONE);
        binding.startNavigation.setEnabled(false);
        binding.navigationStatus.setVisibility(View.GONE);
    }

    private void applyDrivingRestrictions(boolean restricted) {
        if (binding == null) return;
        drivingRestricted = restricted;
        binding.drivingRestrictionNotice.setVisibility(restricted ? View.VISIBLE : View.GONE);
        binding.scenery.setMotionAllowed(!restricted);
        setChildrenEnabled(binding.tripMode, !restricted);
        setChildrenEnabled(binding.duration, !restricted);
        setChildrenEnabled(binding.theme, !restricted);
        binding.customMinutes.setEnabled(!restricted && binding.timeCustom.isChecked());
        binding.findRoute.setEnabled(!restricted && !searchInProgress);
        refreshRouteChoiceAvailability();
        refreshNavigationControl();
        if (restricted) {
            if (locationProvider.cancel()) resetSearchButton();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);
        }
    }

    private void refreshRouteChoiceAvailability() {
        for (int index = 0; index < binding.routeChoices.getChildCount(); index++) {
            View child = binding.routeChoices.getChildAt(index);
            RouteRecommendationClient.Route route =
                    (RouteRecommendationClient.Route) child.getTag();
            child.setEnabled(!drivingRestricted && liveRoutesDisplayed
                    && route != null && !route.stops().isEmpty());
        }
    }

    private static void setChildrenEnabled(ViewGroup group, boolean enabled) {
        for (int index = 0; index < group.getChildCount(); index++) {
            group.getChildAt(index).setEnabled(enabled);
        }
    }

    private static String themeLabel(String theme) {
        return switch (theme) {
            case "ANYWHERE" -> "어디든";
            case "WATER" -> "강/호수";
            case "PARK" -> "공원";
            case "CITY" -> "도시";
            case "MOUNTAIN" -> "산";
            case "RIVER" -> "강";
            case "LAKE" -> "호수";
            case "SEA" -> "바다";
            default -> theme;
        };
    }

    private String describeError(IOException error) {
        if (error instanceof RouteRecommendationClient.HttpError http) {
            return switch (http.status) {
                case 401, 403 -> "추천 서버의 접근 권한 설정을 확인해 주세요.";
                case 429 -> "추천 요청 한도에 도달했습니다. 잠시 후 다시 검색해 주세요.";
                case 400 -> "서버가 입력 조건을 받지 못했습니다. 출발지와 주행 조건을 확인해 주세요.";
                case 502, 503 -> "경로 제공 서비스가 응답하지 못했습니다. 잠시 후 다시 검색해 주세요.";
                default -> "추천 서버에 오류가 발생했습니다. 잠시 후 다시 검색해 주세요.";
            };
        }
        if (error instanceof RouteRecommendationClient.InvalidResponse) {
            return "추천 서버 응답을 읽지 못했습니다. 잠시 후 다시 검색해 주세요.";
        }
        if (error instanceof SocketTimeoutException) {
            return "추천 서버 응답 시간이 초과됐습니다. 다시 검색해 주세요.";
        }
        return "추천 서버에 연결할 수 없습니다. 서버 실행과 연결 설정을 확인한 뒤 다시 검색해 주세요.";
    }

    private void renderResult(String title, String details) {
        if (binding == null) return;
        resetSearchButton();
        binding.resultTitle.setText(title);
        binding.selectionSummary.setText(details);
        binding.selectionResult.setVisibility(View.VISIBLE);
        binding.selectionResult.post(() -> {
            if (binding == null) return;
            View result = binding.selectionResult;
            int visibleHeight = Math.min(result.getHeight(), binding.scroll.getHeight());
            // 짧은 오류·추천 결과는 제목뿐 아니라 카드 본문까지 차량 하단 바 위로 올립니다.
            result.requestRectangleOnScreen(
                    new Rect(0, 0, result.getWidth(), visibleHeight), true);
        });
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("trip", binding.tripMode.getCheckedRadioButtonId());
        out.putInt("duration", binding.duration.getCheckedRadioButtonId());
        out.putInt("theme", binding.theme.getCheckedRadioButtonId());
        out.putString("minutes", binding.customMinutes.getText().toString());
        super.onSaveInstanceState(out);
    }
}
