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
}
