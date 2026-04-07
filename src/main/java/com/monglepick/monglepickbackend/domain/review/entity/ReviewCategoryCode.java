package com.monglepick.monglepickbackend.domain.review.entity;

/**
 * 리뷰 작성 가능 카테고리 코드 (review_category_code).
 *
 * <p>엑셀 첫 시트(t2_09_RDBMS상세데이터의 사본) 5번 reviews 테이블 8번 컬럼 정의를
 * 코드 레벨로 매핑한 enum입니다.</p>
 *
 * <h3>엑셀 출처</h3>
 * <ul>
 *   <li>행81 한글 헤더: <b>리뷰작성 가능 카테고리코드</b></li>
 *   <li>행82 영문 헤더: <i>(공란 — 영문 컬럼명 누락 상태)</i></li>
 *   <li>행83~88 샘플: 1.극장영수증인증 / 2.도장깨기 / 3.월드컵 / 4.위시리스트 / 5. ai 추천 / 6.플레이리스트</li>
 * </ul>
 *
 * <h3>영문명 결정 근거</h3>
 * <p>엑셀에 영문 컬럼명이 누락되어 있으므로, 한글 헤더와 샘플 6종을 기준으로
 * 도메인 의미가 명확한 영문 식별자를 부여한다. {@code @Enumerated(EnumType.STRING)}로
 * DB에는 enum 이름이 저장되어 가독성과 typo 방지를 동시에 확보한다.</p>
 *
 * <h3>{@link com.monglepick.monglepickbackend.domain.review.entity.Review#reviewSource} 와의 차이</h3>
 * <ul>
 *   <li>{@code review_source} — 어느 구체 엔티티(채팅 세션 ID·월드컵 매치 ID 등)에서 작성됐는지의 <b>참조 ID</b>.
 *       예: {@code chat_ses_001}, {@code cup_mch_005}</li>
 *   <li>{@code review_category_code}(본 enum) — 어느 기능 카테고리에서 작성됐는지의 <b>분류 코드</b>.
 *       예: {@link #AI_RECOMMEND}, {@link #WORLDCUP}</li>
 * </ul>
 * <p>두 컬럼은 의미적으로 redundant 하지만 엑셀 설계가 둘 다 정의했으므로 모두 보존한다.
 * 클라이언트는 일반적으로 카테고리만 알고 있으면 충분하며, {@code review_source}는 가능할 때만 채워 보낸다.</p>
 */
public enum ReviewCategoryCode {

    /** 1.극장영수증인증 — 오프라인 극장 관람 영수증을 OCR 인증하면서 작성한 리뷰 */
    THEATER_RECEIPT,

    /** 2.도장깨기 — 도장깨기(course) 진행 중 단계별 인증 리뷰 */
    COURSE,

    /** 3.월드컵 — 이상형 월드컵 결과 화면에서 우승작에 대해 작성한 리뷰 */
    WORLDCUP,

    /** 4.위시리스트 — 위시리스트(wishlist)에 담아둔 영화에 대해 작성한 리뷰 */
    WISHLIST,

    /** 5. ai 추천 — AI 챗봇 추천 결과(영화 상세/추천 카드)에서 작성한 리뷰 */
    AI_RECOMMEND,

    /** 6.플레이리스트 — 플레이리스트(playlist)에 담긴 영화에 대해 작성한 리뷰 */
    PLAYLIST;
}
