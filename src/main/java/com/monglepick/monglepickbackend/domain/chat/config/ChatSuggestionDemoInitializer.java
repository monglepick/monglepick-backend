package com.monglepick.monglepickbackend.domain.chat.config;

import com.monglepick.monglepickbackend.domain.chat.entity.ChatSuggestion;
import com.monglepick.monglepickbackend.domain.chat.repository.ChatSuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 채팅 추천 칩 데모 시드 초기화 컴포넌트.
 *
 * <p>앱 기동 시 chat_suggestions 테이블에 데이터가 0건일 때만
 * 기본 추천 칩 10개를 INSERT 한다.
 * 운영자가 관리자 페이지에서 이미 등록했다면 전체 스킵한다(멱등성 보장).</p>
 *
 * <h3>시드 데이터 (10개)</h3>
 * <ol>
 *   <li>오늘 기분이 우울한데 영화 추천해줘 (mood, order=10)</li>
 *   <li>인터스텔라 같은 영화 보고 싶어 (similar, order=20)</li>
 *   <li>가족이랑 볼 애니메이션 추천해줘 (family, order=30)</li>
 *   <li>요즘 인기 있는 한국 영화 뭐 있어? (trending, order=40)</li>
 *   <li>혼자 보기 좋은 영화 추천해줘 (mood, order=50)</li>
 *   <li>스트레스 풀 수 있는 영화 알려줘 (mood, order=60)</li>
 *   <li>기생충 같은 사회 비판 영화 있어? (similar, order=70)</li>
 *   <li>주말에 가볍게 볼 코미디 추천해줘 (genre, order=80)</li>
 *   <li>올해 SF 신작 뭐 봐야 해? (trending, order=90)</li>
 *   <li>인생 영화 한 편만 추천해줘 (personal, order=100)</li>
 * </ol>
 *
 * <h3>실행 순서</h3>
 * <p>{@code @Order(220)} — NoticeDemoInitializer(Order=210) 이후 실행한다.</p>
 */
@Slf4j
@Component
@Order(220)
@RequiredArgsConstructor
public class ChatSuggestionDemoInitializer implements ApplicationRunner {

    private final ChatSuggestionRepository chatSuggestionRepository;

    /**
     * 앱 기동 후 추천 칩이 0건이면 데모 시드를 INSERT 한다.
     *
     * <p>트랜잭션 내에서 일괄 저장되므로 중간 실패 시 전체 롤백된다.
     * 이미 1건 이상 존재하면 멱등 스킵한다.</p>
     *
     * @param args 애플리케이션 시작 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = chatSuggestionRepository.count();
        if (existing > 0) {
            log.debug("[ChatSuggestionDemoInitializer] 추천 칩 {}건 존재 — 데모 시드 스킵", existing);
            return;
        }

        // 시드 전체 is_active=true, start_at=null(즉시), end_at=null(무기한)
        List<ChatSuggestion> seeds = List.of(
                ChatSuggestion.builder()
                        .text("오늘 기분이 우울한데 영화 추천해줘")
                        .category("mood")
                        .isActive(true)
                        .displayOrder(10)
                        .build(),
                ChatSuggestion.builder()
                        .text("인터스텔라 같은 영화 보고 싶어")
                        .category("similar")
                        .isActive(true)
                        .displayOrder(20)
                        .build(),
                ChatSuggestion.builder()
                        .text("가족이랑 볼 애니메이션 추천해줘")
                        .category("family")
                        .isActive(true)
                        .displayOrder(30)
                        .build(),
                ChatSuggestion.builder()
                        .text("요즘 인기 있는 한국 영화 뭐 있어?")
                        .category("trending")
                        .isActive(true)
                        .displayOrder(40)
                        .build(),
                ChatSuggestion.builder()
                        .text("혼자 보기 좋은 영화 추천해줘")
                        .category("mood")
                        .isActive(true)
                        .displayOrder(50)
                        .build(),
                ChatSuggestion.builder()
                        .text("스트레스 풀 수 있는 영화 알려줘")
                        .category("mood")
                        .isActive(true)
                        .displayOrder(60)
                        .build(),
                ChatSuggestion.builder()
                        .text("기생충 같은 사회 비판 영화 있어?")
                        .category("similar")
                        .isActive(true)
                        .displayOrder(70)
                        .build(),
                ChatSuggestion.builder()
                        .text("주말에 가볍게 볼 코미디 추천해줘")
                        .category("genre")
                        .isActive(true)
                        .displayOrder(80)
                        .build(),
                ChatSuggestion.builder()
                        .text("올해 SF 신작 뭐 봐야 해?")
                        .category("trending")
                        .isActive(true)
                        .displayOrder(90)
                        .build(),
                ChatSuggestion.builder()
                        .text("인생 영화 한 편만 추천해줘")
                        .category("personal")
                        .isActive(true)
                        .displayOrder(100)
                        .build()
        );

        chatSuggestionRepository.saveAll(seeds);
        log.info("[ChatSuggestionDemoInitializer] 채팅 추천 칩 데모 시드 {}건 완료", seeds.size());
    }
}
