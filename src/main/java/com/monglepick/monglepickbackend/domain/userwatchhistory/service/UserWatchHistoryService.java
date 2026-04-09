package com.monglepick.monglepickbackend.domain.userwatchhistory.service;

import com.monglepick.monglepickbackend.domain.userwatchhistory.dto.UserWatchHistoryRequest;
import com.monglepick.monglepickbackend.domain.userwatchhistory.dto.UserWatchHistoryResponse;
import com.monglepick.monglepickbackend.domain.userwatchhistory.entity.UserWatchHistory;
import com.monglepick.monglepickbackend.domain.userwatchhistory.repository.UserWatchHistoryRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 실 유저 시청 이력 도메인 서비스.
 *
 * <p>시청 기록의 추가·조회·삭제·재관람 카운트를 담당한다.
 * 본 서비스는 {@code /api/v1/watch-history} 독립 경로의 비즈니스 로직을 전담하며,
 * 마이페이지 통합 경로({@code /api/v1/users/me/watch-history})는 {@code UserService} 가
 * 본 리포지토리를 직접 호출해 처리한다.</p>
 *
 * <h3>설계 원칙</h3>
 * <ul>
 *   <li><b>중복 허용</b>: 같은 사용자가 같은 영화를 N 번 시청한 경우 N 건의 레코드가 저장된다.
 *       재관람 횟수 추적과 시청 패턴 분석을 위한 의도적 설계이다.</li>
 *   <li><b>소유권 검증</b>: 삭제 시 본인 소유 레코드인지 한 쿼리로 확인하여 race condition 을 방지한다.</li>
 *   <li><b>watchedAt null 처리</b>: 클라이언트가 명시하지 않으면 서버 수신 시각으로 자동 채운다.</li>
 *   <li><b>String userId</b>: Phase 1 원칙(설계서 §15.4)에 따라 User 엔티티 조회 없이 userId 를 그대로 사용한다.
 *       사용자 존재 검증은 JWT 인증 단계에서 이미 처리됨.</li>
 * </ul>
 *
 * <h3>트랜잭션 전략</h3>
 * <p>클래스 레벨 {@code @Transactional(readOnly = true)} 로 조회 성능을 최적화하고,
 * 쓰기 메서드(add/delete)는 {@code @Transactional} 로 오버라이드한다.</p>
 *
 * <h3>Kaggle 시드와의 분리</h3>
 * <p>본 서비스는 {@code user_watch_history} 테이블만 접근하며, Kaggle MovieLens 26M 시드인
 * {@code kaggle_watch_history} 와는 완전히 분리되어 있다. Kaggle 시드는 Agent CF 파이프라인
 * 전용 read-only 테이블이며 Backend 에서 R/W 하지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserWatchHistoryService {

    private final UserWatchHistoryRepository userWatchHistoryRepository;

    // ════════════════════════════════════════════════════════════════
    // 조회 (readOnly = true 클래스 레벨 적용)
    // ════════════════════════════════════════════════════════════════

    /**
     * 사용자의 시청 이력을 페이징 조회한다.
     *
     * <p>정렬은 호출 측 Pageable 에 위임한다. Controller 의 {@code @PageableDefault} 가
     * watchedAt DESC 를 기본값으로 지정하므로 일반적으로 최신순 결과가 반환된다.</p>
     *
     * @param userId   조회 대상 사용자 ID (JWT 에서 추출)
     * @param pageable 페이징/정렬 정보
     * @return 페이지 단위의 시청 이력 응답 DTO
     */
    public Page<UserWatchHistoryResponse> getWatchHistory(String userId, Pageable pageable) {
        log.debug("시청 이력 조회 - userId: {}, page: {}, size: {}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        return userWatchHistoryRepository.findByUserId(userId, pageable)
                .map(UserWatchHistoryResponse::from);
    }

    /**
     * 특정 사용자가 특정 영화를 몇 번 시청했는지 카운트한다.
     *
     * <p>마이페이지 또는 영화 상세 화면의 "이 영화를 N 번 봤어요" 표시에 사용한다.
     * 같은 사용자의 동일 영화 레코드를 모두 합산하므로 중복 허용 정책과 정합한다.</p>
     *
     * @param userId  조회 대상 사용자 ID
     * @param movieId 조회 대상 영화 ID
     * @return 시청 횟수 (시청 기록이 없으면 0)
     */
    public long getRewatchCount(String userId, String movieId) {
        return userWatchHistoryRepository.countByUserIdAndMovieId(userId, movieId);
    }

    // ════════════════════════════════════════════════════════════════
    // 쓰기 (readOnly 오버라이드)
    // ════════════════════════════════════════════════════════════════

    /**
     * 시청 기록을 추가한다.
     *
     * <p><b>중복 허용 정책</b>: 같은 영화를 여러 번 시청한 경우 매번 새 레코드로 저장된다.
     * UNIQUE 제약을 두지 않으며, 재관람 횟수와 시청 시각의 시계열 추적이 가능하다.</p>
     *
     * <p><b>watchedAt null 처리</b>: 클라이언트가 미입력 시 서버 수신 시각으로 자동 채운다.
     * 클라이언트가 시청 직후 즉시 기록하는 일반 케이스를 지원한다.</p>
     *
     * <p><b>사용자 존재 검증</b>: JWT 인증 단계에서 이미 처리되었으므로 별도의 users 조회를 생략한다.
     * (Phase 1 하이브리드 §15.4 — String userId 직접 사용)</p>
     *
     * @param userId  JWT 에서 추출한 사용자 ID
     * @param request 시청 기록 요청 DTO (movieId 필수, 5 필드 선택)
     * @return 저장된 시청 이력 응답 DTO
     */
    @Transactional
    public UserWatchHistoryResponse addWatchHistory(String userId, UserWatchHistoryRequest request) {
        // watchedAt 누락 시 서버 시각으로 채움
        LocalDateTime watchedAt = request.watchedAt() != null
                ? request.watchedAt()
                : LocalDateTime.now();

        UserWatchHistory entity = UserWatchHistory.builder()
                .userId(userId)
                .movieId(request.movieId())
                .watchedAt(watchedAt)
                .rating(request.rating())
                .watchSource(request.watchSource())
                .watchDurationSeconds(request.watchDurationSeconds())
                .completionStatus(request.completionStatus())
                .build();

        UserWatchHistory saved = userWatchHistoryRepository.save(entity);
        log.info("시청 기록 추가 - userId: {}, movieId: {}, id: {}",
                userId, request.movieId(), saved.getUserWatchHistoryId());

        return UserWatchHistoryResponse.from(saved);
    }

    /**
     * 특정 시청 기록을 삭제한다.
     *
     * <p><b>소유권 검증</b>: {@code findByUserWatchHistoryIdAndUserId} 한 쿼리로
     * "존재 + 본인 소유" 를 동시에 확인한다. 별도의 owner 체크 분기가 없어
     * race condition 가능성이 줄어든다.</p>
     *
     * <p>본인 소유가 아니거나 존재하지 않는 ID 는 동일하게 {@code G002 INVALID_INPUT}
     * 으로 응답하여 enumeration attack(타인의 ID 존재 여부 추론)을 방지한다.</p>
     *
     * @param userId             JWT 에서 추출한 사용자 ID
     * @param userWatchHistoryId 삭제할 시청 이력 PK
     * @throws BusinessException G002 INVALID_INPUT — 시청 기록이 없거나 본인 소유가 아닌 경우
     */
    @Transactional
    public void deleteWatchHistory(String userId, Long userWatchHistoryId) {
        UserWatchHistory entity = userWatchHistoryRepository
                .findByUserWatchHistoryIdAndUserId(userWatchHistoryId, userId)
                .orElseThrow(() -> {
                    log.warn("시청 이력 삭제 실패 - 없거나 소유권 불일치 - userId: {}, id: {}",
                            userId, userWatchHistoryId);
                    return new BusinessException(
                            ErrorCode.INVALID_INPUT, "해당 시청 기록을 찾을 수 없습니다");
                });

        userWatchHistoryRepository.delete(entity);
        log.info("시청 기록 삭제 - userId: {}, id: {}", userId, userWatchHistoryId);
    }
}
