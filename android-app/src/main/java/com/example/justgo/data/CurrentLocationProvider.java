package com.example.justgo.data;

import android.content.Context;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public final class CurrentLocationProvider {
    public interface Callback {
        void onLocation(Location location);
        void onError(String message);
    }

    private final Context context;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationListener listener;

    public CurrentLocationProvider(Context context) {
        this.context = context.getApplicationContext();
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public void request(Callback callback) {
        cancel();
        if (manager == null || !manager.isLocationEnabled()) {
            callback.onError("기기의 위치 기능을 켜고 다시 검색해 주세요.");
            return;
        }
        listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                if (listener != this || !isUsable(location)) return;
                cancel();
                callback.onLocation(location);
            }
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        };
        try {
            boolean requested = false;
            Location freshestKnown = null;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (LocationManager.GPS_PROVIDER.equals(provider)
                        && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) continue;
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper());
                    requested = true;
                    Location known = manager.getLastKnownLocation(provider);
                    if (isUsable(known) && (freshestKnown == null
                            || known.getElapsedRealtimeNanos()
                            > freshestKnown.getElapsedRealtimeNanos())) {
                        freshestKnown = known;
                    }
                }
            }
            if (!requested) {
                cancel();
                callback.onError("사용 가능한 위치 서비스가 없습니다. 기기 위치 설정을 확인해 주세요.");
                return;
            }
            if (freshestKnown != null) {
                cancel();
                callback.onLocation(freshestKnown);
                return;
            }
            handler.postDelayed(() -> {
                if (cancel()) callback.onError("현재 위치를 가져오지 못했습니다. 위치 수신 상태를 확인한 뒤 다시 검색해 주세요.");
            }, 20_000L);
        } catch (SecurityException | IllegalArgumentException error) {
            cancel();
            callback.onError("위치 권한과 기기 위치 설정을 확인해 주세요.");
        }
    }

    private boolean isUsable(Location location) {
        return location != null && LocationFreshness.isUsable(
                SystemClock.elapsedRealtimeNanos(),
                location.getElapsedRealtimeNanos(),
                location.getLatitude(),
                location.getLongitude());
    }

    public boolean cancel() {
        handler.removeCallbacksAndMessages(null);
        if (listener == null) return false;
        LocationListener previous = listener;
        listener = null;
        try {
            manager.removeUpdates(previous);
        } catch (SecurityException ignored) {
            // Permission may have been revoked while waiting for location.
        }
        return true;
    }
}
