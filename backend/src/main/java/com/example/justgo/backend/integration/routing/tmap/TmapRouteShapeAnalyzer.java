package com.example.justgo.backend.integration.routing.tmap;

import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TMAP GeoJSON 선형에서 같은 도로 선분을 다시 주행한 비율을 계산합니다.
 * 원시 좌표는 계산 중에만 사용하며 반환하거나 저장하지 않습니다.
 */
final class TmapRouteShapeAnalyzer {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;
    private static final int COORDINATE_SCALE = 6;

    /**
     * 방향이 반대여도 같은 선분으로 보고, 최초 통과 이후의 거리 비율을 정수 백분율로 반환합니다.
     * 분석할 수 있는 선분이 없으면 품질을 추측하지 않고 null을 반환합니다.
     */
    Integer calculateRepeatedSegmentPercent(List<List<TmapCoordinate>> lineStrings) {
        Set<SegmentKey> visitedSegments = new HashSet<>();
        double totalDistanceMeters = 0.0;
        double repeatedDistanceMeters = 0.0;

        for (List<TmapCoordinate> lineString : lineStrings) {
            for (int index = 1; index < lineString.size(); index++) {
                TmapCoordinate start = lineString.get(index - 1);
                TmapCoordinate end = lineString.get(index);
                SegmentKey key = SegmentKey.of(start, end);
                if (key.start().equals(key.end())) {
                    continue;
                }

                double segmentDistanceMeters = distanceMeters(start, end);
                totalDistanceMeters += segmentDistanceMeters;
                if (!visitedSegments.add(key)) {
                    repeatedDistanceMeters += segmentDistanceMeters;
                }
            }
        }

        if (totalDistanceMeters == 0.0) {
            return null;
        }
        return (int) Math.round(repeatedDistanceMeters * 100.0 / totalDistanceMeters);
    }

    /** WGS84 두 점 사이 거리를 Haversine 공식으로 근사합니다. */
    private double distanceMeters(TmapCoordinate start, TmapCoordinate end) {
        double startLatitude = Math.toRadians(start.latitude().doubleValue());
        double endLatitude = Math.toRadians(end.latitude().doubleValue());
        double latitudeDifference = endLatitude - startLatitude;
        double longitudeDifference = Math.toRadians(
                end.longitude().subtract(start.longitude()).doubleValue());
        double haversine = Math.pow(Math.sin(latitudeDifference / 2.0), 2)
                + Math.cos(startLatitude) * Math.cos(endLatitude)
                * Math.pow(Math.sin(longitudeDifference / 2.0), 2);
        double boundedHaversine = Math.min(1.0, haversine);
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(boundedHaversine));
    }

    /** 좌표 오차를 줄이면서 역방향 선분도 같은 키로 정규화합니다. */
    private record SegmentKey(PointKey start, PointKey end) {

        private static SegmentKey of(TmapCoordinate start, TmapCoordinate end) {
            PointKey first = PointKey.of(start);
            PointKey second = PointKey.of(end);
            return first.compareTo(second) <= 0
                    ? new SegmentKey(first, second)
                    : new SegmentKey(second, first);
        }
    }

    /** 소수점 여섯 자리로 정규화한 WGS84 좌표 키입니다. */
    private record PointKey(long latitude, long longitude) implements Comparable<PointKey> {

        private static PointKey of(TmapCoordinate coordinate) {
            return new PointKey(
                    coordinate.latitude().movePointRight(COORDINATE_SCALE)
                            .setScale(0, RoundingMode.HALF_UP)
                            .longValueExact(),
                    coordinate.longitude().movePointRight(COORDINATE_SCALE)
                            .setScale(0, RoundingMode.HALF_UP)
                            .longValueExact());
        }

        @Override
        public int compareTo(PointKey other) {
            int latitudeResult = Long.compare(latitude, other.latitude);
            return latitudeResult != 0
                    ? latitudeResult
                    : Long.compare(longitude, other.longitude);
        }
    }
}
