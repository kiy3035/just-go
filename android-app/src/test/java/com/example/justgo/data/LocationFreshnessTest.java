package com.example.justgo.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationFreshnessTest {
    private static final long NOW = 100_000_000_000L;

    @Test
    public void acceptsCurrentValidCoordinateThroughMaximumAge() {
        assertTrue(LocationFreshness.isUsable(NOW, NOW, 37.5, 127.0));
        assertTrue(LocationFreshness.isUsable(
                NOW, NOW - LocationFreshness.MAX_AGE_NANOS, -90, 180));
    }

    @Test
    public void rejectsStaleFutureAndInvalidCoordinates() {
        assertFalse(LocationFreshness.isUsable(
                NOW, NOW - LocationFreshness.MAX_AGE_NANOS - 1, 37.5, 127.0));
        assertFalse(LocationFreshness.isUsable(NOW, NOW + 1, 37.5, 127.0));
        assertFalse(LocationFreshness.isUsable(NOW, NOW, 91, 127.0));
        assertFalse(LocationFreshness.isUsable(NOW, NOW, 37.5, Double.NaN));
    }
}
