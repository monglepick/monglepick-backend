package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.support.entity.SupportProfanity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 관리자 전용 비속어 사전 리포지토리.
 *
 * <p>관리자 페이지 "고객센터 → 비속어 사전" 탭의 CRUD 및 CSV 임포트/익스포트 지원 쿼리를 제공한다.</p>
 */
public interface AdminProfanityRepository extends JpaRepository<SupportProfanity, Long> {

    /**
     * 전체 비속어를 가나다순(word 오름차순)으로 페이징 조회한다.
     *
     * @param pageable 페이지 정보
     * @return 비속어 페이지
     */
    Page<SupportProfanity> findAllByOrderByWordAsc(Pageable pageable);

    /**
     * 전체 비속어를 리스트로 조회한다 (CSV 익스포트용).
     *
     * <p>대량 조회이므로 호출자는 트랜잭션 크기에 주의해야 한다.
     * 보통 단어 수는 수백~수천 건 규모이므로 메모리 부담은 제한적이다.</p>
     *
     * @return 전체 비속어 리스트 (가나다순)
     */
    List<SupportProfanity> findAllByOrderByWordAsc();

    /**
     * 특정 단어가 이미 등록되어 있는지 조회한다 (중복 등록 방지).
     *
     * @param word 검사할 단어
     * @return 존재하면 해당 엔티티, 없으면 empty
     */
    Optional<SupportProfanity> findByWord(String word);
}
