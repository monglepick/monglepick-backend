package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminGenreDto.CreateGenreRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminGenreDto.GenreResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminGenreDto.UpdateGenreRequest;
import com.monglepick.monglepickbackend.domain.movie.entity.GenreMaster;
import com.monglepick.monglepickbackend.domain.movie.repository.GenreMasterRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 장르 마스터(GenreMaster) 관리 서비스.
 *
 * <p>장르 마스터 데이터의 등록·수정·삭제 비즈니스 로직을 담당한다.
 * 추천 엔진/필터/온보딩 장르 선택 등에서 사용되는 마스터 데이터를 관리한다.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>장르 목록 조회 (페이징 또는 전체)</li>
 *   <li>장르 단건 조회</li>
 *   <li>장르 신규 등록 (genre_code UNIQUE 사전 검증)</li>
 *   <li>장르 한국어명 수정 (genre_code 변경 불가)</li>
 *   <li>장르 hard delete (contents_count > 0이면 경고 로그)</li>
 * </ol>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>{@code genre_code}는 시스템 식별자이며 변경 불가</li>
 *   <li>{@code contents_count}는 비정규화 카운터로 영화 등록/삭제 시 자동 동기화 — 직접 수정 불가</li>
 *   <li>장르 삭제는 hard delete. 해당 장르를 참조하는 영화의 genres JSON 컬럼은
 *       자동 정리되지 않으므로 운영상 신중하게 사용</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminGenreService {

    /** 장르 마스터 레포지토리 (JpaRepository&lt;GenreMaster, Long&gt;) */
    private final GenreMasterRepository genreMasterRepository;

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 장르 목록 페이징 조회.
     *
     * @param pageable 페이지 정보
     * @return 페이징된 장르 응답
     */
    public Page<GenreResponse> getGenres(Pageable pageable) {
        return genreMasterRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * 전체 장르 목록 조회 (페이징 없음 — 드롭다운/필터용).
     *
     * @return 장르 응답 리스트
     */
    public List<GenreResponse> getAllGenres() {
        return genreMasterRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * 장르 단건 조회.
     *
     * @param id genre_id
     * @return 장르 응답
     * @throws BusinessException 존재하지 않으면 GENRE_NOT_FOUND
     */
    public GenreResponse getGenre(Long id) {
        return toResponse(findGenreByIdOrThrow(id));
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    /**
     * 신규 장르 등록.
     *
     * <p>{@code genre_code} UNIQUE — 중복 시 409.</p>
     *
     * @param request 신규 등록 요청
     * @return 생성된 장르 응답
     */
    @Transactional
    public GenreResponse createGenre(CreateGenreRequest request) {
        if (genreMasterRepository.existsByGenreCode(request.genreCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_GENRE_CODE);
        }

        GenreMaster entity = GenreMaster.builder()
                .genreCode(request.genreCode())
                .genreName(request.genreName())
                .contentsCount(0)
                .build();

        GenreMaster saved = genreMasterRepository.save(entity);
        log.info("[관리자] 장르 마스터 등록 — id={}, code={}, name={}",
                saved.getGenreId(), saved.getGenreCode(), saved.getGenreName());

        return toResponse(saved);
    }

    /**
     * 장르 한국어명 수정 (genre_code 제외).
     *
     * @param id      대상 장르 ID
     * @param request 수정 요청
     * @return 수정된 장르 응답
     */
    @Transactional
    public GenreResponse updateGenre(Long id, UpdateGenreRequest request) {
        GenreMaster entity = findGenreByIdOrThrow(id);
        entity.updateName(request.genreName());

        log.info("[관리자] 장르 마스터 수정 — id={}, code={}, name={}",
                id, entity.getGenreCode(), entity.getGenreName());
        return toResponse(entity);
    }

    /**
     * 장르 hard delete.
     *
     * <p>contents_count &gt; 0인 경우 경고 로그만 남기고 삭제는 진행한다.
     * 해당 장르를 참조하는 영화의 genres JSON 컬럼은 자동 정리되지 않는다.</p>
     *
     * @param id 삭제 대상 장르 ID
     */
    @Transactional
    public void deleteGenre(Long id) {
        GenreMaster entity = findGenreByIdOrThrow(id);

        if (entity.getContentsCount() != null && entity.getContentsCount() > 0) {
            log.warn("[관리자] 영화가 연결된 장르 hard delete — id={}, code={}, contentsCount={}",
                    id, entity.getGenreCode(), entity.getContentsCount());
        }

        genreMasterRepository.delete(entity);
        log.info("[관리자] 장르 마스터 삭제 — id={}, code={}", id, entity.getGenreCode());
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    /** ID로 장르 조회 또는 404 */
    private GenreMaster findGenreByIdOrThrow(Long id) {
        return genreMasterRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.GENRE_NOT_FOUND,
                        "장르 ID " + id + "를 찾을 수 없습니다"));
    }

    /** 엔티티 → 응답 DTO */
    private GenreResponse toResponse(GenreMaster entity) {
        return new GenreResponse(
                entity.getGenreId(),
                entity.getGenreCode(),
                entity.getGenreName(),
                entity.getContentsCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
