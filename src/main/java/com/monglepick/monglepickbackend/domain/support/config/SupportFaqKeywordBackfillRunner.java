package com.monglepick.monglepickbackend.domain.support.config;

import com.monglepick.monglepickbackend.domain.support.entity.SupportFaq;
import com.monglepick.monglepickbackend.domain.support.repository.SupportFaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 운영 DB 의 기존 FAQ 레코드에 {@code keywords} 값이 비어 있으면 자동으로 백필하는 Runner.
 *
 * <p>배경: v3.3 에서 {@code keywords} 컬럼이 도입되었으나, {@link SupportFaqInitializer}
 * 는 테이블이 비어 있을 때만 INSERT 하는 멱등 시드이므로 운영 DB 에 이미 24건이
 * 있으면 신규 {@code .keywords(...)} 값이 반영되지 않는다. 이 Runner 가 해당 갭을 메운다.</p>
 *
 * <h3>동작 규칙</h3>
 * <ul>
 *   <li>FAQ 전체 조회 → {@code keywords} 가 null 또는 빈 문자열인 건만 대상</li>
 *   <li>{@link #QUESTION_TO_KEYWORDS} 맵에서 {@code question} 문자열로 1:1 매칭</li>
 *   <li>매칭되는 값이 있으면 {@link SupportFaq#updateKeywords(String)} 로 업데이트</li>
 *   <li>매칭 없거나 이미 keywords 가 설정된 레코드(관리자 수동 입력 포함)는 건드리지 않음</li>
 *   <li>실패해도 앱 기동을 막지 않음 (try/catch 로 감싸고 warn 로그만 남김)</li>
 * </ul>
 *
 * <h3>실행 순서</h3>
 * <p>{@code @Order(120)} — {@link SupportFaqInitializer}({@code @Order(100)}) 이후,
 * {@link SupportFaqEsBootstrap}({@code @Order(150)}) 이전에 실행되어 ES 재색인 시
 * keywords 가 반영된 상태가 되도록 한다.</p>
 *
 * <h3>확장 방법</h3>
 * <p>신규 FAQ 를 시드에 추가할 때:
 * <ol>
 *   <li>{@link SupportFaqInitializer#buildSeedData()} 에 {@code .keywords(...)} 포함</li>
 *   <li>본 클래스 {@link #QUESTION_TO_KEYWORDS} 맵에 동일 question/keywords 쌍 추가</li>
 *   <li>그래야 신규 배포에서도 기존 DB 의 미 백필 레코드까지 자동 커버된다</li>
 * </ol>
 * 두 소스가 갈라지지 않도록 <b>반드시 한 쌍으로 유지</b>한다.</p>
 *
 * @see SupportFaqInitializer
 * @see SupportFaqEsBootstrap
 */
@Slf4j
@Component
@Order(120)
@RequiredArgsConstructor
public class SupportFaqKeywordBackfillRunner implements ApplicationRunner {

    /**
     * {@code question} → {@code keywords} (쉼표 구분 동의어) 매핑 테이블.
     *
     * <p>{@link SupportFaqInitializer#buildSeedData()} 의 24건과 1:1 대응하며,
     * 두 소스가 엇갈리지 않도록 갱신 시 함께 수정한다.</p>
     *
     * <p>{@link LinkedHashMap} 으로 선언해 디버깅 시 카테고리별 그룹핑이 눈에 보이게 한다.</p>
     */
    private static final Map<String, String> QUESTION_TO_KEYWORDS;

    static {
        Map<String, String> map = new LinkedHashMap<>();

        // ─────────────────────────────────────────────
        // GENERAL (7건)
        // ─────────────────────────────────────────────
        map.put("몽글픽은 무료로 이용할 수 있나요?",
                "무료,유료,요금,가격,비용,서비스,이용,기본");
        map.put("몽글픽에는 어떤 영화 데이터가 있나요?",
                "영화데이터,데이터베이스,DB,영화정보,보유영화,편수,콘텐츠");
        map.put("서비스 이용 가능 시간은 어떻게 되나요?",
                "이용시간,운영시간,점검,서비스중단,24시간,장애,공지");
        map.put("모바일 앱이 있나요?",
                "앱,모바일앱,iOS,Android,스마트폰,아이폰,갤럭시,다운로드,설치");
        map.put("고객센터 전화번호와 연락처가 어떻게 되나요?",
                "전화번호,연락처,고객센터,콜센터,전화,문의,상담,연락,이메일");
        map.put("이메일로 문의하고 싶어요. 어디로 보내면 되나요?",
                "이메일,메일,email,mail,문의,접수,보내기,연락처");
        map.put("고객센터 응답 시간과 운영 시간이 어떻게 되나요?",
                "응답시간,운영시간,답변,처리시간,영업일,주말,공휴일,얼마나,언제");

        // ─────────────────────────────────────────────
        // ACCOUNT (4건)
        // ─────────────────────────────────────────────
        map.put("회원가입은 어떻게 하나요?",
                "회원가입,가입,signup,계정생성,등록,가입방법,신규");
        map.put("비밀번호를 잊어버렸어요. 어떻게 재설정하나요?",
                "비밀번호,패스워드,암호,변경,재설정,초기화,수정,바꾸기,찾기,잊어버림,분실");
        map.put("소셜 로그인은 어떻게 연동하나요?",
                "소셜로그인,카카오,네이버,구글,간편로그인,SNS,연동,OAuth");
        map.put("회원 탈퇴는 어떻게 하나요?",
                "탈퇴,회원탈퇴,계정삭제,그만두기,해지,탈퇴방법,삭제");

        // ─────────────────────────────────────────────
        // CHAT (3건)
        // ─────────────────────────────────────────────
        map.put("AI 채팅은 어떻게 사용하나요?",
                "AI채팅,채팅,대화,사용법,어떻게,시작,봇,챗봇,영화추천");
        map.put("AI 채팅에서 이미지도 보낼 수 있나요?",
                "이미지,사진,업로드,포스터,첨부,이미지분석,사진올리기,파일");
        map.put("지난번 AI 채팅 내용을 이어서 볼 수 있나요?",
                "채팅이력,대화기록,이어서,저장,히스토리,세션,이전대화,복원");

        // ─────────────────────────────────────────────
        // RECOMMENDATION (3건)
        // ─────────────────────────────────────────────
        map.put("AI 추천은 어떻게 작동하나요?",
                "AI추천,추천알고리즘,작동원리,AI,추천방식,머신러닝,협업필터링");
        map.put("추천 결과가 마음에 들지 않으면 어떻게 하나요?",
                "추천불만,재추천,피드백,별로,마음에안들어,다시,취향수정,개선");
        map.put("둘이 영화 고르기(Movie Match)는 어떻게 사용하나요?",
                "둘이,커플,영화고르기,MovieMatch,함께볼영화,같이,매칭,공동추천");

        // ─────────────────────────────────────────────
        // COMMUNITY (3건)
        // ─────────────────────────────────────────────
        map.put("커뮤니티 게시글은 어떻게 작성하나요?",
                "게시글,글쓰기,작성,커뮤니티,포스팅,글,올리기,토론,게시판");
        map.put("부적절한 게시글을 신고하려면 어떻게 하나요?",
                "신고,불법,욕설,혐오,스팸,부적절,광고,도배,신고방법,차단");
        map.put("영화 리뷰는 어디에서 작성하나요?",
                "리뷰,후기,평점,별점,감상,의견,작성,영화평,코멘트");

        // ─────────────────────────────────────────────
        // PAYMENT (4건)
        // ─────────────────────────────────────────────
        map.put("포인트는 어떻게 충전하나요?",
                "포인트,충전,포인트충전,결제,캐시,리워드,구매,크레딧,적립");
        map.put("환불은 어떻게 받나요?",
                "환불,반환,취소,회수,돈,결제취소,돌려받기,환급,반품");
        map.put("구독은 어떻게 해지하나요?",
                "구독해지,멤버십해지,정기결제취소,구독취소,해지방법,해지,중단,자동결제");
        map.put("어떤 결제 수단을 사용할 수 있나요?",
                "결제수단,카드,신용카드,체크카드,간편결제,토스,카카오페이,계좌이체,휴대폰결제");

        QUESTION_TO_KEYWORDS = Map.copyOf(map);
    }

    private final SupportFaqRepository faqRepository;

    /**
     * 앱 기동 후 keywords 백필 실행.
     *
     * <p>검색 품질 기능이므로 실패해도 앱 기동을 막으면 안 된다. 전체 try/catch 로 감싸고
     * 예외는 warn 로그로만 기록한다.</p>
     *
     * @param args 앱 실행 인수 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            // 24건뿐인 작은 테이블이라 전체 로드 후 메모리 필터가 리포지토리 추가보다 간결
            List<SupportFaq> all = faqRepository.findAll();

            int updated = 0;
            int skippedAlreadySet = 0;
            int skippedUnknownQuestion = 0;

            for (SupportFaq faq : all) {
                final String current = faq.getKeywords();

                // 이미 keywords 가 설정된 레코드는 건드리지 않는다
                // (관리자가 수동 입력한 값을 덮어쓰지 않기 위함)
                if (current != null && !current.isBlank()) {
                    skippedAlreadySet++;
                    continue;
                }

                // 질문 문자열로 매핑 테이블에서 찾기
                final String mappedKeywords = QUESTION_TO_KEYWORDS.get(faq.getQuestion());
                if (mappedKeywords == null) {
                    // 운영자가 관리자 페이지에서 추가한 신규 FAQ 일 수 있음 — 스킵
                    skippedUnknownQuestion++;
                    continue;
                }

                faq.updateKeywords(mappedKeywords);
                updated++;
            }

            if (updated > 0) {
                log.info("[SupportFaqKeywordBackfill] keywords 자동 백필 완료 — 업데이트 {}건 / 기보유 스킵 {}건 / 미매칭 스킵 {}건",
                        updated, skippedAlreadySet, skippedUnknownQuestion);
            } else {
                log.info("[SupportFaqKeywordBackfill] 백필 대상 없음 — 기보유 {}건 / 미매칭 {}건",
                        skippedAlreadySet, skippedUnknownQuestion);
            }
        } catch (Exception e) {
            // 백필 실패가 서비스 기동을 막지 않도록 전역 catch
            log.warn("[SupportFaqKeywordBackfill] 백필 중 예외 발생 — 계속 진행. err={}", e.getMessage());
        }
    }
}
