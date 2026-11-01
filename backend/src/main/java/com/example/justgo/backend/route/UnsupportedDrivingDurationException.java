package com.example.justgo.backend.route;

/**
 * 제품에서 허용한 30·60·90·120분 이외의 주행 시간이 들어왔을 때 발생합니다.
 */
public class UnsupportedDrivingDurationException extends RuntimeException {

    public UnsupportedDrivingDurationException(int desiredMinutes) {
        super("희망 주행 시간은 30, 60, 90, 120분 중 하나여야 합니다: " + desiredMinutes);
    }
}
