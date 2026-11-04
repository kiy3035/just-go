package com.example.justgo.backend.spot;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 명시적인 candidate-import 프로필에서만 로컬 후보 파일을 한 번 가져옵니다. */
@Component
@Profile("candidate-import")
public class SpotCandidateImportRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpotCandidateImportRunner.class);

    private final SpotCandidateImportFileReader fileReader;
    private final SpotCandidateImportService importService;
    private final String configuredPath;
    private final boolean dryRun;

    public SpotCandidateImportRunner(
            SpotCandidateImportFileReader fileReader,
            SpotCandidateImportService importService,
            @Value("${justgo.spot-import.path:}") String configuredPath,
            @Value("${justgo.spot-import.dry-run:false}") boolean dryRun) {
        this.fileReader = fileReader;
        this.importService = importService;
        this.configuredPath = configuredPath;
        this.dryRun = dryRun;
    }

    /** 파일 경로나 후보값은 로그에 남기지 않고 처리 건수만 기록합니다. */
    @Override
    public void run(ApplicationArguments args) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalArgumentException("SCENIC_CANDIDATE_IMPORT_FILE 설정이 필요합니다.");
        }
        SpotCandidateImportDocument document = fileReader.read(configuredFile());
        if (dryRun) {
            SpotCandidateImportValidationResult result =
                    importService.validateCandidates(document.candidates());
            LOGGER.info(
                    "경관 후보 사전 검증 완료: 전체 {}건, 등록 가능 {}건, DB 중복 {}건, 파일 내 중복 {}건",
                    result.totalCount(),
                    result.readyCount(),
                    result.existingDuplicateCount(),
                    result.inputDuplicateCount());
            return;
        }
        SpotCandidateImportResult result = importService.importCandidates(document.candidates());
        LOGGER.info(
                "경관 후보 가져오기 완료: 신규 {}건, 중복 제외 {}건",
                result.insertedCount(),
                result.skippedDuplicateCount());
    }

    /** 잘못된 경로 문자열이 예외 메시지에 그대로 노출되지 않도록 일반 오류로 바꿉니다. */
    private Path configuredFile() {
        try {
            return Path.of(configuredPath);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("경관 후보 파일 경로 형식을 확인해 주세요.");
        }
    }
}
