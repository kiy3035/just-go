package com.example.justgo.backend.route;

import java.math.BigDecimal;

import com.example.justgo.backend.spot.ScenicTheme;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 정차 중 사용자가 비교할 넓은 장소 후보 목록을 제공합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/places")
public class CandidatePlaceController {

    private final CandidatePlaceService candidatePlaceService;

    public CandidatePlaceController(CandidatePlaceService candidatePlaceService) {
        this.candidatePlaceService = candidatePlaceService;
    }

    @GetMapping("/candidates")
    public CandidatePlaceListResponse findCandidates(
            @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @RequestParam @Min(10) @Max(240) int desiredMinutes,
            @RequestParam TripType tripType,
            @RequestParam ScenicTheme theme,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return candidatePlaceService.findCandidates(
                latitude, longitude, desiredMinutes, tripType, theme, limit);
    }
}
