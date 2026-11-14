package com.example.justgo.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import com.example.justgo.R;
import com.example.justgo.data.RouteRecommendationClient;
import com.example.justgo.data.RouteRecommendationClient.BannerEvent;
import com.example.justgo.data.RouteRecommendationClient.EventHttpFailure;
import com.example.justgo.data.RouteRecommendationClient.InvalidResponse;
import com.example.justgo.data.RouteRecommendationClient.TodayEvents;
import com.example.justgo.databinding.EventBannerBinding;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;

/** 오늘 행사 추천 카드의 조회, 자동 전환, 수동 탐색과 내비 전달을 관리한다. */
final class TodayEventBanner {
    private static final long ROTATE_DELAY = 8_000;
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService eventWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService imageWorker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> photos = new LruCache<>(5);
    private final EventBannerBinding binding;
    private final RouteRecommendationClient client;
    private final Consumer<BannerEvent> navigate;
    private final Runnable rotate = this::next;
    private List<BannerEvent> events = List.of();
    private int index;
    private boolean loading;
    private boolean active;
    private boolean restricted = true;
    private boolean focused;
    private boolean navigationPending;
    private boolean closed;

    TodayEventBanner(EventBannerBinding binding, RouteRecommendationClient client,
                     Consumer<BannerEvent> navigate) {
        this.binding = binding;
        this.client = client;
        this.navigate = navigate;
        binding.eventNavigate.setEnabled(false);
        binding.eventNavigate.setOnFocusChangeListener((view, hasFocus) -> scheduleRotation());
        binding.eventNext.setEnabled(false);
        binding.eventNext.setOnFocusChangeListener((view, hasFocus) -> scheduleRotation());
        // 포스터와 무관한 명시적 버튼으로 사용자가 다음 행사를 확인한다.
        binding.eventNext.setOnClickListener(view -> {
            if (canAdvance()) next();
        });
        binding.eventArtworkPanel.setClickable(false);
        binding.eventArtworkPanel.setFocusable(false);
        // 이동하기는 팝업 없이 현재 보이는 행사를 곧바로 차량 내비로 전달한다.
        binding.eventNavigate.setOnClickListener(view -> navigateToCurrentEvent());
        updateControls();
    }

    void start() {
        active = true;
        if (loading) return;
        loading = true;
        eventWorker.execute(() -> {
            try {
                TodayEvents result = client.todayEvents();
                handler.post(() -> show(result));
            } catch (IOException exception) {
                handler.post(() -> showFailure(exception));
            }
        });
    }

    void stop() {
        active = false;
        restricted = true;
        updateControls();
    }

    void close() {
        stop();
        closed = true;
        eventWorker.shutdownNow();
        imageWorker.shutdownNow();
    }

    void setRestricted(boolean restricted) {
        this.restricted = restricted;
        updateControls();
    }

    void setFocused(boolean focused) {
        this.focused = focused;
        scheduleRotation();
    }

    void setNavigationPending(boolean pending) {
        navigationPending = pending;
        updateControls();
    }

    private void show(TodayEvents result) {
        loading = false;
        if (closed) return;
        events = result.events();
        index = 0;
        if (events.isEmpty()) {
            binding.eventHeading.setText(R.string.event_banner_label);
            binding.eventRegionBadge.setVisibility(View.GONE);
            binding.eventTitle.setText(result.partial()
                    ? "행사를 불러오지 못했어요" : "오늘은 행사가 없어요");
            binding.eventDetail.setVisibility(View.GONE);
            binding.eventSource.setVisibility(View.GONE);
            binding.eventRecommendationReason.setVisibility(View.GONE);
            binding.eventRecommendationRow.setVisibility(View.GONE);
            binding.eventPageStatus.setVisibility(View.GONE);
            binding.eventNext.setVisibility(View.GONE);
            binding.eventNavigate.setVisibility(View.GONE);
            binding.eventArt.setImageResource(R.drawable.event_art_empty);
            updateControls();
            return;
        }
        render();
    }

    private void showFailure(IOException failure) {
        show(new TodayEvents(List.of(), true));
        if (closed) return;
        // 서버 연결과 행사 데이터 오류를 구분해 어느 곳을 확인할지 안내한다.
        String detail = "백엔드 실행과 연결 주소를 확인해 주세요.";
        if (failure instanceof EventHttpFailure http) {
            detail = switch (http.status()) {
                case 404 -> "백엔드를 최신 버전으로 다시 실행해 주세요.";
                case 503 -> "백엔드의 행사 API 키와 조회 상태를 확인해 주세요.";
                default -> "백엔드의 행사 API 응답을 확인해 주세요.";
            };
        } else if (failure instanceof InvalidResponse) {
            detail = "백엔드의 행사 API 응답을 확인해 주세요.";
        }
        binding.eventDetail.setText(detail);
        binding.eventDetail.setVisibility(View.VISIBLE);
    }

