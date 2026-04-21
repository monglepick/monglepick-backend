package com.monglepick.monglepickbackend.domain.movie.config;

import com.monglepick.monglepickbackend.domain.movie.entity.GenreMaster;
import com.monglepick.monglepickbackend.domain.movie.repository.GenreMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * genre_master 초기 데이터 적재기.
 *
 * <p>JPA {@code ddl-auto=update} 로 테이블이 자동 생성된 뒤, 최초 기동 환경에서만
 * 기본 장르 마스터를 멱등하게 적재한다. 이미 한 건이라도 존재하면 운영자가 관리 중인
 * 데이터로 간주하고 전체 시드를 건너뛴다.</p>
 */
@Slf4j
@Component
@Order(120)
@RequiredArgsConstructor
public class GenreMasterInitializer implements ApplicationRunner {

    private final GenreMasterRepository genreMasterRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = genreMasterRepository.count();
        if (existing > 0) {
            log.debug("[GenreMasterInitializer] genre_master 기존 데이터 {}건 존재 — 시드 스킵", existing);
            return;
        }

        List<GenreMaster> seeds = buildSeeds().stream()
                .map(seed -> GenreMaster.builder()
                        .genreCode(seed.code())
                        .genreName(seed.name())
                        .contentsCount(0)
                        .build())
                .toList();

        genreMasterRepository.saveAllAndFlush(seeds);
        int updatedRows = genreMasterRepository.rebuildContentsCountFromMovies();

        log.info("[GenreMasterInitializer] genre_master 시드 {}건 적재, contents_count {}건 재계산 완료",
                seeds.size(), updatedRows);
    }

    private List<GenreSeed> buildSeeds() {
        return List.of(
                new GenreSeed("DRAMA", "드라마"),
                new GenreSeed("DOCUMENTARY", "다큐멘터리"),
                new GenreSeed("COMEDY", "코미디"),
                new GenreSeed("ANIMATION", "애니메이션"),
                new GenreSeed("ROMANCE", "로맨스"),
                new GenreSeed("HORROR", "공포"),
                new GenreSeed("MUSIC", "음악"),
                new GenreSeed("THRILLER", "스릴러"),
                new GenreSeed("ACTION", "액션"),
                new GenreSeed("CRIME", "범죄"),
                new GenreSeed("FAMILY", "가족"),
                new GenreSeed("TV_MOVIE", "TV 영화"),
                new GenreSeed("FANTASY", "판타지"),
                new GenreSeed("ADVENTURE", "어드벤처"),
                new GenreSeed("SF", "SF"),
                new GenreSeed("MYSTERY", "미스터리"),
                new GenreSeed("HISTORY", "역사"),
                new GenreSeed("WAR", "전쟁"),
                new GenreSeed("EROTIC", "에로"),
                new GenreSeed("WESTERN", "서부극"),
                new GenreSeed("BIOGRAPHY", "인물"),
                new GenreSeed("COMEDY_OLD", "코메디"),
                new GenreSeed("SOCIAL", "사회"),
                new GenreSeed("CULTURE", "문화"),
                new GenreSeed("REGIONAL", "지역"),
                new GenreSeed("NATURE_ENVIRONMENT", "자연ㆍ환경"),
                new GenreSeed("MELODRAMA", "멜로드라마"),
                new GenreSeed("HUMAN_RIGHTS", "인권"),
                new GenreSeed("PERFORMANCE", "공연"),
                new GenreSeed("CHILDREN", "아동"),
                new GenreSeed("EDUCATION", "교육"),
                new GenreSeed("SPORTS", "스포츠"),
                new GenreSeed("SCIENCE", "과학"),
                new GenreSeed("PERIOD_DRAMA", "시대극/사극"),
                new GenreSeed("ORGANIZATION", "기업ㆍ기관ㆍ단체"),
                new GenreSeed("OMNIBUS", "옴니버스"),
                new GenreSeed("YOUTH", "청춘영화"),
                new GenreSeed("SOCIAL_DRAMA", "사회물(경향)"),
                new GenreSeed("NOIR", "느와르"),
                new GenreSeed("LGBTQ", "동성애"),
                new GenreSeed("ART", "예술"),
                new GenreSeed("SERIES", "다부작"),
                new GenreSeed("RELIGION", "종교"),
                new GenreSeed("ROAD_MOVIE", "로드무비"),
                new GenreSeed("HIGH_TEEN", "하이틴(고교)"),
                new GenreSeed("DISASTER", "재난"),
                new GenreSeed("BIOPIC", "전기"),
                new GenreSeed("TV_DRAMA", "TV드라마"),
                new GenreSeed("ARCHIVAL", "기록"),
                new GenreSeed("ENLIGHTENMENT", "계몽"),
                new GenreSeed("ANTI_COMMUNISM_DIVISION", "반공/분단"),
                new GenreSeed("SPY", "첩보"),
                new GenreSeed("GANGSTER", "갱스터"),
                new GenreSeed("FOREIGN", "Foreign"),
                new GenreSeed("MILITARY", "군사"),
                new GenreSeed("LITERARY", "문예"),
                new GenreSeed("SWASHBUCKLER", "활극"),
                new GenreSeed("SENTIMENTAL", "신파"),
                new GenreSeed("OPERA", "오페라"),
                new GenreSeed("ADAPTATION", "합작(번안물)")
        );
    }

    private record GenreSeed(String code, String name) {
    }
}
