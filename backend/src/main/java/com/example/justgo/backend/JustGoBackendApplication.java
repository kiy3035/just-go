package com.example.justgo.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * just go 백엔드의 실행 진입점입니다.
 * 비즈니스 기능은 각 기능 패키지의 서비스 계층에 두고 이 클래스에는 시작 설정만 둡니다.
 */
@SpringBootApplication
public class JustGoBackendApplication {

    /**
     * Spring Boot 애플리케이션을 시작합니다.
     *
     * @param args 실행 시 전달된 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(JustGoBackendApplication.class, args);
    }
}
