package com.example.justgo.backend.spot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 JSON 파일을 크기 제한과 엄격한 필드 검사 아래 읽습니다.
 * 파일 경로나 원문은 로그에 남기지 않습니다.
 */
@Component
@Profile("candidate-import")
public class SpotCandidateImportFileReader {

    private static final long MAX_FILE_BYTES = 1_048_576L;
    private static final int MAX_CANDIDATES = 500;

    private final ObjectMapper objectMapper;

    public SpotCandidateImportFileReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 읽을 수 있는 1MB 이하 JSON 파일을 최대 500개 후보 문서로 변환합니다. */
    public SpotCandidateImportDocument read(Path path) {
        try {
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IllegalArgumentException("경관 후보 파일을 읽을 수 없습니다.");
            }
            if (Files.size(path) > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("경관 후보 파일은 1MB 이하여야 합니다.");
            }
            SpotCandidateImportDocument document;
            try (InputStream inputStream = Files.newInputStream(path)) {
                document = objectMapper.readerFor(SpotCandidateImportDocument.class)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .readValue(inputStream);
            }
            if (document == null || document.candidates() == null) {
                throw new IllegalArgumentException("candidates 배열이 필요합니다.");
            }
            if (document.candidates().isEmpty()) {
                throw new IllegalArgumentException("가져올 경관 후보가 한 건 이상 필요합니다.");
            }
            if (document.candidates().size() > MAX_CANDIDATES) {
                throw new IllegalArgumentException("경관 후보는 한 번에 500건까지만 가져올 수 있습니다.");
            }
            return document;
        } catch (IOException exception) {
            throw new IllegalArgumentException("경관 후보 JSON 형식을 확인할 수 없습니다.");
        }
    }
}
