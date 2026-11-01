package com.example.justgo.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.inputmethod.InputMethodManager;
import com.example.justgo.R;
import com.example.justgo.data.DriveSelection;
import com.example.justgo.databinding.ActivityDriveBinding;

public final class DriveActivity extends Activity {
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
        binding.theme.setOnCheckedChangeListener((group, id) -> syncScenery());
        binding.duration.setOnCheckedChangeListener((g, id) -> syncCustomInput());
        binding.tripMode.setOnCheckedChangeListener((g, id) -> updateTimeHint());
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
    }
    private void syncScenery() {
        RiversideSceneView.Theme theme = binding.city.isChecked() ? RiversideSceneView.Theme.CITY
            : binding.mountain.isChecked() ? RiversideSceneView.Theme.MOUNTAIN
            : binding.sea.isChecked() ? RiversideSceneView.Theme.SEA
            : binding.water.isChecked() ? RiversideSceneView.Theme.WATER
            : binding.park.isChecked() ? RiversideSceneView.Theme.PARK : RiversideSceneView.Theme.ANYWHERE;
        binding.scenery.setTheme(theme);
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
        String theme = binding.anywhere.isChecked() ? "어디든 좋아" : binding.city.isChecked() ? "도시"
            : binding.mountain.isChecked() ? "산" : binding.sea.isChecked() ? "바다"
            : binding.water.isChecked() ? "강/호수" : "공원";
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
        label.setSpan(new RelativeSizeSpan(.65f), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(0xFFD8E8FF), prefix.length(), label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.findRoute.setText(label);
        binding.findRoute.setContentDescription(prefix + lastSummary);
        // UI processing preview only. No route API is invoked or fabricated.
        uiHandler.postDelayed(completePreview, 1800);
    }
    private void resetSearchButton() {
        binding.findRoute.setActivated(false);
        binding.findRoute.setEnabled(true);
        binding.findRoute.setText("이 조건으로 코스 찾기  →");
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
