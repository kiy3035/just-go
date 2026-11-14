package com.example.justgo.ui;

import android.app.AlertDialog;
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

/** 메인 화면에서 오늘 행사 한 건을 추천 정보 카드로 조회하고 표시한다. */
final class TodayEventBanner {
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService eventWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService imageWorker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> photos = new LruCache<>(5);
    private final EventBannerBinding binding;
    private final RouteRecommendationClient client;
    private final Consumer<BannerEvent> navigate;
    private BannerEvent featuredEvent;
    private AlertDialog detailDialog;
    private boolean loading;
    private boolean active;
    private boolean restricted = true;
    private boolean navigationPending;
    private boolean closed;

    TodayEventBanner(EventBannerBinding binding, RouteRecommendationClient client,
                     Consumer<BannerEvent> navigate) {
        this.binding = binding;
        this.client = client;
        this.navigate = navigate;
        binding.eventNavigate.setEnabled(false);
        // 카드에서는 행사 정보를 먼저 확인하고 사용자가 선택한 경우에만 내비로 전달한다.
        binding.eventNavigate.setOnClickListener(view -> showFeaturedEventDetails());
        binding.eventPages.setVisibility(View.GONE);
        binding.eventArtworkPanel.setClickable(false);
        binding.eventArtworkPanel.setFocusable(false);
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
        dismissDetails();
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
        if (restricted) dismissDetails();
        updateControls();
    }

    void setNavigationPending(boolean pending) {
        navigationPending = pending;
        if (pending) dismissDetails();
        updateControls();
    }

    /** 서버 정렬 결과의 첫 행사를 오늘의 대표 추천으로 고정해 광고식 자동 전환을 막는다. */
    private void show(TodayEvents result) {
        loading = false;
        if (closed) return;
        featuredEvent = result.events().isEmpty() ? null : result.events().get(0);
        if (featuredEvent == null) {
            binding.eventHeading.setText(R.string.event_banner_label);
            binding.eventRegionBadge.setVisibility(View.GONE);
            binding.eventCategory.setVisibility(View.GONE);
            binding.eventTitle.setText(result.partial()
                    ? "행사를 불러오지 못했어요" : "오늘은 행사가 없어요");
            binding.eventDetail.setVisibility(View.GONE);
            binding.eventSource.setVisibility(View.GONE);
            binding.eventRecommendationReason.setVisibility(View.GONE);
            binding.eventRecommendationRow.setVisibility(View.GONE);
            binding.eventNavigate.setVisibility(View.GONE);
            binding.eventPages.setVisibility(View.GONE);
            binding.eventArt.setImageResource(R.drawable.event_art_empty);
            updateControls();
            return;
        }
        render(featuredEvent);
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

    /** 행사 분류, 장소, 일정과 제공처를 한눈에 읽히는 추천 정보 순서로 배치한다. */
    private void render(BannerEvent event) {
        binding.eventHeading.setText(R.string.event_banner_label);
        binding.eventRegionBadge.setText(binding.getRoot().getContext().getString(
                R.string.event_banner_region_today, event.region()));
        binding.eventRegionBadge.setVisibility(View.VISIBLE);
        binding.eventCategory.setText(categoryLabel(event.category()));
        binding.eventCategory.setVisibility(View.VISIBLE);
        binding.eventTitle.setText(event.title());

        String detail = event.venue();
        if (!event.schedule().isBlank()) {
            detail = detail.isBlank() ? event.schedule() : detail + " · " + event.schedule();
        }
        binding.eventDetail.setText(detail);
        binding.eventDetail.setVisibility(detail.isBlank() ? View.GONE : View.VISIBLE);

        binding.eventSource.setText(R.string.event_banner_reason);
        binding.eventSource.setVisibility(View.VISIBLE);
        binding.eventRecommendationReason.setText(R.string.event_banner_recommendation);
        binding.eventRecommendationReason.setVisibility(View.VISIBLE);
        binding.eventRecommendationRow.setVisibility(View.VISIBLE);
        binding.eventNavigate.setVisibility(View.VISIBLE);
        binding.eventPages.setVisibility(View.GONE);

        binding.eventArt.setImageResource(switch (event.category()) {
            case "PERFORMANCE" -> R.drawable.event_art_performance;
            case "EXHIBITION" -> R.drawable.event_art_exhibition;
            case "FESTIVAL" -> R.drawable.event_art_festival;
            default -> R.drawable.event_art_empty;
        });
        loadPhoto(event);
        updateControls();
    }

    /** 원격 이미지는 크기와 프로토콜을 제한하고 대표 추천이 바뀌지 않았을 때만 반영한다. */
    private void loadPhoto(BannerEvent event) {
        if (event.imageUrl().isBlank()) return;
        imageWorker.execute(() -> {
            Bitmap stored = photos.get(event.imageUrl());
            Bitmap photo = stored != null ? stored : downloadPhoto(event.imageUrl());
            if (stored == null && photo != null) photos.put(event.imageUrl(), photo);
            handler.post(() -> {
                if (photo == null || closed || featuredEvent != event) return;
                binding.eventArt.setImageBitmap(photo);
            });
        });
    }

    /** 추천 행사 상세를 먼저 보여주고 명시적으로 선택했을 때만 차량 내비를 실행한다. */
    private void showFeaturedEventDetails() {
        if (!active || restricted || navigationPending || featuredEvent == null
                || detailDialog != null) return;
        BannerEvent event = featuredEvent;
        String provider = event.source().equals("SEOUL") ? "서울특별시" : "한국관광공사";
        String category = binding.getRoot().getContext().getString(categoryLabel(event.category()));
        StringBuilder details = new StringBuilder(category);
        if (!event.venue().isBlank()) details.append("\n").append(event.venue());
        if (!event.schedule().isBlank()) details.append("\n").append(event.schedule());
        details.append("\n\n").append(binding.getRoot().getContext().getString(
                R.string.event_detail_provider, provider));

        AlertDialog dialog = new AlertDialog.Builder(binding.getRoot().getContext())
                .setTitle(event.title())
                .setMessage(details.toString())
                .setNegativeButton(R.string.event_detail_close, null)
                .setPositiveButton(R.string.event_detail_navigate, (ignored, which) -> {
                    if (active && !restricted && !navigationPending && featuredEvent == event) {
                        navigate.accept(event);
                    }
                })
                .create();
        detailDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (detailDialog == dialog) detailDialog = null;
        });
        dialog.show();
    }

    private void dismissDetails() {
        if (detailDialog == null) return;
        AlertDialog dialog = detailDialog;
        detailDialog = null;
        dialog.dismiss();
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
                && !navigationPending && featuredEvent != null);
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
