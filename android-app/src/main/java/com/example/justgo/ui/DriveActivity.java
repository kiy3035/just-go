package com.example.justgo.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.example.justgo.BuildConfig;
import com.example.justgo.R;
import com.example.justgo.data.DriveSelection;
import com.example.justgo.data.RouteRecommendationClient;
import com.example.justgo.data.RouteRecommendationClient.Recommendation;
import com.example.justgo.databinding.ActivityDriveBinding;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DriveActivity extends Activity {
    private static final String SEARCH_PROMPT = "이 조건으로 코스 찾기 →";
    private final ExecutorService routeExecutor = Executors.newSingleThreadExecutor();
    private final RouteRecommendationClient routeClient =
            new RouteRecommendationClient(BuildConfig.ROUTE_API_BASE_URL);
    private ActivityDriveBinding binding;
    private AlertDialog originDialog;
    private Double lastLatitude;
    private Double lastLongitude;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
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
            binding.theme.check(saved.getInt("theme", R.id.city));
            binding.customMinutes.setText(saved.getString("minutes", "90"));
        }
        syncCustomInput();
        updateTimeHint();
        syncScenery();
        syncConditions();
    }
    private RiversideSceneView.Theme selectedTheme() {
        if (binding.mountain.isChecked()) return RiversideSceneView.Theme.MOUNTAIN;
        if (binding.sea.isChecked()) return RiversideSceneView.Theme.SEA;
        if (binding.river.isChecked()) return RiversideSceneView.Theme.RIVER;
        if (binding.lake.isChecked()) return RiversideSceneView.Theme.LAKE;
        return RiversideSceneView.Theme.CITY;
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
        binding.customMinutes.setEnabled(custom);
    }
    private void updateTimeHint() {
        binding.timeHint.setText((binding.roundTrip.isChecked() ? "출발지 복귀" : "목적지 도착") + "까지 · 체류 시간 제외");
    }
    private void chooseOrigin() {
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
        RiversideSceneView.Theme theme = selectedTheme();
        boolean roundTrip = binding.roundTrip.isChecked();

        LinearLayout coordinates = new LinearLayout(this);
        coordinates.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        coordinates.setPadding(padding, 0, padding, 0);
        EditText latitude = coordinateField("위도 (−90 ~ 90)", lastLatitude);
        EditText longitude = coordinateField("경도 (−180 ~ 180)", lastLongitude);
        coordinates.addView(latitude);
        coordinates.addView(longitude);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("출발지 좌표 입력")
                .setMessage("정차 중 WGS84 위도와 경도를 입력해 주세요.")
                .setView(coordinates)
                .setNegativeButton("취소", null)
                .setPositiveButton("코스 찾기", null)
                .create();
        originDialog = dialog;
        dialog.setOnDismissListener(ignored -> originDialog = null);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double originLatitude;
            double originLongitude;
            try {
                originLatitude = DriveSelection.parseCoordinate(latitude.getText().toString(), 90);
            } catch (IllegalArgumentException error) {
                latitude.setError("−90 ~ 90 사이의 위도를 입력해 주세요.");
                latitude.requestFocus();
                return;
            }
            try {
                originLongitude = DriveSelection.parseCoordinate(longitude.getText().toString(), 180);
            } catch (IllegalArgumentException error) {
                longitude.setError("−180 ~ 180 사이의 경도를 입력해 주세요.");
                longitude.requestFocus();
                return;
            }
            lastLatitude = originLatitude;
            lastLongitude = originLongitude;
            dialog.dismiss();
            requestRecommendations(originLatitude, originLongitude, minutes, roundTrip, theme);
        });
    }

    private EditText coordinateField(String hint, Double previous) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setSingleLine(true);
        field.setSaveEnabled(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setImeOptions(field.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        if (previous != null) {
            field.setText(previous.toString());
        }
        return field;
    }

    private void requestRecommendations(double latitude, double longitude, int minutes,
                                        boolean roundTrip, RiversideSceneView.Theme theme) {
        String tripType = roundTrip ? "ROUND_TRIP" : "ONE_WAY";
        String conditions = (roundTrip ? "왕복" : "편도") + " · " + minutes + "분 · " + theme.getLabel();
        binding.selectionResult.setVisibility(View.GONE);
        binding.findRoute.setActivated(true);
        binding.findRoute.setEnabled(false);
        String prefix = "추천 코스를 찾고 있어요…   ";
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
                        tripType, theme.name());
                runOnUiThread(() -> showRecommendation(conditions, result));
            } catch (IOException error) {
                String message = describeError(error);
                runOnUiThread(() -> renderResult("추천 요청 실패", conditions + "\n\n" + message));
            }
        });
    }
    private void resetSearchButton() {
        binding.findRoute.setActivated(false);
        binding.findRoute.setEnabled(true);
        binding.findRoute.setText(SEARCH_PROMPT);
        binding.findRoute.setContentDescription(null);
    }
    @Override protected void onStop() {
        if (originDialog != null) originDialog.dismiss();
        lastLatitude = null;
        lastLongitude = null;
        super.onStop();
    }

    @Override protected void onDestroy() {
        routeExecutor.shutdownNow();
        binding = null;
        super.onDestroy();
    }

    private void showRecommendation(String conditions, Recommendation result) {
        String heading = switch (result.mode()) {
            case MOCK -> "MOCK · 개발용 추천 예시";
            case LIVE -> result.routes().isEmpty() ? "추천 코스 없음" : "추천 코스";
            case UNAVAILABLE -> "추천 서비스 사용 불가";
        };
        StringBuilder details = new StringBuilder(conditions);
        for (RouteRecommendationClient.Route route : result.routes()) {
            details.append("\n\n").append(route.title());
            details.append(String.format(Locale.KOREA, "\n%d분 · %.1f km",
                    route.minutes(), route.meters() / 1000.0));
            for (RouteRecommendationClient.Stop stop : route.stops()) {
                details.append("\n").append(stop.sequence()).append(". ")
                        .append(stop.type() == RouteRecommendationClient.StopType.WAYPOINT
                                ? "경유 · " : "도착 · ")
                        .append(stop.name());
            }
            details.append("\n").append(route.reason());
        }
        if (!result.suggestions().isEmpty()) {
            details.append("\n\n다시 검색할 조건");
            for (RouteRecommendationClient.Suggestion suggestion : result.suggestions()) {
                String theme = switch (suggestion.theme()) {
                    case "CITY" -> "도시";
                    case "MOUNTAIN" -> "산";
                    case "RIVER" -> "강";
                    case "LAKE" -> "호수";
                    case "SEA" -> "바다";
                    default -> suggestion.theme();
                };
                details.append("\n• ").append(suggestion.tripType().equals("ROUND_TRIP") ? "왕복" : "편도")
                        .append(" · ").append(suggestion.minutes()).append("분 · ").append(theme)
                        .append("\n").append(suggestion.reason());
            }
        }
        if (!result.notice().trim().isEmpty()) {
            details.append("\n\n").append(result.notice());
        }
        renderResult(heading, details.toString());
    }

    private String describeError(IOException error) {
        if (error instanceof RouteRecommendationClient.HttpError http) {
            return switch (http.status) {
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
        binding.resultTitle.post(() -> {
            if (binding == null) return;
            View header = binding.resultTitle;
            header.requestRectangleOnScreen(new Rect(0, 0, header.getWidth(), header.getHeight()));
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
