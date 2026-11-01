package com.example.justgo.backend.route;

import java.math.BigDecimal;

/** Android 앱이 차량 내비 전달 순서를 구성할 수 있도록 제공하는 경로 지점입니다. */
public record RouteRecommendationStop(
        int sequence,
        String type,
        String name,
        BigDecimal latitude,
        BigDecimal longitude) {
}
