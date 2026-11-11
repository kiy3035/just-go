package com.example.justgo.ui;

import android.app.Activity;
import android.app.Dialog;
import android.Manifest;
import android.content.pm.PackageManager;
import com.example.justgo.data.CurrentLocationProvider;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import com.example.justgo.BuildConfig;
import com.example.justgo.R;
import com.example.justgo.data.DriveSelection;
import com.example.justgo.data.LocationFreshness;
import com.example.justgo.data.RouteRecommendationClient;
import com.example.justgo.data.RouteRecommendationClient.Recommendation;
import com.example.justgo.databinding.ActivityDriveBinding;
import com.example.justgo.databinding.DialogRouteResultBinding;
import com.example.justgo.databinding.RouteCourseRowBinding;
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
            requestCourses(location.getLatitude(), location.getLongitude(),
                    minutes, roundTrip, theme);
            return;
        }
        locationProvider.request(new CurrentLocationProvider.Callback() {
            @Override public void onLocation(Location location) {
                if (binding == null) return;
                prefetchedLocation = location;
                requestCourses(location.getLatitude(), location.getLongitude(),
                        minutes, roundTrip, theme);
            }

            @Override public void onError(String message) {
                renderResult("현재 위치 확인 필요", message);
            }
        });
    }

    private void requestCourses(double latitude, double longitude, int minutes,
                                boolean roundTrip, String theme) {
        String tripType = roundTrip ? "ROUND_TRIP" : "ONE_WAY";
        String tripSummary = (roundTrip ? "왕복" : "편도") + " " + minutes + "분";
        String conditions = tripSummary + " · " + themeLabel(theme);
        showSearchProgress("조건에 맞는 코스를 찾고 있어요…   ", conditions);
        routeExecutor.execute(() -> {
            try {
                Recommendation result = routeClient.recommend(latitude, longitude, minutes, tripType, theme);
                runOnUiThread(() -> {
                    if (binding != null) showCourseChoices(conditions, tripSummary, result);
                });
            } catch (IOException error) {
                showRequestError("추천 요청 실패", conditions, error);
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

    private void showRequestError(String title, String conditions, IOException error) {
        String message = describeError(error);
        runOnUiThread(() -> {
            if (binding != null && prepareResult(title, conditions)) {
                showResultDetails(message);
                showResultDialog();
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

    private void showCourseChoices(String conditions, String tripSummary, Recommendation result) {
        boolean alternative = result.routes().isEmpty() && !result.alternativeRoutes().isEmpty();
        var routes = alternative ? result.alternativeRoutes() : result.routes();
        boolean live = result.mode() == RouteRecommendationClient.Mode.LIVE;
        if (live) {
            routes = new ArrayList<>(routes);
            routes.removeIf(route -> route.stops().isEmpty());
        }
        if (routes.isEmpty()) {
            if (result.mode() == RouteRecommendationClient.Mode.UNAVAILABLE) {
                renderResult("지금은 코스를 찾기 어려워요", "잠시 후 다시 시도해 주세요.");
            } else {
                renderResult("조건에 맞는 코스가 없어요",
                        "희망 시간 ±10분 안에 코스를 찾지 못했어요. 시간이나 풍경을 바꿔보세요.");
            }
            return;
        }
        if (!live) {
            showCourseDetails("MOCK · 개발용 추천 예시", tripSummary, result, routes.get(0), false);
            return;
        }

        String heading = alternative ? "다른 풍경의 추천 코스" : "추천 코스";
        if (!prepareResult(heading, conditions)) return;
        resultBinding.closeCourseList.setVisibility(View.VISIBLE);
        resultBinding.closeCourseList.setOnClickListener(v -> dismissResult());
        for (RouteRecommendationClient.Route route : routes) {
            addCourseRow(route);
        }
        resultBinding.routeChoices.setVisibility(View.VISIBLE);
        resultBinding.cancel.setVisibility(View.VISIBLE);
        resultBinding.confirm.setText("코스 확인");
        resultBinding.confirm.setBackgroundResource(R.drawable.route_detail_primary);
        resultBinding.confirm.setEnabled(false);
        resultBinding.confirm.setOnClickListener(v -> {
            View checked = (View) resultBinding.routeChoices.getTag();
            if (checked != null) {
                showCourseDetails("추천 코스", tripSummary, result,
                        (RouteRecommendationClient.Route) checked.getTag(), true);
            }
        });
        showResultDetails("코스를 고르면 상세 정보를 확인할 수 있어요.");
        showResultDialog();
    }

    private String courseMetrics(RouteRecommendationClient.Route route) {
        return String.format(Locale.KOREA, "%d분  ·  %.1f km",
                route.minutes(), route.meters() / 1000.0);
    }

    private void showCourseDetails(String heading, String tripSummary, Recommendation result,
                                   RouteRecommendationClient.Route route, boolean live) {
        if (!prepareResult(heading, tripSummary)) return;
        resultBinding.selectionSummary.setVisibility(View.GONE);
        resultBinding.resultTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_route_popup, 0, 0, 0);
        resultBinding.resultTitle.setCompoundDrawablePadding(
                Math.round(16 * getResources().getDisplayMetrics().density));

        var detail = resultBinding.courseDetail;
        detail.getRoot().setVisibility(View.VISIBLE);
        detail.tripChip.setText(tripSummary);
        detail.themeChip.setText(themeLabel(route.theme()));
        var sceneryTheme = switch (route.theme()) {
            case "CITY" -> RiversideSceneView.Theme.CITY;
            case "MOUNTAIN" -> RiversideSceneView.Theme.MOUNTAIN;
            case "SEA" -> RiversideSceneView.Theme.SEA;
            case "RIVER", "LAKE", "WATER" -> RiversideSceneView.Theme.WATER;
            case "PARK" -> RiversideSceneView.Theme.PARK;
            default -> RiversideSceneView.Theme.ANYWHERE;
        };
        int themeIcon = switch (sceneryTheme) {
            case CITY -> R.drawable.ic_city;
            case MOUNTAIN -> R.drawable.ic_mountain;
            case SEA -> R.drawable.ic_sea;
            case WATER -> R.drawable.ic_water;
            case PARK -> R.drawable.ic_park;
            case ANYWHERE -> R.drawable.ic_anywhere;
        };
        detail.themeChip.setCompoundDrawablesRelativeWithIntrinsicBounds(themeIcon, 0, 0, 0);
        detail.headerScenery.setPreviewMode();
        detail.headerScenery.setTheme(sceneryTheme);
        detail.thumbnail.setPreviewMode();
        detail.thumbnail.setTheme(sceneryTheme);

        detail.destination.setVisibility(View.GONE);
        for (RouteRecommendationClient.Stop stop : route.stops()) {
            if (stop.type() == RouteRecommendationClient.StopType.DESTINATION && !stop.name().isBlank()) {
                detail.destination.setText("목적지  " + stop.name());
                detail.destination.setVisibility(View.VISIBLE);
                break;
            }
        }
        detail.courseNotice.setText(result.notice());
        detail.courseNotice.setVisibility(
                result.notice() == null || result.notice().isBlank() ? View.GONE : View.VISIBLE);
        detail.courseTitle.setText(route.title());
        String repeated = route.repeatedSegmentPercent() == null
                ? ""
                : "  ·  왕복 중복 " + route.repeatedSegmentPercent() + "%";
        detail.courseMetrics.setText(courseMetrics(route) + repeated);
        detail.courseReason.setText(route.reason());
        detail.courseReason.setVisibility(
                route.reason() == null || route.reason().isBlank() ? View.GONE : View.VISIBLE);

        resultOffersNavigation = live;
        if (live) {
            selectedRoute = route;
            resultBinding.cancel.setVisibility(View.VISIBLE);
            resultBinding.confirm.setText("시작");
            resultBinding.confirm.setBackgroundResource(R.drawable.route_detail_primary);
            resultBinding.confirm.setOnClickListener(v -> startNavigation());
            refreshNavigationControl();
        }
        showResultDialog();
    }

    private void addCourseRow(RouteRecommendationClient.Route route) {
        var list = resultBinding.routeChoices;
        var row = RouteCourseRowBinding.inflate(getLayoutInflater(), list, false);
        String theme = themeLabel(route.theme());
        String metrics = courseMetrics(route);
        row.courseName.setText(route.title());
        row.courseTheme.setText(theme);
        row.courseStats.setText(metrics);
        View choice = row.getRoot();
        choice.setContentDescription(route.title() + " · " + theme + " · " + metrics);
        choice.setTag(route);
        choice.setOnClickListener(v -> {
            View previous = (View) list.getTag();
            if (previous != null) previous.setSelected(false);
            choice.setSelected(true);
            list.setTag(choice);
            resultBinding.confirm.setEnabled(!drivingRestricted);
        });
        list.addView(choice);
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
            showResultDetails("시작을 누르면 차량 내비로 보냅니다.");
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
        boolean courseList = resultBinding.routeChoices.getVisibility() == View.VISIBLE;
        int preferredWidth = Math.round(availableWidth * (courseList ? 0.86f : 0.62f));
        int readableWidth = Math.max(preferredWidth, Math.round(420 * density));
        int panelWidth = Math.min(readableWidth,
                Math.min(Math.round((courseList ? 1420 : 1120) * density),
                        Math.round(availableWidth * 0.86f)));
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

}
