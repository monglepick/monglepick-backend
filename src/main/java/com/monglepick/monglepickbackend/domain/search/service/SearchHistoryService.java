package com.monglepick.monglepickbackend.domain.search.service;

import com.monglepick.monglepickbackend.domain.search.entity.SearchHistory;
import com.monglepick.monglepickbackend.domain.search.repository.SearchHistoryRepository;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 검색 이력 서비스 — 사용자 검색 키워드 이력 비즈니스 로직.
 *
 * <p>사용자가 검색을 수행할 때마다 키워드를 저장하고,
 * 최근 검색어 목록을 제공하며, 검색어 삭제를 지원한다.
 * 동일 사용자가 같은 키워드를 재검색하면 searchedAt만 갱신한다 (UPSERT 방식).</p>
 *
 * <h3>UPSERT 처리 흐름 (saveSearchKeyword)</h3>
 * <ol>
 *   <li>User 조회 — userId로 users 테이블 조회</li>
 *   <li>기존 이력 확인 — (userId, keyword) 조합으로 조회</li>
 *   <li>존재하면 updateSearchedAt(), 없으면 새 SearchHistory 생성 후 save()</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 클래스 레벨: 읽기 전용 (쓰기 메서드는 개별 @Transactional 오버라이드)
public class SearchHistoryService {

    /** 검색 이력 JPA 리포지토리 */
    private final SearchHistoryRepository searchHistoryRepository;

    /** 사용자 JPA 리포지토리 (사용자 존재 여부 확인 및 FK 연결용) */
    private final UserRepository userRepository;

    /**
     * 검색 키워드를 저장한다 (UPSERT).
     *
     * <p>동일 사용자가 같은 키워드를 이미 검색한 이력이 있으면
     * 새 레코드를 추가하는 대신 searchedAt을 현재 시각으로 갱신한다.
     * 처음 검색하는 키워드는 새 이력 레코드를 생성한다.</p>
     *
     * <p>빈 문자열 또는 공백만인 키워드는 저장하지 않는다.</p>
     *
     * @param userId  검색을 수행한 사용자 ID
     * @param keyword 검색 키워드
     * @throws BusinessException USER_NOT_FOUND — userId에 해당하는 사용자가 없을 때
     */
    @Transactional
    public void saveSearchKeyword(String userId, String keyword) {
        // 빈 키워드는 저장하지 않음 (불필요한 레코드 생성 방지)
        if (keyword == null || keyword.isBlank()) {
            log.debug("빈 키워드는 검색 이력에 저장하지 않음: userId={}", userId);
            return;
        }

        // 1. 사용자 조회 — UserRepository PK가 String(userId)이므로 findById 사용
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 기존 이력 확인 후 UPSERT 분기
        searchHistoryRepository.findByUser_UserIdAndKeyword(userId, keyword)
                .ifPresentOrElse(
                        existing -> {
                            // 2-a. 이미 존재하는 키워드 — searchedAt만 갱신 (dirty checking으로 자동 UPDATE)
                            log.debug("기존 검색 이력 갱신: userId={}, keyword={}", userId, keyword);
                            existing.updateSearchedAt();
                        },
                        () -> {
                            // 2-b. 처음 검색하는 키워드 — 새 이력 생성
                            log.debug("신규 검색 이력 저장: userId={}, keyword={}", userId, keyword);
                            searchHistoryRepository.save(
                                    SearchHistory.builder()
                                            .user(user)
                                            .keyword(keyword)
                                            .searchedAt(LocalDateTime.now())
                                            .build()
                            );
                        }
                );
    }

    /**
     * 사용자의 최근 검색어 목록을 반환한다.
     *
     * <p>searchedAt 내림차순으로 최대 20개의 키워드를 반환한다.
     * 클라이언트 검색창의 "최근 검색어" 드롭다운에 표시하기 위해 사용된다.</p>
     *
     * @param userId 검색 이력을 조회할 사용자 ID
     * @return 최근 검색 키워드 목록 (최대 20개, 최신순)
     */
    public List<String> getRecentSearches(String userId) {
        return searchHistoryRepository
                .findTop20ByUser_UserIdOrderBySearchedAtDesc(userId)
                .stream()
                .map(SearchHistory::getKeyword)
                .toList();
    }

    /**
     * 특정 검색 이력을 삭제한다.
     *
     * <p>본인의 검색 이력만 삭제할 수 있다.
     * 다른 사용자의 이력 삭제 시도 또는 존재하지 않는 이력 삭제 시도는 무시된다
     * (클라이언트 UX 상 이미 삭제된 항목을 다시 삭제해도 오류 없이 처리).</p>
     *
     * @param userId          삭제를 요청한 사용자 ID
     * @param searchHistoryId 삭제할 검색 이력 ID
     */
    @Transactional
    public void deleteSearchHistory(String userId, Long searchHistoryId) {
        // 본인 소유 여부 확인 후 삭제 (존재하지 않거나 타인 소유면 조용히 무시)
        searchHistoryRepository.findById(searchHistoryId)
                .filter(history -> history.getUser().getUserId().equals(userId))
                .ifPresentOrElse(
                        history -> {
                            log.info("검색 이력 삭제: userId={}, searchHistoryId={}, keyword={}",
                                    userId, searchHistoryId, history.getKeyword());
                            searchHistoryRepository.delete(history);
                        },
                        () -> log.debug("삭제 대상 검색 이력 없음 (이미 삭제됐거나 타인 소유): " +
                                "userId={}, searchHistoryId={}", userId, searchHistoryId)
                );
    }
}
