package com.example.justgo.backend.spot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * spot 테이블 접근을 담당하는 MyBatis Mapper입니다.
 * SQL은 같은 이름의 XML Mapper에 두어 Java 계층과 분리합니다.
 */
@Mapper
public interface SpotMapper {

    /**
     * XML의 #{...} 바인딩 동작을 검증하기 위한 최소 개발용 행을 추가합니다.
     *
     * @param name 장소 이름
     * @param theme 풍경 테마
     * @return 추가된 행 수
     */
    int insert(@Param("name") String name, @Param("theme") ScenicTheme theme);

    /**
     * 식별자로 한 장소를 조회합니다.
     *
     * @param id 데이터베이스 식별자
     * @return 조회 결과, 없으면 빈 Optional
     */
    Optional<SpotRecord> findById(@Param("id") long id);

    /**
     * 테스트에서 방금 추가한 행의 식별자를 안전한 파라미터 바인딩으로 찾습니다.
     *
     * @param name 장소 이름
     * @return 일치하는 최신 식별자, 없으면 빈 Optional
     */
    Optional<Long> findLatestIdByName(@Param("name") String name);

    /**
     * 테스트와 향후 관리 기능에서 좌표와 검수 상태가 있는 후보를 추가합니다.
     * 공개 API에서는 이 쓰기 기능을 노출하지 않습니다.
     *
     * @return 추가된 행 수
     */
    int insertCandidate(
            @Param("name") String name,
            @Param("theme") ScenicTheme theme,
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude,
            @Param("verificationStatus") SpotVerificationStatus verificationStatus,
            @Param("enabled") boolean enabled);

    /**
     * 지정한 테마에서 활성화된 검수 완료 후보만 식별자 순으로 조회합니다.
     *
     * @param theme 풍경 테마
     * @param limit 최대 조회 건수
     * @return 검수 완료 장소 후보
     */
    List<SpotCandidateRecord> findVerifiedCandidatesByTheme(
            @Param("theme") ScenicTheme theme,
            @Param("limit") int limit);
}
