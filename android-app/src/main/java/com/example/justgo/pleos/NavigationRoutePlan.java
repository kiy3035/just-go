package com.example.justgo.pleos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class NavigationRoutePlan {
    record Plan(NavigationGateway.Stop destination, List<NavigationGateway.Stop> waypoints) { }

    private NavigationRoutePlan() { }

    static Plan from(List<NavigationGateway.Stop> stops) {
        if (stops == null || stops.isEmpty()) {
            throw new IllegalArgumentException("목적지가 없어 내비로 전달할 수 없습니다.");
        }
        List<NavigationGateway.Stop> ordered = new ArrayList<>(stops);
        ordered.sort(Comparator.comparingInt(NavigationGateway.Stop::sequence));
        Set<Integer> sequences = new HashSet<>();
        NavigationGateway.Stop destination = null;
        List<NavigationGateway.Stop> waypoints = new ArrayList<>();
        for (NavigationGateway.Stop stop : ordered) {
            validate(stop, sequences);
            if (stop.type() == NavigationGateway.StopType.DESTINATION) {
                if (destination != null) {
                    throw new IllegalArgumentException("목적지가 두 개 이상이라 내비로 전달할 수 없습니다.");
                }
                destination = stop;
            } else {
                waypoints.add(stop);
            }
        }
        if (destination == null) {
            throw new IllegalArgumentException("목적지가 없어 내비로 전달할 수 없습니다.");
        }
        if (waypoints.size() > 2) {
            throw new IllegalArgumentException("Pleos 내비는 경유지를 최대 두 곳까지 받을 수 있습니다.");
        }
        if (ordered.get(ordered.size() - 1) != destination) {
            throw new IllegalArgumentException("목적지는 경유지 다음의 마지막 순서여야 합니다.");
        }
        return new Plan(destination, List.copyOf(waypoints));
    }

    private static void validate(NavigationGateway.Stop stop, Set<Integer> sequences) {
        if (stop == null || stop.type() == null || stop.sequence() < 1
                || !sequences.add(stop.sequence()) || stop.name() == null || stop.name().isBlank()
                || !Double.isFinite(stop.latitude()) || stop.latitude() < -90 || stop.latitude() > 90
                || !Double.isFinite(stop.longitude()) || stop.longitude() < -180 || stop.longitude() > 180) {
            throw new IllegalArgumentException("내비로 전달할 경로 지점 정보가 올바르지 않습니다.");
        }
    }
}