    /** 자동 또는 수동 전환 후 다음 행사를 같은 표시 시간으로 다시 시작한다. */
    private void next() {
        if (!canAdvance()) return;
        index = (index + 1) % events.size();
        render();
    }

    /** 현재 행사의 분류, 장소, 일정과 추천 이유를 카드에 표시한다. */
    private void render() {
        BannerEvent event = events.get(index);
        binding.eventHeading.setText(R.string.event_banner_label);
        binding.eventRegionBadge.setText(binding.getRoot().getContext().getString(
                R.string.event_banner_region_today, event.region()));
        binding.eventRegionBadge.setVisibility(View.VISIBLE);
        binding.eventTitle.setText(event.title());

        String detail = event.venue();
        if (!event.schedule().isBlank()) {
            detail = detail.isBlank() ? event.schedule() : detail + " · " + event.schedule();
        }
        binding.eventDetail.setText(detail);
        binding.eventDetail.setVisibility(detail.isBlank() ? View.GONE : View.VISIBLE);
        String category = binding.getRoot().getContext().getString(categoryLabel(event.category()));
        binding.eventSource.setText(binding.getRoot().getContext().getString(
                R.string.event_banner_reason, category));
        binding.eventSource.setVisibility(View.VISIBLE);
        binding.eventRecommendationReason.setText(R.string.event_banner_recommendation);
        binding.eventRecommendationReason.setVisibility(View.VISIBLE);
        binding.eventRecommendationRow.setVisibility(View.VISIBLE);
        binding.eventPageStatus.setText(binding.getRoot().getContext().getString(
                R.string.event_banner_page, index + 1, events.size()));
        binding.eventPageStatus.setVisibility(View.VISIBLE);
        binding.eventNext.setVisibility(events.size() > 1 ? View.VISIBLE : View.GONE);
        binding.eventNavigate.setVisibility(View.VISIBLE);

        binding.eventArt.setImageResource(switch (event.category()) {
            case "PERFORMANCE" -> R.drawable.event_art_performance;
            case "EXHIBITION" -> R.drawable.event_art_exhibition;
            case "FESTIVAL" -> R.drawable.event_art_festival;
            default -> R.drawable.event_art_empty;
        });
        loadPhoto(event);
        updateControls();
    }

    /** 원격 이미지는 크기와 프로토콜을 제한하고 현재 행사일 때만 반영한다. */
    private void loadPhoto(BannerEvent event) {
        if (event.imageUrl().isBlank()) return;
        imageWorker.execute(() -> {
            Bitmap stored = photos.get(event.imageUrl());
            Bitmap photo = stored != null ? stored : downloadPhoto(event.imageUrl());
            if (stored == null && photo != null) photos.put(event.imageUrl(), photo);
            handler.post(() -> {
                if (photo == null || closed || events.isEmpty() || events.get(index) != event) return;
                binding.eventArt.setImageBitmap(photo);
            });
        });
    }

    private void navigateToCurrentEvent() {
        if (active && !restricted && !navigationPending && !events.isEmpty()) {
            navigate.accept(events.get(index));
        }
    }

    private int categoryLabel(String category) {
        return switch (category) {
            case "PERFORMANCE" -> R.string.event_banner_category_performance;
            case "EXHIBITION" -> R.string.event_banner_category_exhibition;
            case "FESTIVAL" -> R.string.event_banner_category_festival;
            default -> R.string.event_banner_category_default;
        };
    }

    private void updateControls() {
        binding.eventNavigate.setEnabled(active && !restricted
                && !navigationPending && !events.isEmpty());
        boolean advance = canAdvance();
        binding.eventNext.setEnabled(advance);
        binding.eventArtworkPanel.setEnabled(true);
        binding.eventArtworkPanel.setClickable(false);
        binding.eventArtworkPanel.setFocusable(false);
        binding.eventArtworkPanel.setContentDescription(null);
        scheduleRotation();
    }

    private boolean canAdvance() {
        return active && !restricted && !navigationPending && events.size() > 1;
    }

    private void scheduleRotation() {
        handler.removeCallbacks(rotate);
        if (focused && canAdvance()
                && !binding.eventNext.hasFocus() && !binding.eventNavigate.hasFocus()) {
            handler.postDelayed(rotate, ROTATE_DELAY);
        }
    }

    private static Bitmap downloadPhoto(String address) {
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(address);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return null;
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(2_500);
            connection.setReadTimeout(3_000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200
                    || connection.getContentLength() > MAX_IMAGE_BYTES) return null;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1; ) {
                    if (output.size() + count > MAX_IMAGE_BYTES) return null;
                    output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1024) {
                    options.inSampleSize *= 2;
                }
                options.inJustDecodeBounds = false;
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            }
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
