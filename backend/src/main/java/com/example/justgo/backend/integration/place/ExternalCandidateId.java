package com.example.justgo.backend.integration.place;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 외부 원천 식별자를 DB 양수 ID와 겹치지 않는 안정적인 음수 ID로 축약합니다. */
public final class ExternalCandidateId {

    private ExternalCandidateId() {
    }

    public static long from(String source, String externalId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((source + ':' + externalId)
                    .getBytes(StandardCharsets.UTF_8));
            long positive = ByteBuffer.wrap(hash).getLong() & Long.MAX_VALUE;
            return positive == 0L ? -1L : -positive;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
