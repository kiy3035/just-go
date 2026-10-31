package com.example.justgo.backend.spot;

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
}
