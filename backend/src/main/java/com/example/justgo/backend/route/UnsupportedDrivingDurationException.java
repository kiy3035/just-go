package com.example.justgo.backend.route;

/**
 * 임시 개발 기준인 10~240분과 5분 단위를 벗어난 시간이 들어왔을 때 발생합니다.
 */
public class UnsupportedDrivingDurationException extends RuntimeException {

    public UnsupportedDrivingDurationException(int desiredMinutes) {
        super("희망 주행 시간은 10~240분 범위의 5분 단위여야 합니다: " + desiredMinutes);
    }
}
