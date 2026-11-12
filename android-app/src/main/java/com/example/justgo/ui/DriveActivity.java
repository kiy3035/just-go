package com.example.justgo.ui;

import android.app.Activity;
import android.app.Dialog;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.example.justgo.data.CurrentLocationProvider;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.net.Uri;
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
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import com.example.justgo.BuildConfig;
import com.example.justgo.R;
import com.example.justgo.data.DriveSelection;
import com.example.justgo.data.LocationFreshness;
import com.example.justgo.data.RouteRecommendationClient;
import com.example.justgo.data.RouteRecommendationClient.Recommendation;
import com.example.justgo.databinding.ActivityDriveBinding;
import com.example.justgo.databinding.DialogRouteResultBinding;
import com.example.justgo.databinding.ItemRouteResultBinding;
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
    private boolean foreground;
    private boolean resultOffersNavigation;
    private Dialog navigationRequestDialog;
    private Dialog resultDialog;
    private DialogRouteResultBinding resultBinding;
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
        if (!LocationFreshness.isUsable(
                SystemClock.elapsedRealtimeNanos(),
                prefetchedLocation.getElapsedRealtimeNanos(),
                prefetchedLocation.getLatitude(),
                prefetchedLocation.getLongitude())) {
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
                runOnUiThread(() -> {
                    if (resultDialog != null && resultDialog == navigationRequestDialog) {
                        showResultDetails("차량 내비가 경로를 찾고 있어요.");
                    }
                });
            }

            @Override public void onRoutingCompleted() {
                runOnUiThread(() -> {
                    Dialog requestedFrom = navigationRequestDialog;
                    navigationRequestDialog = null;
                    if (requestedFrom != null && resultDialog == requestedFrom) dismissResult();
                    else refreshNavigationControl();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    Dialog requestedFrom = navigationRequestDialog;
                    navigationRequestDialog = null;
                    refreshNavigationControl();
                    if (requestedFrom != null && resultDialog == requestedFrom) {
                        showResultDetails(message);
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
        dismissResult();
        searchInProgress = true;
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
        requestRecommendations(new SearchContext(latitude, longitude, minutes, tripType, theme, conditions));
    }

    private void requestRecommendations(SearchContext context) {
        dismissResult();
        searchInProgress = true;
        showSearchProgress("코스를 계산하고 있어요…   ", context.conditions());
        routeExecutor.execute(() -> {
            try {
                Recommendation result = routeClient.recommend(context.latitude(), context.longitude(),
                        context.minutes(), context.tripType(), context.theme());
                runOnUiThread(() -> {
                    if (binding != null) showRecommendation(context, result);
                });
            } catch (IOException error) {
                showRequestError(context, describeError(error));
            }
        });
    }

    private void showSearchProgress(String prefix, String conditions) {
        binding.findRoute.setActivated(true);
        binding.findRoute.setEnabled(false);
        SpannableString label = new SpannableString(prefix + conditions);
        // 로딩 중에도 선택 조건의 굵기를 유지하고 크기와 색상만 낮춘다.
        label.setSpan(new StyleSpan(Typeface.BOLD), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new RelativeSizeSpan(.65f), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(0xFFD8E8FF), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.findRoute.setText(label);
        binding.findRoute.setContentDescription(prefix + conditions);
    }

    /** 조회 실패 팝업. 기존 조건으로 바로 재시도할 수 있다. */
    private void showRequestError(SearchContext context, String message) {
        runOnUiThread(() -> {
            if (!prepareResult("추천 요청 실패", context.conditions())) return;
            showResultDetails(message);
            resultBinding.cancel.setVisibility(View.VISIBLE);
            resultBinding.cancel.setText("닫기");
            resultBinding.confirm.setText("다시 시도");
            resultBinding.confirm.setOnClickListener(v -> requestRecommendations(context));
            showResultDialog();
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
        foreground = true;
        if (hasLocationPermission()) prefetchCurrentLocation();
        restrictionsMonitor.start(restricted -> runOnUiThread(
                () -> applyDrivingRestrictions(restricted)));
    }

    @Override protected void onStop() {
        foreground = false;
        dismissResult();
        restrictionsMonitor.stop();
        routeClient.cancelActiveRequest();
        if (locationProvider.cancel()) resetSearchButton();
        super.onStop();
    }

    @Override protected void onDestroy() {
        dismissResult();
        locationProvider.cancel();
        routeClient.cancelActiveRequest();
        routeExecutor.shutdownNow();
        restrictionsMonitor.stop();
        navigationGateway.release();
        binding = null;
        super.onDestroy();
    }

    /** 추천 코스 목록 팝업. 서버가 계산하고 정렬한 경로를 카드로 보여준다. */
    private void showRecommendation(SearchContext context, Recommendation result) {
        boolean alternative = result.routes().isEmpty() && !result.alternativeRoutes().isEmpty();
        var routes = alternative ? result.alternativeRoutes() : result.routes();
        if (routes.isEmpty()) {
            if (result.mode() == RouteRecommendationClient.Mode.UNAVAILABLE) {
                showRequestError(context, "추천 서비스에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
            } else {
                showEmptyResult(context);
            }
            return;
        }
        boolean live = result.mode() == RouteRecommendationClient.Mode.LIVE;
        String heading = live ? (alternative ? "다른 풍경의 추천 코스" : "추천 코스") : "MOCK · 개발용 추천 예시";
        String summary = context.conditions() + " · " + routes.size() + "개";
        if (live) {
            boolean hasEvents = routes.stream().anyMatch(route -> route.event() != null);
            summary += hasEvents ? "\n관람 가능한 행사 우선 · TMAP 예상 주행시간"
                    : "\nTMAP 예상 주행시간 · 목표 시간과 가까운 순";
        }
        if (!prepareResult(heading, summary)) return;
        for (RouteRecommendationClient.Route route : routes) {
            var card = addRouteCard(context, route);
            // 목록에서 선택한 코스를 상세 화면에 그대로 넘겨 두 번째 선택을 요구하지 않는다.
            if (live && !route.stops().isEmpty()) {
                card.getRoot().setFocusable(true);
                card.getRoot().setOnClickListener(v -> showCourseDetail(context, result, route));
            }
        }
        showResultDialog();
    }

    private ItemRouteResultBinding addRouteCard(SearchContext context, RouteRecommendationClient.Route route) {
        var card = ItemRouteResultBinding.inflate(getLayoutInflater(), resultBinding.routeChoices, false);
        card.themeGraphic.setImageResource(themeGraphic(route.theme()));
        card.courseName.setText(route.event() == null ? route.title() : route.event().name());
        String category = route.event() != null ? "행사" : "CITY".equals(route.theme())
                ? "일반 도시 명소" : themeLabel(route.theme());
        card.courseCategory.setText(category);
        // 서버 정렬에 쓰는 초 단위 차이를 사용해, 분 단위 표시가 같아도 차이를 숨기지 않는다.
        long delta = route.durationSeconds() - context.minutes() * 60L;
        long differenceSeconds = Math.abs(delta);
        String difference = delta == 0 ? "목표 시간과 같아요" : "목표보다 "
                + (differenceSeconds < 60 ? "1분 미만" : Math.round(differenceSeconds / 60.0) + "분")
                + (delta > 0 ? " 길어요" : " 짧아요");
        card.courseMetrics.setText(String.format(Locale.KOREA, "%d분 · %.1f km · %s",
                route.minutes(), route.meters() / 1000.0, difference));
        if (route.event() != null) {
            card.courseReason.setText("예상 도착 " + formatEventTime(route.event().arrivesAt()));
            card.courseReason.setVisibility(View.VISIBLE);
        } else if (route.reason() != null && !route.reason().isBlank()) {
            card.courseReason.setText(route.reason());
            card.courseReason.setVisibility(View.VISIBLE);
        }
        resultBinding.routeChoices.addView(card.getRoot());
        resultBinding.routeChoices.setVisibility(View.VISIBLE);
        return card;
    }

    /** 코스 상세 팝업. 선택한 코스의 구간과 도시 행사 상세를 표시하고 내비 시작을 연결한다. */
    private void showCourseDetail(SearchContext context, Recommendation result, RouteRecommendationClient.Route route) {
        String title = route.event() == null ? "코스 상세" : "행사 상세";
        if (!prepareResult(title, context.conditions())) return;
        addRouteCard(context, route);
        // 구간별 측정값이 없는 이전 서버 응답은 지점 순서만 표시한다.
        StringBuilder itinerary = new StringBuilder("출발 · 현재 위치");
        for (int index = 0; index < route.stops().size(); index++) {
            var stop = route.stops().get(index);
            itinerary.append("\n").append(stop.type() == RouteRecommendationClient.StopType.WAYPOINT
                    ? "경유 · " : "도착 · ").append(stop.name());
            if (index < route.legSeconds().size()) {
                long seconds = route.legSeconds().get(index);
                itinerary.append("  ·  약 ").append((seconds + 59) / 60).append("분");
            }
        }
        if (route.event() != null) {
            var event = route.event();
            itinerary.append("\n\n장소 · ").append(event.venue())
                    .append("\n운영 · ").append(formatEventTime(event.opensAt()))
                    .append(" ~ ").append(formatEventTime(event.closesAt()))
                    .append("\n예상 도착 · ").append(formatEventTime(event.arrivesAt()));
        }
        resultBinding.courseDetail.setText(itinerary);
        resultBinding.courseDetail.setVisibility(View.VISIBLE);
        if (route.event() != null) {
            // 행사 데이터를 보여주는 상세 화면에만 원본 출처 링크를 표시한다.
            var source = resultBinding.eventDataSource;
            source.setVisibility(View.VISIBLE);
            source.setPaintFlags(source.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            source.setOnClickListener(v -> openEventDataSource());
        }
        selectedRoute = route;
        resultOffersNavigation = true;
        resultBinding.cancel.setVisibility(View.VISIBLE);
        resultBinding.cancel.setOnClickListener(v -> showRecommendation(context, result));
        resultBinding.confirm.setText("시작");
        resultBinding.confirm.setOnClickListener(v -> startNavigation());
        refreshNavigationControl();
        showResultDialog();
    }

    private void openEventDataSource() {
        if (!foreground || drivingRestricted) return;
        Intent link = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.event_data_source_url)));
        try {
            startActivity(link);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.event_data_source_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    /** 정상 조회 후 코스가 없는 팝업. 기존 메인 화면에서 시간 또는 테마를 조정하도록 안내한다. */
    private void showEmptyResult(SearchContext context) {
        if (!prepareResult("조건에 맞는 코스가 부족해요", context.conditions())) return;
        showResultDetails("주행시간을 조정하거나 다른 테마를 선택해 보세요.");
        resultBinding.cancel.setVisibility(View.VISIBLE);
        resultBinding.cancel.setText("시간 변경");
        resultBinding.cancel.setOnClickListener(v -> focusSearchChoice(binding.duration));
        resultBinding.confirm.setText("다른 테마 보기");
        resultBinding.confirm.setOnClickListener(v -> focusSearchChoice(binding.theme));
        showResultDialog();
    }

    private void focusSearchChoice(ViewGroup group) {
        dismissResult();
        if (group.getChildCount() > 0) group.getChildAt(0).requestFocus();
    }

    private static String formatEventTime(java.time.ZonedDateTime time) {
        return time.format(java.time.format.DateTimeFormatter.ofPattern("MM.dd HH:mm", Locale.KOREA));
    }

    private static int themeGraphic(String theme) {
        return switch (theme) {
            case "CITY" -> R.drawable.ic_city;
            case "MOUNTAIN" -> R.drawable.ic_mountain;
            case "SEA" -> R.drawable.ic_sea;
            case "WATER", "RIVER", "LAKE" -> R.drawable.ic_water;
            case "PARK" -> R.drawable.ic_park;
            default -> R.drawable.ic_anywhere;
        };
    }

    private void startNavigation() {
        if (drivingRestricted || navigationRequestDialog != null || selectedRoute == null
                || !navigationGateway.isConnected()) return;
        List<NavigationGateway.Stop> stops = new ArrayList<>();
        for (RouteRecommendationClient.Stop stop : selectedRoute.stops()) {
            stops.add(new NavigationGateway.Stop(
                    stop.sequence(),
                    stop.type() == RouteRecommendationClient.StopType.WAYPOINT
                            ? NavigationGateway.StopType.WAYPOINT
                            : NavigationGateway.StopType.DESTINATION,
                    stop.name(), stop.latitude(), stop.longitude()));
        }
        navigationRequestDialog = resultDialog;
        resultBinding.cancel.setText("닫기");
        refreshNavigationControl();
        showResultDetails("선택한 코스를 차량 내비로 전달하고 있어요…");
        navigationGateway.startRoute(stops);
    }

    private void refreshNavigationControl() {
        if (resultBinding == null || !resultOffersNavigation) return;
        boolean connected = navigationGateway.isConnected();
        resultBinding.confirm.setEnabled(!drivingRestricted && navigationRequestDialog == null
                && connected && selectedRoute != null);
        if (navigationRequestDialog != null) return;
        resultBinding.cancel.setText("취소");
        if (navigationConnectionFailed) {
            showResultDetails("차량 내비에 연결하지 못했습니다.");
        } else if (!connected) {
            showResultDetails("차량 내비 연결을 확인하고 있어요.");
        } else {
            showResultDetails(selectedRoute == null
                    ? "내비로 보낼 코스를 선택해 주세요."
                    : "시작을 누르면 차량 내비로 보냅니다.");
        }
    }

    private void showResultDetails(String message) {
        if (resultBinding == null) return;
        resultBinding.navigationStatus.setText(message);
        resultBinding.navigationStatus.setVisibility(View.VISIBLE);
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
        if (restricted) {
            dismissResult();
            routeClient.cancelActiveRequest();
            if (locationProvider.cancel()) resetSearchButton();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);
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
            if ("INVALID_CANDIDATE_TOKEN".equals(http.code)) {
                return "선택한 장소가 만료됐습니다. 장소를 다시 검색해 주세요.";
            }
            if ("PLACE_SOURCE_UNAVAILABLE".equals(http.code)) {
                return "장소 데이터를 불러오지 못했어요. 잠시 후 다시 검색해 주세요.";
            }
            if ("ROUTE_PROVIDER_QUOTA_EXCEEDED".equals(http.code)) {
                return "경로 조회 한도에 도달했어요.";
            }
            if ("ROUTE_API_BUSY".equals(http.code)) {
                return "다른 추천을 계산하고 있습니다. 잠시 후 다시 시도해 주세요.";
            }
            if ("ROUTE_API_RATE_LIMITED".equals(http.code)) {
                return "추천 요청이 많습니다. 잠시 후 다시 검색해 주세요.";
            }
            if (http.code != null && http.code.startsWith("INVALID_")
                    && http.getMessage() != null && !http.getMessage().isBlank()) {
                return http.getMessage();
            }
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
        if (prepareResult(title, details)) showResultDialog();
    }

    private boolean prepareResult(String title, String details) {
        if (binding == null) return false;
        resetSearchButton();
        if (!foreground || drivingRestricted) return false;
        dismissResult();

        resultBinding = DialogRouteResultBinding.inflate(getLayoutInflater());
        resultBinding.resultTitle.setText(title);
        resultBinding.selectionSummary.setText(details);
        resultBinding.cancel.setVisibility(View.GONE);
        resultBinding.cancel.setOnClickListener(v -> dismissResult());
        resultBinding.confirm.setOnClickListener(v -> dismissResult());

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(resultBinding.getRoot());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            if (resultDialog == dialog) {
                resultDialog = null;
                resultBinding = null;
                selectedRoute = null;
                resultOffersNavigation = false;
            }
        });
        resultDialog = dialog;
        return true;
    }

    private void showResultDialog() {
        float density = getResources().getDisplayMetrics().density;
        int availableWidth = binding.getRoot().getWidth();
        int preferredWidth = Math.round(availableWidth * 0.62f);
        int readableWidth = Math.max(preferredWidth, Math.round(420 * density));
        int panelWidth = Math.min(readableWidth,
                Math.min(Math.round(1120 * density), Math.round(availableWidth * 0.86f)));
        ViewGroup.LayoutParams panelLayout = resultBinding.resultPanel.getLayoutParams();
        panelLayout.width = panelWidth;
        resultBinding.resultPanel.setLayoutParams(panelLayout);

        resultDialog.show();
        Window window = resultDialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void dismissResult() {
        if (resultDialog == null) return;
        Dialog dialog = resultDialog;
        resultDialog = null;
        resultBinding = null;
        selectedRoute = null;
        resultOffersNavigation = false;
        dialog.dismiss();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("trip", binding.tripMode.getCheckedRadioButtonId());
        out.putInt("duration", binding.duration.getCheckedRadioButtonId());
        out.putInt("theme", binding.theme.getCheckedRadioButtonId());
        out.putString("minutes", binding.customMinutes.getText().toString());
        super.onSaveInstanceState(out);
    }

    private record SearchContext(
            double latitude,
            double longitude,
            int minutes,
            String tripType,
            String theme,
            String conditions) { }
}
