package com.example.justgo.backend.spot;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 로컬 후보 파일이 엄격한 JSON 구조와 최소 한 건 규칙을 지키는지 검증합니다. */
class SpotCandidateImportFileReaderTest {

    @TempDir
    Path temporaryDirectory;

    private final SpotCandidateImportFileReader fileReader =
            new SpotCandidateImportFileReader(new ObjectMapper().findAndRegisterModules());

    /** 오타 난 필드를 조용히 무시하지 않고 파일 전체를 거부합니다. */
    @Test
    void rejectsUnknownJsonField() throws IOException {
        Path file = write("""
                {
                  "candidates": [],
                  "unknownField": "TEST_ONLY"
                }
                """);

        assertThatThrownBy(() -> fileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON 형식");
    }

    /** 빈 파일을 성공으로 오인하지 않도록 후보가 한 건 이상 있어야 합니다. */
    @Test
    void rejectsEmptyCandidateArray() throws IOException {
        Path file = write("{\"candidates\": []}");

        assertThatThrownBy(() -> fileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("한 건 이상");
    }

    /** 테스트 임시 디렉터리에만 JSON을 기록합니다. */
    private Path write(String content) throws IOException {
        Path file = temporaryDirectory.resolve("candidates-test-only.json");
        return Files.writeString(file, content);
    }
}
