package com.example.justgo.backend.route;

import java.math.BigDecimal;

import com.example.justgo.backend.spot.InvalidSpotLimitException;
import com.example.justgo.backend.spot.ScenicPlaceDistance;
import com.example.justgo.backend.spot.ScenicTheme;
import com.example.justgo.backend.spot.SpotCandidateQuery;
import org.springframework.stereotype.Service;

/** 경로 API를 호출하기 전에 넓은 장소 후보 목록과 단기 선택 토큰을 만듭니다. */
@Service
public class CandidatePlaceService {

    private static final int MAX_LIMIT = 50;

    private final SpotCandidateQuery candidateQuery;
    private final CandidatePlaceSelectionStore selectionStore;
    private final RouteCandidateDistancePolicy distancePolicy = new RouteCandidateDistancePolicy();

    public CandidatePlaceService(
            SpotCandidateQuery candidateQuery,
            CandidatePlaceSelectionStore selectionStore) {
        this.candidateQuery = candidateQuery;
        this.selectionStore = selectionStore;
    }

    public CandidatePlaceListResponse findCandidates(
            BigDecimal latitude,
            BigDecimal longitude,
            int desiredMinutes,
            TripType tripType,
            ScenicTheme theme,
            int limit) {
        if (desiredMinutes < 10 || desiredMinutes > 240 || desiredMinutes % 5 != 0) {
            throw new UnsupportedDrivingDurationException(desiredMinutes);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidSpotLimitException(limit);
        }
        long maxDistanceMeters = distancePolicy.maxStraightLineDistanceMeters(
                desiredMinutes, tripType);
        var candidates = candidateQuery.loadNearbyVerifiedCandidates(
                theme, latitude, longitude, maxDistanceMeters, limit);
        var responses = candidates.stream()
                .map(candidate -> new CandidatePlaceResponse(
                        selectionStore.register(candidate),
                        candidate.name(),
                        candidate.theme(),
                        candidate.latitude(),
                        candidate.longitude(),
                        ScenicPlaceDistance.meters(
                                latitude,
                                longitude,
                                candidate.latitude(),
                                candidate.longitude()),
                        candidate.source()))
                .toList();
        return new CandidatePlaceListResponse(
                theme,
                responses.size(),
                Math.toIntExact(CandidatePlaceSelectionStore.TOKEN_TTL.toMinutes()),
                responses);
    }
}
