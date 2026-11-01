package com.example.justgo.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import com.example.justgo.R;
import com.example.justgo.data.DriveSelection;
import com.example.justgo.databinding.ActivityDriveBinding;

public final class DriveActivity extends Activity {
    private static final long PREVIEW_DURATION_MS = 1_800L;
    private static final String SEARCH_PROMPT = "이 조건으로 코스 찾기 →";
    private ActivityDriveBinding binding;
    private String lastSummary;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable completePreview = () -> {
        if (binding == null) return;
        resetSearchButton();
        renderSummary();
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        binding = ActivityDriveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.findRoute.setOnClickListener(v -> showSelection());
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
            lastSummary = saved.getString("summary");
            if (lastSummary != null) renderSummary();
        }
        syncCustomInput();
        updateTimeHint();
        syncScenery();
        syncConditions();
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
        binding.customMinutes.setEnabled(custom);
    }
    private void updateTimeHint() {
        binding.timeHint.setText((binding.roundTrip.isChecked() ? "출발지 복귀" : "목적지 도착") + "까지 · 체류 시간 제외");
    }
    private void showSelection() {
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
        String theme = selectedTheme().getLabel();
        lastSummary = (binding.roundTrip.isChecked() ? "왕복" : "편도") + " · " + minutes + "분 · " + theme;
        binding.customMinutes.clearFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);
        uiHandler.removeCallbacks(completePreview);
        binding.selectionResult.setVisibility(View.GONE);
        binding.findRoute.setActivated(true);
        binding.findRoute.setEnabled(false);
        String prefix = "좋은 길을 찾고 있어요…   ";
        SpannableString label = new SpannableString(prefix + lastSummary);
        // 로딩 중에도 선택 조건의 굵기를 유지하고 크기와 색상만 낮춘다.
        label.setSpan(new StyleSpan(Typeface.BOLD), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new RelativeSizeSpan(.65f), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(0xFFD8E8FF), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.findRoute.setText(label);
        binding.findRoute.setContentDescription(prefix + lastSummary);
        // UI processing preview only. No route API is invoked or fabricated.
        uiHandler.postDelayed(completePreview, PREVIEW_DURATION_MS);
    }
    private void resetSearchButton() {
        binding.findRoute.setActivated(false);
        binding.findRoute.setEnabled(true);
        binding.findRoute.setText(SEARCH_PROMPT);
        binding.findRoute.setContentDescription(null);
    }
    @Override protected void onStop() {
        uiHandler.removeCallbacks(completePreview);
        resetSearchButton();
        super.onStop();
    }
    @Override protected void onDestroy() {
        uiHandler.removeCallbacks(completePreview);
        binding = null;
        super.onDestroy();
    }
    private void renderSummary() {
        binding.selectionSummary.setText(lastSummary);
        binding.selectionResult.setVisibility(View.VISIBLE);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("trip", binding.tripMode.getCheckedRadioButtonId());
        out.putInt("duration", binding.duration.getCheckedRadioButtonId());
        out.putInt("theme", binding.theme.getCheckedRadioButtonId());
        out.putString("minutes", binding.customMinutes.getText().toString());
        out.putString("summary", lastSummary);
        super.onSaveInstanceState(out);
    }
}
