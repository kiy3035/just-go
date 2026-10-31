package com.example.justgo.backend.spot;

/**
 * 데이터베이스의 최소 spot 행을 표현하는 모델입니다.
 * 향후 외부 API 응답 DTO와 혼용하지 않고 별도 DTO로 변환합니다.
 *
 * @param id 데이터베이스 식별자
 * @param name 장소 이름
 * @param theme 풍경 테마
 */
public record SpotRecord(Long id, String name, ScenicTheme theme) {
}
