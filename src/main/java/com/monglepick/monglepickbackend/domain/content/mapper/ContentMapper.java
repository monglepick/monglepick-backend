package com.monglepick.monglepickbackend.domain.content.mapper;

import com.monglepick.monglepickbackend.domain.content.entity.Banner;
import com.monglepick.monglepickbackend.domain.content.entity.ProfanityWord;
import com.monglepick.monglepickbackend.domain.content.entity.ToxicityLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 콘텐츠 관리 MyBatis Mapper.
 *
 * <p>banners, profanity_dictionary, toxicity_logs 테이블의 CRUD를 담당한다.
 * 관리자 페이지의 배너 관리, 혐오표현 관리, 유해성 로그 조회에서 사용된다.</p>
 *
 * <p>SQL 정의: {@code resources/mapper/content/ContentMapper.xml}</p>
 */
@Mapper
public interface ContentMapper {

    // ══════════════════════════════════════════════
    // Banner (배너 관리)
    // ══════════════════════════════════════════════

    /** PK로 배너 조회 (없으면 null) */
    Banner findBannerById(@Param("bannerId") Long bannerId);

    /** 위치 + 활성 상태 배너 목록 (프론트엔드 노출용, 정렬순서 ASC) */
    List<Banner> findActiveBannersByPosition(@Param("position") String position);

    /** 활성 배너 전체 목록 (sort_order 오름차순) — 메인 페이지 배너 노출용 */
    List<Banner> findAllActiveBanners();

    /** 전체 배너 목록 (관리자, 페이징, sort_order ASC) */
    List<Banner> findAllBanners(@Param("offset") int offset, @Param("limit") int limit);

    /** 전체 배너 수 */
    long countAllBanners();

    /** 배너 등록 */
    void insertBanner(Banner banner);

    /** 배너 수정 */
    void updateBanner(Banner banner);

    /** 배너 삭제 */
    void deleteBanner(@Param("bannerId") Long bannerId);

    // ══════════════════════════════════════════════
    // ProfanityWord (금칙어 사전)
    // ══════════════════════════════════════════════

    /** PK로 금칙어 조회 */
    ProfanityWord findProfanityById(@Param("profanityId") Long profanityId);

    /** 활성 금칙어 전체 목록 (모더레이션 엔진 로드용) */
    List<ProfanityWord> findActiveProfanityWords();

    /** 금칙어 목록 (관리자, 카테고리 필터 + 페이징) */
    List<ProfanityWord> findProfanityWords(@Param("category") String category,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /** 금칙어 수 (카테고리 필터) */
    long countProfanityWords(@Param("category") String category);

    /** 금칙어 등록 */
    void insertProfanity(ProfanityWord word);

    /** 금칙어 수정 */
    void updateProfanity(ProfanityWord word);

    /** 금칙어 삭제 */
    void deleteProfanity(@Param("profanityId") Long profanityId);

    // ══════════════════════════════════════════════
    // ToxicityLog (유해성 감지 로그)
    // ══════════════════════════════════════════════

    /** PK로 로그 조회 (없으면 null) */
    ToxicityLog findToxicityLogById(@Param("toxicityLogId") Long toxicityLogId);

    /** 심각도별 미처리 로그 목록 (관리자 대시보드, 페이징) */
    List<ToxicityLog> findPendingLogs(@Param("severity") String severity,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /** 미처리 로그 수 (심각도 필터) */
    long countPendingLogs(@Param("severity") String severity);

    /**
     * 전체 유해성 로그 페이징 조회 (최신순) — 관리자 혐오표현 탭.
     *
     * <p>{@code minScore=null}이면 필터 없이 전체, 지정 시 {@code toxicity_score >= minScore}만 반환.</p>
     */
    List<ToxicityLog> findAllToxicityLogs(@Param("minScore") Float minScore,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 전체 유해성 로그 총 건수 (minScore 필터 포함) */
    long countAllToxicityLogs(@Param("minScore") Float minScore);

    /** 유해성 로그 등록 */
    void insertToxicityLog(ToxicityLog log);

    /** 관리자 조치 처리 (actionTaken, processedAt 갱신) */
    void processAction(@Param("toxicityLogId") Long toxicityLogId,
                        @Param("actionTaken") String actionTaken);
}
