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
 * <p>앱 기동 시 chat_suggestions 테이블에 surface 별 시드 존재 여부를 확인해
 * 비어 있는 채널에만 기본 시드를 INSERT 한다.
 * 운영자가 관리자 페이지에서 이미 등록한 채널은 스킵한다(멱등성 보장, 2026-04-23 변경).</p>
 *
 * <h3>surface 별 시드 구성</h3>
 * <ul>
 *   <li>{@code user_chat} — 유저 채팅 환영 화면 10개 (기존)</li>
 *   <li>{@code admin_assistant} — 관리자 AI 어시스턴트 빈 상태 빠른 질문 5개 (신규 2026-04-23)</li>
 *   <li>{@code faq_chatbot} — 고객센터 FAQ 챗봇 위젯 4개 (신규 2026-04-23)</li>
 * </ul>
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
     * 앱 기동 후 surface 별로 시드 존재 여부를 확인해 비어 있는 채널에 INSERT 한다.
     *
     * <p>트랜잭션 내에서 일괄 저장되므로 중간 실패 시 전체 롤백된다.
     * 특정 surface 에 1건 이상 있으면 해당 surface 만 스킵하고 나머지 surface 는 시드한다.
     * 이로써 기존 user_chat 만 있는 DB 에도 admin_assistant/faq_chatbot 가 자동 추가된다
     * (2026-04-23 surface 컬럼 도입 후).</p>
     *
     * @param args 애플리케이션 시작 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 0) 레거시 복구 — surface 컬럼이 방금 추가됐거나 DEFAULT 가 제대로 적용 안 된 경우
        //    기존 레코드의 surface 가 NULL/빈 문자열로 남아 있을 수 있다. 이를 user_chat
        //    으로 일괄 복구해야 countBySurface('user_chat') 가 정확히 동작하고,
        //    3채널 분리 조회(findActiveBySurfaceAt)가 의도대로 빈 결과를 반환한다.
        int normalized = chatSuggestionRepository.normalizeLegacySurface();
        if (normalized > 0) {
            log.info(
                    "[ChatSuggestionDemoInitializer] surface 레거시 복구 — {}건을 'user_chat' 으로 정규화",
                    normalized
            );
        }

        int inserted = 0;
        inserted += seedSurfaceIfEmpty("user_chat", userChatSeeds());
        inserted += seedSurfaceIfEmpty("admin_assistant", adminAssistantSeeds());
        inserted += seedSurfaceIfEmpty("faq_chatbot", faqChatbotSeeds());
        if (inserted > 0) {
            log.info("[ChatSuggestionDemoInitializer] 채팅 추천 칩 데모 시드 {}건 완료", inserted);
        } else {
            log.debug("[ChatSuggestionDemoInitializer] 모든 surface 에 데이터 존재 — 전체 스킵");
        }
    }

    /**
     * 특정 surface 에 레코드가 0건이면 seeds 를 INSERT 하고 삽입 건수를 반환한다.
     * 이미 존재하면 0 반환 + 스킵 로그.
     */
    private int seedSurfaceIfEmpty(String surface, List<ChatSuggestion> seeds) {
        long existing = chatSuggestionRepository.countBySurface(surface);
        if (existing > 0) {
            log.debug(
                    "[ChatSuggestionDemoInitializer] surface={} 이미 {}건 존재 — 스킵",
                    surface, existing
            );
            return 0;
        }
        chatSuggestionRepository.saveAll(seeds);
        log.info(
                "[ChatSuggestionDemoInitializer] surface={} 시드 {}건 삽입 완료",
                surface, seeds.size()
        );
        return seeds.size();
    }

    // ─────────────────────────────────────────────
    // surface 별 시드 팩토리
    // ─────────────────────────────────────────────

    private List<ChatSuggestion> userChatSeeds() {
        return List.of(
                build("user_chat", "오늘 기분이 우울한데 영화 추천해줘", "mood", 10),
                build("user_chat", "인터스텔라 같은 영화 보고 싶어", "similar", 20),
                build("user_chat", "가족이랑 볼 애니메이션 추천해줘", "family", 30),
                build("user_chat", "요즘 인기 있는 한국 영화 뭐 있어?", "trending", 40),
                build("user_chat", "혼자 보기 좋은 영화 추천해줘", "mood", 50),
                build("user_chat", "스트레스 풀 수 있는 영화 알려줘", "mood", 60),
                build("user_chat", "기생충 같은 사회 비판 영화 있어?", "similar", 70),
                build("user_chat", "주말에 가볍게 볼 코미디 추천해줘", "genre", 80),
                build("user_chat", "올해 SF 신작 뭐 봐야 해?", "trending", 90),
                build("user_chat", "인생 영화 한 편만 추천해줘", "personal", 100)
        );
    }

    /** 관리자 AI 어시스턴트 빈 상태 빠른 질문 (2026-04-23 신규). */
    private List<ChatSuggestion> adminAssistantSeeds() {
        return List.of(
                build("admin_assistant", "지난 7일 DAU 추이 보여줘", "stats", 10),
                build("admin_assistant", "이번 달 환불된 결제 주문 목록", "query", 20),
                build("admin_assistant", "대기 중인 고객센터 티켓 몇 건이야?", "query", 30),
                build("admin_assistant", "AI 추천 서비스 현황 알려줘", "stats", 40),
                build("admin_assistant", "커뮤니티 최근 신고 건수 요약해줘", "stats", 50)
        );
    }

    /** 고객센터 FAQ 챗봇 위젯 빠른 질문 (2026-04-23 신규). */
    private List<ChatSuggestion> faqChatbotSeeds() {
        return List.of(
                build("faq_chatbot", "포인트는 어떻게 충전하나요?", "faq", 10),
                build("faq_chatbot", "AI 추천은 어떻게 사용하나요?", "faq", 20),
                build("faq_chatbot", "비밀번호를 변경하고 싶어요", "faq", 30),
                build("faq_chatbot", "환불은 어떻게 하나요?", "faq", 40)
        );
    }

    /**
     * 시드 1건 빌드 — 공통 속성(is_active=true, start/end=null, 즉시·무기한)을 자동 설정.
     */
    private ChatSuggestion build(String surface, String text, String category, int order) {
        return ChatSuggestion.builder()
                .surface(surface)
                .text(text)
                .category(category)
                .isActive(true)
                .displayOrder(order)
                .build();
    }
}
