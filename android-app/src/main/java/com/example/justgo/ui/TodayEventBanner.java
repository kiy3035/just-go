package com.example.justgo.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
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

/** 메인 화면 행사 카드의 조회, 이미지 표시, 자동 전환을 관리한다. */
final class TodayEventBanner {
    private static final long ROTATE_DELAY = 8_000;
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService eventWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService imageWorker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> photos = new LruCache<>(5);
    private final EventBannerBinding binding;
    private final RouteRecommendationClient client;
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
        binding.eventNavigate.setEnabled(false);
        binding.eventNavigate.setOnFocusChangeListener((view, hasFocus) -> scheduleRotation());
        // 클릭 당시 화면에 보이는 카드를 목적지로 고정한다.
        binding.eventNavigate.setOnClickListener(view -> {
            if (active && !restricted && !navigationPending && !events.isEmpty()) {
                navigate.accept(events.get(index));
            }
        });
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
        scheduleRotation();
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
            binding.eventTitle.setText(result.partial()
                    ? "행사를 불러오지 못했어요" : "오늘은 행사가 없어요");
            binding.eventDetail.setVisibility(View.GONE);
            binding.eventSource.setVisibility(View.GONE);
            binding.eventNavigate.setVisibility(View.GONE);
            binding.eventPages.setVisibility(View.GONE);
            binding.eventArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            binding.eventArt.setImageResource(R.drawable.event_art_empty);
            scheduleRotation();
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

    private void next() {
        index = (index + 1) % events.size();
        render();
    }

    private void render() {
        BannerEvent event = events.get(index);
        // 배너가 넘어갈 때 제목도 현재 행사 한 장의 지역에 맞춰 갱신한다.
        binding.eventHeading.setText(switch (event.source()) {
            case "SEOUL" -> R.string.event_banner_seoul_label;
            case "TOUR_API" -> R.string.event_banner_gyeonggi_label;
            default -> R.string.event_banner_label;
        });
        binding.eventTitle.setText(event.title());
        String detail = event.venue().startsWith(event.region())
                ? event.venue() : event.region() + " · " + event.venue();
        binding.eventDetail.setText(event.schedule().isBlank() ? detail : detail + " · " + event.schedule());
        binding.eventDetail.setVisibility(View.VISIBLE);
        binding.eventSource.setText(event.source().equals("SEOUL")
                ? "출처 · 서울특별시" : "출처 · 한국관광공사");
        binding.eventSource.setVisibility(View.VISIBLE);
        binding.eventNavigate.setVisibility(View.VISIBLE);
        binding.eventPages.setVisibility(events.size() > 1 ? View.VISIBLE : View.GONE);
        StringBuilder dots = new StringBuilder();
        for (int page = 0; page < events.size(); page++) {
            dots.append(page == index ? "● " : "○ ");
        }
        binding.eventPages.setText(dots.toString().trim());
        binding.eventPages.setContentDescription((index + 1) + " / " + events.size());
        binding.eventArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        binding.eventArt.setImageResource(switch (event.category()) {
            case "PERFORMANCE" -> R.drawable.event_art_performance;
            case "EXHIBITION" -> R.drawable.event_art_exhibition;
            case "FESTIVAL" -> R.drawable.event_art_festival;
            default -> R.drawable.event_art_empty;
        });
        if (!event.imageUrl().isBlank()) {
            imageWorker.execute(() -> {
                Bitmap stored = photos.get(event.imageUrl());
                Bitmap photo = stored != null ? stored : loadPhoto(event.imageUrl());
                if (stored == null && photo != null) photos.put(event.imageUrl(), photo);
                handler.post(() -> {
                    if (photo == null || closed || events.isEmpty() || events.get(index) != event) return;
                    // 출처 이미지 전체를 보여줘 포스터 제목과 일정이 잘리지 않게 한다.
                    binding.eventArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    binding.eventArt.setImageBitmap(photo);
                });
            });
        }
        updateControls();
    }

    private void updateControls() {
        binding.eventNavigate.setEnabled(active && !restricted && !navigationPending);
        scheduleRotation();
    }

    private void scheduleRotation() {
        handler.removeCallbacks(rotate);
        if (active && focused && !restricted && !navigationPending && events.size() > 1
                && !binding.eventNavigate.hasFocus()) {
            handler.postDelayed(rotate, ROTATE_DELAY);
        }
    }

    private static Bitmap loadPhoto(String address) {
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(address);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return null;
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(2_500);
            connection.setReadTimeout(3_000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200 || connection.getContentLength() > MAX_IMAGE_BYTES) return null;
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
