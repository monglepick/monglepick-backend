package com.monglepick.monglepickbackend.domain.support.config;

import com.monglepick.monglepickbackend.domain.support.entity.SupportCategory;
import com.monglepick.monglepickbackend.domain.support.entity.SupportFaq;
import com.monglepick.monglepickbackend.domain.support.repository.SupportFaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 앱 시작 시 고객센터 FAQ 시드 데이터를 자동으로 초기화하는 컴포넌트.
 *
 * <p>{@link ApplicationRunner} 를 구현하여 Spring Boot 컨텍스트 완전 로드 이후 실행된다.
 * FAQ 테이블이 비어 있을 때만 시드 데이터를 INSERT 하며,
 * 이미 데이터가 존재하면 전체 스킵하여 운영자가 관리자 페이지에서 추가/수정한
 * FAQ 를 덮어쓰지 않는다(멱등성 보장).</p>
 *
 * <h3>시드 데이터 정책</h3>
 * <ul>
 *   <li>6개 카테고리(GENERAL/ACCOUNT/CHAT/RECOMMENDATION/COMMUNITY/PAYMENT) 전체 커버</li>
 *   <li>카테고리당 3~4개, 총 {@value #EXPECTED_SEED_COUNT}건 등록</li>
 *   <li>sortOrder 로 카테고리 내 표시 순서 지정 (낮을수록 상위)</li>
 *   <li>isPublished=true 로 생성 직후 즉시 사용자에게 노출</li>
 * </ul>
 *
 * <h3>실행 순서</h3>
 * <p>{@code @Order(100)} — 다른 초기화(업적/리워드/구독)와 독립적이므로 늦게 실행된다.</p>
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class SupportFaqInitializer implements ApplicationRunner {

    /** 시드 데이터 총 건수 (정보 로그용 상수) */
    private static final int EXPECTED_SEED_COUNT = 21;

    private final SupportFaqRepository faqRepository;

    /**
     * 앱 기동 후 FAQ 테이블이 비어 있으면 시드 데이터를 INSERT 한다.
     *
     * <p>트랜잭션 내에서 일괄 저장되므로 중간 실패 시 전체 롤백된다.
     * 이미 데이터가 존재하는 경우(운영 중 재시작 시) 조기 반환하여 운영 데이터를 보호한다.</p>
     *
     * @param args 앱 실행 인수 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        /* 멱등성 보장 — FAQ 가 1건이라도 존재하면 시드 주입 스킵
         * (운영자가 관리자 페이지에서 수동 등록한 FAQ 를 보호) */
        long existingCount = faqRepository.count();
        if (existingCount > 0) {
            log.info("[SupportFaqInitializer] 기존 FAQ {}건 존재 — 시드 주입 스킵", existingCount);
            return;
        }

        log.info("[SupportFaqInitializer] FAQ 시드 데이터 초기화 시작");

        List<SupportFaq> seeds = buildSeedData();
        faqRepository.saveAll(seeds);

        log.info("[SupportFaqInitializer] FAQ 시드 {}건 등록 완료", seeds.size());
    }

    /**
     * 시드 FAQ 데이터 목록을 생성한다.
     *
     * <p>각 FAQ 는 sortOrder 값으로 카테고리 내 표시 순서를 제어한다.
     * answer 본문은 사용자 친화적인 존댓말 + 구체적 절차/수치를 포함한다.</p>
     *
     * @return 저장할 FAQ 엔티티 목록 (순서 보존 불필요, sortOrder 기반 정렬)
     */
    private List<SupportFaq> buildSeedData() {
        return List.of(
                // ─────────────────────────────────────────────
                // 일반 (GENERAL) — 4건
                // ─────────────────────────────────────────────
                SupportFaq.builder()
                        .category(SupportCategory.GENERAL)
                        .question("몽글픽은 무료로 이용할 수 있나요?")
                        .answer("네, 기본 기능은 모두 무료입니다. 회원가입만 하시면 AI 영화 추천, 커뮤니티, 리뷰 작성, 퀴즈, 도장깨기 등 주요 기능을 자유롭게 이용하실 수 있어요.\n\n"
                                + "다만 AI 추천에는 등급별 일일 무료 한도가 있으며, 한도를 초과하면 이용권(쿠폰)을 구매하거나 구독 상품으로 보너스 쿼터를 받으실 수 있습니다.\n\n"
                                + "매일 출석 체크로 포인트를 모아 이용권을 저렴하게 구매할 수도 있어요!")
                        .sortOrder(10)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.GENERAL)
                        .question("몽글픽에는 어떤 영화 데이터가 있나요?")
                        .answer("TMDB, 영화진흥위원회(KOBIS), 한국영화데이터베이스(KMDb) 등에서 수집한 약 117만 건의 영화 데이터를 보유하고 있습니다.\n\n"
                                + "한국 영화, 해외 영화, 독립 영화, 고전까지 폭넓게 포함되어 있으며, 포스터/줄거리/출연진/감독/장르/평점 등 풍부한 메타데이터와 함께 제공됩니다.\n\n"
                                + "AI 추천 시 이 모든 데이터를 벡터 검색(Qdrant) + 텍스트 검색(Elasticsearch) + 그래프 검색(Neo4j) 기술로 하이브리드 결합하여 최적의 영화를 찾아드려요.")
                        .sortOrder(20)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.GENERAL)
                        .question("서비스 이용 가능 시간은 어떻게 되나요?")
                        .answer("몽글픽은 24시간 365일 언제든 이용하실 수 있습니다.\n\n"
                                + "다만 정기 점검이나 긴급 장애 대응이 필요한 경우 일시적으로 서비스가 중단될 수 있으며, 사전 공지는 고객센터 공지사항과 앱 내 배너를 통해 안내드립니다.\n\n"
                                + "점검 시간은 보통 야간 시간대(새벽 2~5시)에 이뤄지며, 작업 완료까지의 예상 시간을 함께 공지하니 참고해 주세요.")
                        .sortOrder(30)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.GENERAL)
                        .question("모바일 앱이 있나요?")
                        .answer("현재 몽글픽은 웹 브라우저에 최적화된 반응형 서비스로 제공됩니다. 모바일 브라우저에서도 PC 웹 동일한 기능을 편리하게 이용하실 수 있어요.\n\n"
                                + "별도의 iOS/Android 네이티브 앱은 아직 출시되지 않았으며, 모바일 앱 출시 계획은 향후 공지사항을 통해 안내드릴 예정입니다.\n\n"
                                + "홈 화면에 추가하면 앱처럼 사용하실 수 있으니, 브라우저 메뉴의 '홈 화면에 추가' 기능을 활용해 보세요.")
                        .sortOrder(40)
                        .build(),

                // ─────────────────────────────────────────────
                // 계정 (ACCOUNT) — 4건
                // ─────────────────────────────────────────────
                SupportFaq.builder()
                        .category(SupportCategory.ACCOUNT)
                        .question("회원가입은 어떻게 하나요?")
                        .answer("이메일 회원가입과 소셜 로그인 두 가지 방식을 지원합니다.\n\n"
                                + "1. 이메일 회원가입: 우측 상단 '회원가입' 버튼 → 이메일/비밀번호/닉네임 입력 → 이메일 인증 → 가입 완료\n"
                                + "2. 소셜 로그인: 카카오, 네이버, 구글 계정으로 한 번의 클릭으로 간편 가입\n\n"
                                + "가입 완료 후 온보딩 단계에서 선호 장르를 설정하시면, 첫 추천부터 맞춤형 영화를 받아보실 수 있어요.")
                        .sortOrder(10)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.ACCOUNT)
                        .question("비밀번호를 잊어버렸어요. 어떻게 재설정하나요?")
                        .answer("로그인 페이지 하단의 '비밀번호 찾기' 링크를 눌러주세요.\n\n"
                                + "가입하신 이메일 주소를 입력하시면 재설정 링크가 포함된 메일이 발송됩니다. 메일 내 링크를 클릭하시면 새 비밀번호를 설정할 수 있는 페이지로 이동합니다.\n\n"
                                + "메일이 도착하지 않으면 스팸함을 확인해 주시고, 5분 이상 기다려도 수신되지 않으면 고객센터에 문의 부탁드립니다.\n\n"
                                + "보안을 위해 비밀번호는 영문/숫자/특수문자를 조합해 8자 이상으로 설정해 주세요.")
                        .sortOrder(20)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.ACCOUNT)
                        .question("소셜 로그인은 어떻게 연동하나요?")
                        .answer("몽글픽은 카카오, 네이버, 구글 계정을 통한 간편 로그인을 지원합니다.\n\n"
                                + "로그인 페이지에서 원하는 소셜 아이콘을 클릭하시면 해당 서비스의 동의 화면으로 이동합니다. 기본 정보(이름/이메일/프로필) 제공에 동의하시면 자동으로 가입 및 로그인이 완료됩니다.\n\n"
                                + "이미 이메일 가입한 계정과 동일한 이메일로 소셜 가입을 시도하면 기존 계정에 소셜 로그인 방식이 추가됩니다.\n\n"
                                + "연동을 해제하고 싶으신 경우 마이페이지 → 계정 설정에서 개별 해제가 가능합니다.")
                        .sortOrder(30)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.ACCOUNT)
                        .question("회원 탈퇴는 어떻게 하나요?")
                        .answer("마이페이지 → 계정 설정 → '회원 탈퇴' 버튼을 통해 진행하실 수 있습니다.\n\n"
                                + "탈퇴 시 유의사항:\n"
                                + "• 보유 포인트, 이용권, 구독 잔여 혜택은 모두 소멸되며 복구되지 않습니다.\n"
                                + "• 작성하신 리뷰/댓글/게시글은 '탈퇴한 사용자'로 표시되며 내용은 유지됩니다(커뮤니티 연속성 보호).\n"
                                + "• 동일 이메일로 30일 내 재가입은 제한될 수 있습니다.\n\n"
                                + "진행 중인 구독이 있는 경우 먼저 구독을 해지한 후 탈퇴해 주세요. 탈퇴 전 마지막으로 한 번 더 생각해 보시길 권해드려요!")
                        .sortOrder(40)
                        .build(),

                // ─────────────────────────────────────────────
                // AI 채팅 (CHAT) — 3건
                // ─────────────────────────────────────────────
                SupportFaq.builder()
                        .category(SupportCategory.CHAT)
                        .question("AI 채팅은 어떻게 사용하나요?")
                        .answer("상단 메뉴의 'AI 추천 → AI 채팅 추천'을 누르시면 채팅 화면으로 이동합니다.\n\n"
                                + "사용법은 간단해요!\n"
                                + "1. 기분, 상황, 좋아하는 장르/분위기를 자유롭게 입력해 주세요. 예: '비 오는 날 혼자 보기 좋은 잔잔한 영화'\n"
                                + "2. AI가 대화를 통해 취향을 더 깊게 파악합니다. 모호한 부분은 되물어봐요.\n"
                                + "3. 몇 차례 대화 후 맞춤 영화 카드가 답변에 포함되어 보여집니다.\n\n"
                                + "대화가 길어질수록 추천 정확도가 높아지니, AI의 질문에 구체적으로 답해주시는 걸 추천해요.")
                        .sortOrder(10)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.CHAT)
                        .question("AI 채팅에서 이미지도 보낼 수 있나요?")
                        .answer("네! 영화 포스터, 장면 스틸컷, 분위기가 느껴지는 사진을 업로드하시면 AI가 이미지를 분석하여 비슷한 분위기의 영화를 추천해 드립니다.\n\n"
                                + "사용 방법:\n"
                                + "• 채팅 입력창 왼쪽의 이미지 업로드 아이콘(📎) 클릭\n"
                                + "• JPG/PNG/WebP 포맷 지원, 최대 10MB까지 업로드 가능\n"
                                + "• 이미지와 함께 텍스트를 입력하면 더 정확한 추천이 가능해요\n\n"
                                + "예: 영화 포스터를 올리면서 '이런 느낌의 스릴러 추천해줘'라고 적으시면 시각적 분위기 + 장르 조건을 모두 고려합니다.")
                        .sortOrder(20)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.CHAT)
                        .question("지난번 AI 채팅 내용을 이어서 볼 수 있나요?")
                        .answer("네, 채팅 이력은 자동으로 저장되며 언제든 이어서 대화하실 수 있습니다.\n\n"
                                + "• 채팅 화면 좌측 사이드바에 지난 대화 목록이 표시됩니다.\n"
                                + "• 원하는 세션을 클릭하면 해당 대화가 복원되어 맥락을 유지한 채 새 질문을 이어갈 수 있어요.\n"
                                + "• 대화 제목은 첫 메시지를 기반으로 자동 생성되며, 수정도 가능합니다.\n\n"
                                + "로그인 사용자의 채팅 이력은 안전하게 암호화되어 저장되며, 마이페이지 → AI 추천 내역에서도 확인하실 수 있습니다.")
                        .sortOrder(30)
                        .build(),

                // ─────────────────────────────────────────────
                // 추천 (RECOMMENDATION) — 3건
                // ─────────────────────────────────────────────
                SupportFaq.builder()
                        .category(SupportCategory.RECOMMENDATION)
                        .question("AI 추천은 어떻게 작동하나요?")
                        .answer("몽글픽 AI 추천은 여러 기술을 결합한 하이브리드 방식입니다.\n\n"
                                + "1. 대화 이해: 사용자 메시지의 감정·의도·장르·분위기를 LLM으로 분석\n"
                                + "2. 하이브리드 검색: 벡터 검색(Qdrant) + 텍스트 검색(Elasticsearch) + 그래프 검색(Neo4j)을 RRF로 합산\n"
                                + "3. 협업 필터링(CF): 유사 취향 사용자의 시청/평점 데이터로 보완\n"
                                + "4. LLM 리랭커: 상위 후보를 대화 맥락에 맞게 재정렬\n"
                                + "5. MMR 다양성: 비슷한 영화만 몰리지 않게 다양한 장르/분위기 포함\n\n"
                                + "대화가 길어질수록, 리뷰/위시리스트 데이터가 쌓일수록 추천이 정교해집니다.")
                        .sortOrder(10)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.RECOMMENDATION)
                        .question("추천 결과가 마음에 들지 않으면 어떻게 하나요?")
                        .answer("AI에게 더 구체적으로 취향을 알려주시면 즉시 재추천이 가능해요!\n\n"
                                + "• '좀 더 가벼운 걸로' / '반전이 있는 스릴러로' / '이건 너무 옛날 영화야' 등 피드백을 자연스럽게 대화로 전달\n"
                                + "• 추천 카드의 👍/👎 버튼으로 선호/비선호를 표시하면 AI가 학습하여 다음 추천에 반영\n"
                                + "• 마이페이지 → AI 추천 내역에서 과거 추천에 대한 피드백도 남길 수 있어요\n\n"
                                + "또한 온보딩 단계에서 설정한 선호 장르를 마이페이지에서 수정하시면 기본 추천 경향 자체가 달라집니다.")
                        .sortOrder(20)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.RECOMMENDATION)
                        .question("둘이 영화 고르기(Movie Match)는 어떻게 사용하나요?")
                        .answer("상단 메뉴 'AI 추천 → 둘이 영화 고르기'에서 사용하실 수 있습니다.\n\n"
                                + "사용 방법:\n"
                                + "1. 두 사람이 각자 좋아하는 영화를 한 편씩 선택\n"
                                + "2. AI가 두 영화의 교집합 특성(장르/분위기/키워드)을 분석\n"
                                + "3. 두 영화를 모두 좋아할 법한 함께 볼 영화 3편을 추천\n\n"
                                + "내부적으로는 두 영화의 특성 벡터를 결합한 센트로이드 검색 + '두 영화를 모두 높게 평가한 사용자들의 또 다른 선호작' 협업 필터링 + LLM 리랭커를 조합합니다.\n\n"
                                + "로그인 없이도 사용 가능하니 친구/연인/가족과 함께 편하게 즐겨보세요!")
                        .sortOrder(30)
                        .build(),

                // ─────────────────────────────────────────────
                // 커뮤니티 (COMMUNITY) — 3건
                // ─────────────────────────────────────────────
                SupportFaq.builder()
                        .category(SupportCategory.COMMUNITY)
                        .question("커뮤니티 게시글은 어떻게 작성하나요?")
                        .answer("로그인 후 상단 메뉴의 '커뮤니티' 페이지로 이동하신 뒤 '글쓰기' 버튼을 눌러주세요.\n\n"
                                + "게시글 작성 시 선택 가능한 카테고리:\n"
                                + "• 자유 토론 — 영화에 대한 자유로운 이야기\n"
                                + "• 추천 요청 — 다른 사용자의 추천을 받고 싶을 때\n"
                                + "• 리뷰 공유 — 본인이 본 영화의 감상 공유\n"
                                + "• 정보 공유 — 개봉 소식, 이벤트 등\n\n"
                                + "제목과 본문을 작성하고 필요 시 영화 태그/이미지를 추가할 수 있습니다. 작성 후에는 댓글을 통해 다른 사용자들과 소통하실 수 있어요.")
                        .sortOrder(10)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.COMMUNITY)
                        .question("부적절한 게시글을 신고하려면 어떻게 하나요?")
                        .answer("게시글이나 댓글 우측 상단의 더보기(⋮) 메뉴에서 '신고' 버튼을 누르시면 신고 양식이 열립니다.\n\n"
                                + "신고 사유 선택지:\n"
                                + "• 욕설/비방/혐오 표현\n"
                                + "• 음란물/선정적 콘텐츠\n"
                                + "• 스팸/광고/도배\n"
                                + "• 개인정보 노출\n"
                                + "• 저작권 침해\n"
                                + "• 기타 (직접 입력)\n\n"
                                + "신고된 콘텐츠는 관리자가 24시간 이내에 검토합니다. 검토 결과에 따라 경고/게시글 삭제/계정 제재 등의 조치가 이뤄집니다.\n\n"
                                + "허위 신고가 반복되면 신고 기능이 제한될 수 있으니 신중히 이용해 주세요.")
                        .sortOrder(20)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.COMMUNITY)
                        .question("영화 리뷰는 어디에서 작성하나요?")
                        .answer("리뷰는 여러 경로로 자유롭게 작성하실 수 있어요.\n\n"
                                + "1. 영화 상세 페이지: 영화 검색 → 상세 진입 → '리뷰 작성' 버튼\n"
                                + "2. AI 채팅 추천 후: 추천받은 영화 카드에서 바로 리뷰 작성 가능\n"
                                + "3. 위시리스트/시청 이력: 마이페이지에서 시청한 영화에 리뷰 추가\n"
                                + "4. 도장깨기 코스 완주 시: 코스 내 영화 각각에 리뷰 작성 유도\n\n"
                                + "리뷰 작성 시 별점(1~5점) + 본문을 입력하시면 되며, 작성한 리뷰는 커뮤니티의 '리뷰' 탭에서 다른 사용자들과 공유됩니다. 좋은 리뷰는 활동 리워드 포인트로도 보상받을 수 있어요!")
                        .sortOrder(30)
                        .build(),

                // ─────────────────────────────────────────────
                // 결제 (PAYMENT) — 4건
                // ─────────────────────────────────────────────
                SupportFaq.builder()
                        .category(SupportCategory.PAYMENT)
                        .question("포인트는 어떻게 충전하나요?")
                        .answer("포인트 페이지(상단 아바타 메뉴 → 포인트)에서 '포인트 충전하기' 버튼을 누르시면 결제 페이지로 이동합니다.\n\n"
                                + "충전 방법:\n"
                                + "1. 포인트 팩 선택 (예: 100P, 500P, 1,000P, 5,000P)\n"
                                + "2. Toss Payments 결제창에서 카드 또는 간편결제(토스페이/카카오페이 등) 선택\n"
                                + "3. 결제 완료 시 즉시 포인트 잔액에 반영\n\n"
                                + "1포인트 = 10원으로 통일되며, 구독 상품을 이용하시면 매달 포인트가 자동 지급되어 더 저렴한 단가로 포인트를 획득하실 수 있습니다.\n\n"
                                + "매일 출석 체크로도 10P~60P를 무료로 받으실 수 있으니 꼭 챙겨주세요!")
                        .sortOrder(10)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.PAYMENT)
                        .question("환불은 어떻게 받나요?")
                        .answer("결제 후 7일 이내, 미사용 상태의 이용권은 전액 환불이 가능합니다.\n\n"
                                + "환불 절차:\n"
                                + "1. 마이페이지 → 결제·구독 → 결제 내역으로 이동\n"
                                + "2. 환불을 원하는 결제 건의 '환불 신청' 버튼 클릭\n"
                                + "3. 환불 사유 선택 후 신청 완료\n"
                                + "4. 영업일 기준 3~5일 내 결제 수단으로 환불 처리\n\n"
                                + "환불 제한:\n"
                                + "• 이미 이용권을 사용하신 경우 잔여분에 대해서만 부분 환불\n"
                                + "• 7일 경과 또는 전자상거래법상 환불 예외 사유에 해당하면 환불 불가\n\n"
                                + "환불 관련 문의는 '문의하기' 탭에서 결제 번호와 함께 남겨주시면 빠르게 처리해 드릴게요.")
                        .sortOrder(20)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.PAYMENT)
                        .question("구독은 어떻게 해지하나요?")
                        .answer("마이페이지 → 결제·구독 페이지에서 현재 구독 상품 옆 '구독 해지' 버튼을 눌러주세요.\n\n"
                                + "해지 시 안내사항:\n"
                                + "• 해지 후에도 현재 결제 주기 종료일까지는 구독 혜택이 유지됩니다(예: 월간 구독 15일에 해지 → 말일까지 사용 가능).\n"
                                + "• 해지 후 다음 주기부터는 자동 결제가 진행되지 않습니다.\n"
                                + "• 이미 지급된 이번 달 월간 포인트/AI 보너스는 회수되지 않습니다.\n\n"
                                + "해지 후 언제든 다시 구독하실 수 있으며, 재구독 시 혜택이 즉시 재개됩니다.\n\n"
                                + "연간 구독의 경우 중도 해지 시 남은 기간에 대한 환불 정책이 별도 적용되니 결제·구독 페이지의 약관을 꼭 확인해 주세요.")
                        .sortOrder(30)
                        .build(),
                SupportFaq.builder()
                        .category(SupportCategory.PAYMENT)
                        .question("어떤 결제 수단을 사용할 수 있나요?")
                        .answer("몽글픽은 Toss Payments를 통해 안전하고 다양한 결제 수단을 지원합니다.\n\n"
                                + "지원 결제 수단:\n"
                                + "• 신용/체크카드 (국내외 주요 카드사 전체)\n"
                                + "• 간편결제 (토스페이, 카카오페이, 네이버페이, 페이코 등)\n"
                                + "• 계좌이체 / 가상계좌\n"
                                + "• 휴대폰 소액결제 (월 한도 내)\n\n"
                                + "모든 결제는 PCI-DSS 인증을 받은 Toss Payments의 보안 결제창에서 처리되며, 몽글픽은 카드 번호 등의 민감 정보를 저장하지 않습니다.\n\n"
                                + "결제에 문제가 있으시다면 '문의하기' 탭에서 주문번호와 함께 말씀해 주세요!")
                        .sortOrder(40)
                        .build()
        );
    }
}
