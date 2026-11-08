package com.example.justgo.backend.route;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.example.justgo.backend.spot.SpotCandidateRecord;
import org.springframework.stereotype.Component;

/** 공공 장소 좌표를 24시간보다 짧게 보관해 선택 후 한 건만 경로 계산하게 합니다. */
@Component
public class CandidatePlaceSelectionStore {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final int MAX_ENTRIES = 4_096;

    private final Clock clock;
    private final Map<String, StoredCandidate> candidates = new LinkedHashMap<>();

    public CandidatePlaceSelectionStore() {
        this(Clock.systemUTC());
    }

    CandidatePlaceSelectionStore(Clock clock) {
        this.clock = clock;
    }

    public synchronized String register(SpotCandidateRecord candidate) {
        removeExpired();
        while (candidates.size() >= MAX_ENTRIES) {
            candidates.remove(candidates.keySet().iterator().next());
        }
        String token = UUID.randomUUID().toString();
        candidates.put(token, new StoredCandidate(candidate, Instant.now(clock).plus(TOKEN_TTL)));
        return token;
    }

    public synchronized Optional<SpotCandidateRecord> resolve(String token) {
        removeExpired();
        StoredCandidate stored = candidates.get(token);
        return stored == null ? Optional.empty() : Optional.of(stored.candidate());
    }

    private void removeExpired() {
        Instant now = Instant.now(clock);
        candidates.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record StoredCandidate(SpotCandidateRecord candidate, Instant expiresAt) {
    }
}
