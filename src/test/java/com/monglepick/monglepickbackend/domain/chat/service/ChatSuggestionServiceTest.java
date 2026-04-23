package com.monglepick.monglepickbackend.domain.chat.service;

import com.monglepick.monglepickbackend.domain.chat.dto.AdminChatSuggestionResponse;
import com.monglepick.monglepickbackend.domain.chat.dto.ChatSuggestionRequest;
import com.monglepick.monglepickbackend.domain.chat.dto.ChatSuggestionResponse;
import com.monglepick.monglepickbackend.domain.chat.entity.ChatSuggestion;
import com.monglepick.monglepickbackend.domain.chat.repository.ChatSuggestionRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatSuggestionService 단위 테스트.
 *
 * <p>Mockito 기반 @ExtendWith(MockitoExtension.class) 패턴.
 * Repository는 Mock 처리하여 DB 의존성 없이 서비스 로직만 검증한다.</p>
 *
 * <h3>테스트 그룹</h3>
 * <ul>
 *   <li>{@link GetActivePoolTest} — limit 클램프, 빈 풀, 셔플, 부족/충분한 풀</li>
 *   <li>{@link TrackClickTest} — 존재/미존재 칩, null id 처리</li>
 *   <li>{@link DomainMethodTest} — activate/deactivate, incrementClickCount, update</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ChatSuggestionServiceTest {

    @Mock
    private ChatSuggestionRepository chatSuggestionRepository;

    @InjectMocks
    private ChatSuggestionService chatSuggestionService;

    // ─────────────────────────────────────────────
    // 테스트 헬퍼
    // ─────────────────────────────────────────────

    /**
     * 테스트용 ChatSuggestion 엔티티 생성 헬퍼.
     *
     * @param id   suggestionId
     * @param text 추천 문구
     * @return 활성 상태의 ChatSuggestion
     */
    private ChatSuggestion buildSuggestion(Long id, String text) {
        return ChatSuggestion.builder()
                .text(text)
                .category("mood")
                .isActive(true)
                .displayOrder(0)
                .build();
    }

    /**
     * 지정된 수만큼 활성 칩 목록을 생성한다.
     *
     * @param count 생성할 칩 수
     * @return 칩 목록 (수정 가능한 ArrayList)
     */
    private List<ChatSuggestion> buildPool(int count) {
        List<ChatSuggestion> pool = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            pool.add(buildSuggestion((long) i, "영화 추천 " + i));
        }
        return pool;
    }

    // ─────────────────────────────────────────────
    // GetActivePool 테스트
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("getActivePool — 활성 칩 풀 조회")
    class GetActivePoolTest {

        @Test
        @DisplayName("limit=0 → 1로 클램프되어 칩 1개 반환")
        void limitZero_clampsToOne() {
            when(chatSuggestionRepository.findActiveBySurfaceAt(any(), any())).thenReturn(buildPool(5));

            List<ChatSuggestionResponse> result = chatSuggestionService.getActivePool(0);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("limit=-1 → 1로 클램프되어 칩 1개 반환")
        void limitNegative_clampsToOne() {
            when(chatSuggestionRepository.findActiveBySurfaceAt(any(), any())).thenReturn(buildPool(5));

            List<ChatSuggestionResponse> result = chatSuggestionService.getActivePool(-1);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("limit=11 → 10으로 클램프되어 칩 최대 10개 반환")
        void limitExceedsMax_clampsToTen() {
            when(chatSuggestionRepository.findActiveBySurfaceAt(any(), any())).thenReturn(buildPool(15));

            List<ChatSuggestionResponse> result = chatSuggestionService.getActivePool(11);

            assertThat(result).hasSize(10);
        }

        @Test
        @DisplayName("활성 풀이 비어 있으면 빈 리스트 반환")
        void emptyPool_returnsEmptyList() {
            when(chatSuggestionRepository.findActiveBySurfaceAt(any(), any())).thenReturn(List.of());

            List<ChatSuggestionResponse> result = chatSuggestionService.getActivePool(4);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("풀 크기가 limit 보다 작으면 풀 전체 반환")
        void poolSmallerThanLimit_returnsAll() {
            when(chatSuggestionRepository.findActiveBySurfaceAt(any(), any())).thenReturn(buildPool(2));

            List<ChatSuggestionResponse> result = chatSuggestionService.getActivePool(4);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("풀 크기가 limit 과 정확히 같으면 전부 반환")
        void poolExactlyLimit_returnsAll() {
            when(chatSuggestionRepository.findActiveBySurfaceAt(any(), any())).thenReturn(buildPool(4));

            List<ChatSuggestionResponse> result = chatSuggestionService.getActivePool(4);

            assertThat(result).hasSize(4);
        }

        @Test
        @DisplayName("반환 DTO 는 id/text 필드를 포함한다")
        void returnsDtoWithTextField() {
            ChatSuggestion suggestion = ChatSuggestion.builder()
                    .text("오늘 기분이 우울한데 영화 추천해줘")
                    .category("mood")
                    .isActive(true)
                    .displayOrder(10)
                    .build();
            when(chatSuggestionRepository.findActiveBySurfaceAt(any(), any())).thenReturn(List.of(suggestion));

            List<ChatSuggestionResponse> result = chatSuggestionService.getActivePool(1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).text()).isEqualTo("오늘 기분이 우울한데 영화 추천해줘");
        }
    }

    // ─────────────────────────────────────────────
    // TrackClick 테스트
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("trackClick — 클릭 트래킹")
    class TrackClickTest {

        @Test
        @DisplayName("존재하는 칩 id → incrementClickCount 호출됨")
        void existingSuggestion_callsIncrement() {
            when(chatSuggestionRepository.existsById(1L)).thenReturn(true);

            chatSuggestionService.trackClick(1L);

            verify(chatSuggestionRepository).incrementClickCount(1L);
        }

        @Test
        @DisplayName("존재하지 않는 id → silent return, incrementClickCount 미호출")
        void nonExistingSuggestion_silentReturn() {
            when(chatSuggestionRepository.existsById(999L)).thenReturn(false);

            chatSuggestionService.trackClick(999L);

            verify(chatSuggestionRepository, never()).incrementClickCount(anyLong());
        }

        @Test
        @DisplayName("null id → silent return, existsById 미호출")
        void nullId_silentReturn() {
            chatSuggestionService.trackClick(null);

            verify(chatSuggestionRepository, never()).existsById(any());
            verify(chatSuggestionRepository, never()).incrementClickCount(anyLong());
        }
    }

    // ─────────────────────────────────────────────
    // DomainMethod 테스트 (엔티티 도메인 메서드 직접 검증)
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("DomainMethod — ChatSuggestion 엔티티 도메인 메서드")
    class DomainMethodTest {

        @Test
        @DisplayName("activate() → isActive=true")
        void activate_setsTrue() {
            ChatSuggestion suggestion = ChatSuggestion.builder()
                    .text("테스트 칩")
                    .isActive(false)
                    .displayOrder(0)
                    .build();

            suggestion.activate();

            assertThat(suggestion.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("deactivate() → isActive=false")
        void deactivate_setsFalse() {
            ChatSuggestion suggestion = ChatSuggestion.builder()
                    .text("테스트 칩")
                    .isActive(true)
                    .displayOrder(0)
                    .build();

            suggestion.deactivate();

            assertThat(suggestion.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("incrementClickCount() → clickCount 1 증가")
        void incrementClickCount_increasesByOne() {
            ChatSuggestion suggestion = ChatSuggestion.builder()
                    .text("테스트 칩")
                    .isActive(true)
                    .displayOrder(0)
                    .build();

            assertThat(suggestion.getClickCount()).isEqualTo(0L);

            suggestion.incrementClickCount();

            assertThat(suggestion.getClickCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("incrementClickCount() 3회 호출 → clickCount=3")
        void incrementClickCount_multipleCallsAccumulate() {
            ChatSuggestion suggestion = ChatSuggestion.builder()
                    .text("테스트 칩")
                    .isActive(true)
                    .displayOrder(0)
                    .build();

            suggestion.incrementClickCount();
            suggestion.incrementClickCount();
            suggestion.incrementClickCount();

            assertThat(suggestion.getClickCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("update() — text/category 변경, null 필드는 기존 값 유지")
        void update_partialFields_preservesNull() {
            ChatSuggestion suggestion = ChatSuggestion.builder()
                    .text("원본 문구")
                    .category("mood")
                    .isActive(true)
                    .displayOrder(10)
                    .build();

            // category=null → 기존 "mood" 유지, text/displayOrder 만 변경
            // 2026-04-23: surface 파라미터 추가됨 (null → 기존 값 유지)
            suggestion.update("수정된 문구", null, null, null, 20, null);

            assertThat(suggestion.getText()).isEqualTo("수정된 문구");
            assertThat(suggestion.getCategory()).isEqualTo("mood");   // 유지
            assertThat(suggestion.getDisplayOrder()).isEqualTo(20);
        }

        @Test
        @DisplayName("update() — startAt/endAt 변경")
        void update_periodFields() {
            ChatSuggestion suggestion = ChatSuggestion.builder()
                    .text("테스트 칩")
                    .isActive(true)
                    .displayOrder(0)
                    .build();

            LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
            LocalDateTime end   = LocalDateTime.of(2026, 5, 31, 23, 59);

            suggestion.update(null, null, start, end, null, null);

            assertThat(suggestion.getStartAt()).isEqualTo(start);
            assertThat(suggestion.getEndAt()).isEqualTo(end);
            assertThat(suggestion.getText()).isEqualTo("테스트 칩"); // 유지
        }
    }

    // ─────────────────────────────────────────────
    // Admin CRUD 기본 동작 테스트
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Admin CRUD — 기본 동작")
    class AdminCrudTest {

        @Test
        @DisplayName("update() — 존재하지 않는 id → CHAT_SUGGESTION_NOT_FOUND 예외")
        void update_notFound_throwsException() {
            when(chatSuggestionRepository.findById(999L)).thenReturn(Optional.empty());

            // 2026-04-23: surface 파라미터 추가로 7 인자 (String, String, Boolean, LocalDateTime, LocalDateTime, Integer, String)
            ChatSuggestionRequest request = new ChatSuggestionRequest(
                    "수정 문구", null, null, null, null, null, null);

            assertThatThrownBy(() -> chatSuggestionService.update(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CHAT_SUGGESTION_NOT_FOUND));
        }

        @Test
        @DisplayName("delete() — 존재하지 않는 id → CHAT_SUGGESTION_NOT_FOUND 예외")
        void delete_notFound_throwsException() {
            when(chatSuggestionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> chatSuggestionService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CHAT_SUGGESTION_NOT_FOUND));
        }

        @Test
        @DisplayName("toggleActive(true) — 존재하는 칩 → activate() 적용 후 isActive=true 응답")
        void toggleActive_true_activates() {
            ChatSuggestion suggestion = ChatSuggestion.builder()
                    .text("테스트 칩")
                    .isActive(false)
                    .displayOrder(0)
                    .build();
            when(chatSuggestionRepository.findById(1L)).thenReturn(Optional.of(suggestion));

            AdminChatSuggestionResponse response = chatSuggestionService.toggleActive(1L, true);

            assertThat(response.isActive()).isTrue();
        }

        @Test
        @DisplayName("toggleActive(false) — 존재하는 칩 → deactivate() 적용 후 isActive=false 응답")
        void toggleActive_false_deactivates() {
            ChatSuggestion suggestion = ChatSuggestion.builder()
                    .text("테스트 칩")
                    .isActive(true)
                    .displayOrder(0)
                    .build();
            when(chatSuggestionRepository.findById(1L)).thenReturn(Optional.of(suggestion));

            AdminChatSuggestionResponse response = chatSuggestionService.toggleActive(1L, false);

            assertThat(response.isActive()).isFalse();
        }
    }
}
