package com.monglepick.monglepickbackend.domain.movie.service;

import com.monglepick.monglepickbackend.domain.movie.entity.FavActor;
import com.monglepick.monglepickbackend.domain.movie.entity.FavDirector;
import com.monglepick.monglepickbackend.domain.movie.repository.FavActorRepository;
import com.monglepick.monglepickbackend.domain.movie.repository.FavDirectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 온보딩 서비스 — 선호 감독/배우 저장 및 조회.
 *
 * <p>사용자가 온보딩(이상형 월드컵 이후) 또는 프로필 설정에서
 * 선호하는 감독과 배우를 등록/수정/조회할 수 있도록 비즈니스 로직을 처리한다.</p>
 *
 * <h3>저장 전략 (Replace All)</h3>
 * <p>저장 요청 시 해당 사용자의 기존 레코드를 전부 삭제한 뒤 새 목록을 일괄 INSERT한다.
 * 이는 부분 diff 연산을 피하고 구현을 단순하게 유지하기 위한 설계 결정이다.
 * 온보딩 데이터의 특성상 수정 빈도가 낮아 성능 영향이 미미하다.</p>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code readOnly=true} — 조회 메서드 기본값</li>
 *   <li>저장 메서드: {@code @Transactional} 오버라이드 — 삭제+INSERT 원자성 보장</li>
 * </ul>
 *
 * @see FavDirectorRepository
 * @see FavActorRepository
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private final FavDirectorRepository favDirectorRepository;
    private final FavActorRepository favActorRepository;

    /**
     * 사용자의 선호 감독 목록을 일괄 저장한다 (Replace All).
     *
     * <p>기존에 저장된 해당 사용자의 모든 선호 감독 레코드를 삭제하고,
     * 요청된 감독 이름 목록을 새로 INSERT한다.
     * 빈 리스트를 전달하면 기존 데이터가 모두 삭제된다.</p>
     *
     * @param userId        사용자 ID (JWT Principal에서 추출)
     * @param directorNames 저장할 감독 이름 목록 (중복 제거는 DB UNIQUE 제약으로 보장)
     */
    @Transactional
    public void saveDirectors(String userId, List<String> directorNames) {
        /* 기존 선호 감독 전체 삭제 (Replace All 전략) */
        favDirectorRepository.deleteByUserId(userId);
        log.debug("선호 감독 기존 데이터 삭제 완료 - userId: {}", userId);

        /* 새 목록 일괄 저장 */
        List<FavDirector> entities = directorNames.stream()
                .filter(name -> name != null && !name.isBlank())  /* null/공백 이름 방어 처리 */
                .map(name -> FavDirector.builder()
                        .userId(userId)
                        .directorName(name.trim())
                        .build())
                .toList();

        favDirectorRepository.saveAll(entities);
        log.info("선호 감독 저장 완료 - userId: {}, 저장 건수: {}", userId, entities.size());
    }

    /**
     * 사용자의 선호 배우 목록을 일괄 저장한다 (Replace All).
     *
     * <p>기존에 저장된 해당 사용자의 모든 선호 배우 레코드를 삭제하고,
     * 요청된 배우 이름 목록을 새로 INSERT한다.
     * 빈 리스트를 전달하면 기존 데이터가 모두 삭제된다.</p>
     *
     * @param userId     사용자 ID (JWT Principal에서 추출)
     * @param actorNames 저장할 배우 이름 목록 (중복 제거는 DB UNIQUE 제약으로 보장)
     */
    @Transactional
    public void saveActors(String userId, List<String> actorNames) {
        /* 기존 선호 배우 전체 삭제 (Replace All 전략) */
        favActorRepository.deleteByUserId(userId);
        log.debug("선호 배우 기존 데이터 삭제 완료 - userId: {}", userId);

        /* 새 목록 일괄 저장 */
        List<FavActor> entities = actorNames.stream()
                .filter(name -> name != null && !name.isBlank())  /* null/공백 이름 방어 처리 */
                .map(name -> FavActor.builder()
                        .userId(userId)
                        .actorName(name.trim())
                        .build())
                .toList();

        favActorRepository.saveAll(entities);
        log.info("선호 배우 저장 완료 - userId: {}, 저장 건수: {}", userId, entities.size());
    }

    /**
     * 사용자의 선호 감독 이름 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 선호 감독 이름 문자열 목록 (등록된 항목 없으면 빈 리스트)
     */
    public List<String> getDirectors(String userId) {
        return favDirectorRepository.findByUserId(userId).stream()
                .map(FavDirector::getDirectorName)
                .toList();
    }

    /**
     * 사용자의 선호 배우 이름 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 선호 배우 이름 문자열 목록 (등록된 항목 없으면 빈 리스트)
     */
    public List<String> getActors(String userId) {
        return favActorRepository.findByUserId(userId).stream()
                .map(FavActor::getActorName)
                .toList();
    }
}
