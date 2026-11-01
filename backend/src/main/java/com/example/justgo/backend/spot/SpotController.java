package com.example.justgo.backend.spot;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검수 완료 경관 장소 조회를 제공하는 REST 진입점입니다.
 * 조회 규칙과 DB 접근은 서비스·Mapper 계층에 위임합니다.
 */
@RestController
@RequestMapping("/api/v1/spots")
public class SpotController {

    private final SpotService spotService;

    public SpotController(SpotService spotService) {
        this.spotService = spotService;
    }

    /**
     * 풍경 테마별 활성 검수 장소를 조회합니다.
     */
    @GetMapping
    public ResponseEntity<SpotCandidateListResponse> findVerifiedCandidates(
            @RequestParam ScenicTheme theme,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(spotService.findVerifiedCandidates(theme, limit));
    }
}
