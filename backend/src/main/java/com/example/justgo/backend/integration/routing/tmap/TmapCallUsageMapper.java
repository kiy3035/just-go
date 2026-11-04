package com.example.justgo.backend.integration.routing.tmap;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** TMAP 날짜별 호출 예약 건수를 PostgreSQL에서 원자적으로 관리합니다. */
@Mapper
public interface TmapCallUsageMapper {

    /** 상한 미만일 때만 건수를 증가시키고 증가 후 건수를 반환합니다. */
    Integer reserveCall(
            @Param("usageDate") LocalDate usageDate,
            @Param("dailyLimit") int dailyLimit);

    /** 통합 테스트와 운영 점검에서 특정 날짜의 예약 건수를 조회합니다. */
    Integer findUsedCalls(@Param("usageDate") LocalDate usageDate);
}
