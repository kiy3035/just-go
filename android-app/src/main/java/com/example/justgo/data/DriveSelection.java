package com.example.justgo.data;
public final class DriveSelection {
    private DriveSelection() {}
    public static int parseMinutes(String value) {
        final int minutes;
        try { minutes = Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("10~240분을 5분 단위로 입력해 주세요."); }
        if (minutes < 10 || minutes > 240 || minutes % 5 != 0)
            throw new IllegalArgumentException("10~240분을 5분 단위로 입력해 주세요.");
        return minutes;
    }

    public static double parseCoordinate(String value, double limit) {
        double coordinate = Double.parseDouble(value.trim());
        if (!Double.isFinite(coordinate) || Math.abs(coordinate) > limit) {
            throw new IllegalArgumentException("좌표가 범위를 벗어났습니다.");
        }
        return coordinate;
    }
}
