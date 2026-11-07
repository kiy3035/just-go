package com.example.justgo.backend.integration.routing.tmap;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** TMAP 설정이 빈 값과 저장소 예시 키를 실제 인증값으로 오인하지 않는지 검증합니다. */
class TmapClientPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** `.env.example`의 자리표시자 키로 tmap 프로필을 시작하지 못하게 합니다. */
    @Test
    void rejectsExampleAppKey() {
        TmapClientProperties properties = validProperties();
        properties.setAppKey("replace-with-local-tmap-app-key");

        assertThat(validator.validate(properties))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("appKey");
                    assertThat(violation.getMessage()).contains("예시 앱 키");
                });
    }

    /** 형식을 추측하지 않고 자리표시자가 아닌 키는 구성 단계에서 허용합니다. */
    @Test
    void acceptsNonPlaceholderAppKey() {
        TmapClientProperties properties = validProperties();
        properties.setAppKey("TEST_NON_SECRET_APP_KEY");

        assertThat(validator.validate(properties)).isEmpty();
    }

    /** 앱 키 외의 필수 기본값이 유효한 테스트 설정을 만듭니다. */
    private TmapClientProperties validProperties() {
        TmapClientProperties properties = new TmapClientProperties();
        properties.setAppKey("TEST_NON_SECRET_APP_KEY");
        return properties;
    }
}
