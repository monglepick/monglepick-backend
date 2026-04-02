package com.monglepick.monglepickbackend.domain.movie.service;

import com.monglepick.monglepickbackend.domain.movie.dto.LikeResponse;
import com.monglepick.monglepickbackend.domain.movie.entity.Like;
import com.monglepick.monglepickbackend.domain.movie.repository.LikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 영화 좋아요 서비스.
 *
 * <p>영화 좋아요 토글(등록/취소/복구), 좋아요 상태 조회, 좋아요 수 조회 비즈니스 로직을 처리한다.</p>
 *
 * <h3>소프트 삭제 정책</h3>
 * <p>좋아요 취소 시 레코드를 물리적으로 삭제하지 않고 {@code deleted_at}에 현재 시각을 기록한다.
 * 이를 통해 이력 조회가 가능하고, UNIQUE(user_id, movie_id) 제약을 유지하면서
 * 재활성화(restore) 시 INSERT 없이 UPDATE만으로 처리할 수 있다.</p>
 *
 * <h3>토글 로직 3분기</h3>
 * <ol>
 *   <li>레코드 미존재 → 신규 Like 엔티티 INSERT, liked=true 반환</li>
 *   <li>레코드 존재 + deleted_at IS NULL (활성) → softDelete() 호출, liked=false 반환</li>
 *   <li>레코드 존재 + deleted_at IS NOT NULL (취소됨) → restore() 호출, liked=true 반환</li>
 * </ol>
 *
 * @see LikeRepository
 * @see Like
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 클래스 레벨: 읽기 전용 기본값
public class LikeService {

    private final LikeRepository likeRepository;

    /**
     * 영화 좋아요를 토글한다 (등록 / 취소 / 복구).
     *
     * <p>동일 사용자-영화 쌍에 대해 UNIQUE 제약이 걸려 있으므로,
     * 기존 레코드를 재사용하는 방식으로 INSERT를 최소화한다.</p>
     *
     * <h3>처리 흐름</h3>
     * <pre>
     * findByUserIdAndMovieId 조회
     *  ├─ Optional.empty()           → 신규 INSERT, liked=true
     *  ├─ deletedAt == null (활성)    → softDelete(), liked=false
     *  └─ deletedAt != null (취소됨) → restore(),    liked=true
     * </pre>
     *
     * @param userId  요청 사용자 ID (JWT Principal 또는 Service Key body)
     * @param movieId 대상 영화 ID
     * @return 변경 후 좋아요 상태 + 전체 좋아요 수
     */
    @Transactional  // 쓰기 전용 오버라이드
    public LikeResponse toggleLike(String userId, String movieId) {
        Optional<Like> existing = likeRepository.findByUserIdAndMovieId(userId, movieId);
        boolean liked;

        if (existing.isEmpty()) {
            // 케이스 1: 좋아요 이력 없음 → 신규 등록
            Like newLike = Like.builder()
                    .userId(userId)
                    .movieId(movieId)
                    .build();
            likeRepository.save(newLike);
            liked = true;
            log.debug("좋아요 신규 등록 - userId: {}, movieId: {}", userId, movieId);

        } else {
            Like like = existing.get();

            if (like.getDeletedAt() == null) {
                // 케이스 2: 활성 좋아요 → 소프트 삭제(취소)
                like.softDelete();
                liked = false;
                log.debug("좋아요 취소 - userId: {}, movieId: {}", userId, movieId);
            } else {
                // 케이스 3: 취소된 좋아요 → 복구(재활성화)
                like.restore();
                liked = true;
                log.debug("좋아요 복구 - userId: {}, movieId: {}", userId, movieId);
            }
        }

        // 변경 완료 후 최신 카운트 조회
        long likeCount = likeRepository.countByMovieIdAndDeletedAtIsNull(movieId);
        return LikeResponse.of(liked, likeCount);
    }

    /**
     * 현재 사용자의 해당 영화 활성 좋아요 여부를 반환한다.
     *
     * <p>deleted_at IS NULL 조건으로 취소된 좋아요를 제외한다.</p>
     *
     * @param userId  요청 사용자 ID
     * @param movieId 대상 영화 ID
     * @return 활성 좋아요가 존재하면 {@code true}, 없으면 {@code false}
     */
    public boolean isLiked(String userId, String movieId) {
        return likeRepository.existsByUserIdAndMovieIdAndDeletedAtIsNull(userId, movieId);
    }

    /**
     * 특정 영화의 전체 활성 좋아요 수를 반환한다.
     *
     * <p>비로그인 사용자도 접근 가능한 공개 API에서 호출된다.
     * deleted_at IS NULL 조건으로 취소된 좋아요를 집계에서 제외한다.</p>
     *
     * @param movieId 대상 영화 ID
     * @return 활성 좋아요 수 (0 이상)
     */
    public long getLikeCount(String movieId) {
        return likeRepository.countByMovieIdAndDeletedAtIsNull(movieId);
    }
}
