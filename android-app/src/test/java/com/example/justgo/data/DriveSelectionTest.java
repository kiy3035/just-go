package com.example.justgo.data;
import org.junit.Test;
import static org.junit.Assert.*;
public class DriveSelectionTest {
    @Test public void acceptsBoundariesAndPresets() {
        for (String value : new String[]{"10", "30", "60", "90", "120", "240"})
            assertEquals(Integer.parseInt(value), DriveSelection.parseMinutes(value));
    }
    @Test public void rejectsInvalidValues() {
        for (String value : new String[]{"", "9", "241", "31", "12.5", "abc"}) {
            try { DriveSelection.parseMinutes(value); fail(value); }
            catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
        }
    }

    @Test public void acceptsSignedCoordinatesAndBoundaries() {
        assertEquals(-90.0, DriveSelection.parseCoordinate(" -90 ", 90), 0.0);
        assertEquals(180.0, DriveSelection.parseCoordinate("180", 180), 0.0);
        assertEquals(-0.125, DriveSelection.parseCoordinate("-0.125", 180), 0.0);
    }

    @Test public void rejectsNonFiniteAndOutOfRangeCoordinates() {
        for (String value : new String[]{"", "NaN", "Infinity", "-90.01", "90.01"}) {
            assertThrows(IllegalArgumentException.class, () -> DriveSelection.parseCoordinate(value, 90));
        }
        assertThrows(IllegalArgumentException.class, () -> DriveSelection.parseCoordinate("180.01", 180));
    }
}
