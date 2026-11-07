package com.example.justgo.pleos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;

public class NavigationRoutePlanTest {
    @Test public void acceptsSingleDestination() {
        NavigationGateway.Stop destination = stop(1, NavigationGateway.StopType.DESTINATION, "도착지");

        NavigationRoutePlan.Plan plan = NavigationRoutePlan.from(List.of(destination));

        assertEquals(destination, plan.destination());
        assertEquals(List.of(), plan.waypoints());
    }

    @Test public void sortsTwoWaypointsBeforeDestination() {
        NavigationGateway.Stop first = stop(1, NavigationGateway.StopType.WAYPOINT, "첫 경유지");
        NavigationGateway.Stop second = stop(2, NavigationGateway.StopType.WAYPOINT, "둘째 경유지");
        NavigationGateway.Stop destination = stop(3, NavigationGateway.StopType.DESTINATION, "도착지");

        NavigationRoutePlan.Plan plan = NavigationRoutePlan.from(List.of(destination, second, first));

        assertEquals(List.of(first, second), plan.waypoints());
        assertEquals(destination, plan.destination());
    }

    @Test public void rejectsMissingDestination() {
        assertThrows(IllegalArgumentException.class, () -> NavigationRoutePlan.from(List.of(
                stop(1, NavigationGateway.StopType.WAYPOINT, "경유지"))));
    }

    @Test public void rejectsMoreThanTwoWaypoints() {
        assertThrows(IllegalArgumentException.class, () -> NavigationRoutePlan.from(List.of(
                stop(1, NavigationGateway.StopType.WAYPOINT, "첫 경유지"),
                stop(2, NavigationGateway.StopType.WAYPOINT, "둘째 경유지"),
                stop(3, NavigationGateway.StopType.WAYPOINT, "셋째 경유지"),
                stop(4, NavigationGateway.StopType.DESTINATION, "도착지"))));
    }

    @Test public void rejectsDestinationBeforeWaypoint() {
        assertThrows(IllegalArgumentException.class, () -> NavigationRoutePlan.from(List.of(
                stop(1, NavigationGateway.StopType.DESTINATION, "도착지"),
                stop(2, NavigationGateway.StopType.WAYPOINT, "경유지"))));
    }

    private static NavigationGateway.Stop stop(
            int sequence, NavigationGateway.StopType type, String name) {
        return new NavigationGateway.Stop(sequence, type, name, 37.5, 127.0);
    }
}
