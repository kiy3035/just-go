package com.example.justgo.backend.spot;

import java.math.BigDecimal;

/** 사용자 위치를 저장하지 않고 두 WGS84 좌표의 직선거리를 계산합니다. */
public final class ScenicPlaceDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private ScenicPlaceDistance() {
    }

    public static long meters(
            BigDecimal firstLatitude,
            BigDecimal firstLongitude,
            BigDecimal secondLatitude,
            BigDecimal secondLongitude) {
        double firstLatRadians = Math.toRadians(firstLatitude.doubleValue());
        double secondLatRadians = Math.toRadians(secondLatitude.doubleValue());
        double latitudeDelta = secondLatRadians - firstLatRadians;
        double longitudeDelta = Math.toRadians(
                secondLongitude.doubleValue() - firstLongitude.doubleValue());
        double haversine = Math.pow(Math.sin(latitudeDelta / 2.0), 2.0)
                + Math.cos(firstLatRadians) * Math.cos(secondLatRadians)
                * Math.pow(Math.sin(longitudeDelta / 2.0), 2.0);
        double centralAngle = 2.0 * Math.asin(Math.sqrt(Math.min(1.0, haversine)));
        return Math.round(EARTH_RADIUS_METERS * centralAngle);
    }
}
