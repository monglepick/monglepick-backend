package com.monglepick.monglepickbackend.domain.roadmap.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.QuizResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.SubmitRequest;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.SubmitResponse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import com.monglepick.monglepickbackend.domain.roadmap.entity.QuizParticipation;
import com.monglepick.monglepickbackend.domain.roadmap.repository.QuizParticipationRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.QuizRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 퀴즈 서비스 — 퀴즈 목록 조회, 정답 제출, 리워드 지급 비즈니스 로직.
 *
 * <h3>퀴즈 파이프라인</h3>
 * <pre>
 * [AI 생성 / 관리자 등록] → PENDING
 *   → 관리자 검수 통과  → APPROVED
 *   → 출제 예정일 도래   → PUBLISHED  ← 사용자 참여 가능 상태
 *   → 사용자 답변 제출   → QuizParticipation INSERT
 *   → 정답이면          → RewardService.grantReward(QUIZ_CORRECT) 호출
 * </pre>
 *
 * <h3>리워드 정책</h3>
 * <ul>
 *   <li>리워드는 최초 정답 1회만 지급한다.</li>
 *   <li>동일 퀴즈를 여러 번 제출할 수 있지만, 이미 isCorrect=true 참여 기록이 있으면
 *       채점은 수행하되 {@code grantReward} 호출은 건너뛴다.</li>
 *   <li>{@code RewardService.grantReward}의 REQUIRES_NEW 트랜잭션 덕분에
 *       리워드 지급 실패가 퀴즈 제출 저장 롤백을 유발하지 않는다.</li>
 * </ul>
 *
 * <h3>중복 참여 처리</h3>
 * <p>{@code quiz_participations} 테이블에 (quiz_id, user_id) UNIQUE 제약이 있으므로
 * 동일 퀴즈에 중복 레코드 INSERT를 시도하면 DB 레벨에서 예외가 발생한다.
 * 서비스 레이어에서 {@code findByQuiz_QuizIdAndUserId}로 사전 조회하여
 * 이미 참여한 경우 기존 기록을 업데이트한다 (재제출 허용, 단 리워드 최초 1회).</p>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code readOnly=true} (목록 조회 최적화)</li>
 *   <li>{@code submitAnswer}: {@code @Transactional} 오버라이드 (쓰기 트랜잭션)</li>
 * </ul>
 *
 * @see QuizRepository         퀴즈 목록/날짜별 조회
 * @see QuizParticipationRepository  참여 이력 조회/저장
 * @see RewardService          QUIZ_CORRECT 포인트 지급 위임
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    /** 퀴즈 리포지토리 — 상태별/영화별/날짜별 퀴즈 조회 */
    private final QuizRepository quizRepository;

    /** 퀴즈 참여 리포지토리 — 사용자 답변 제출 이력 조회/저장 */
    private final QuizParticipationRepository participationRepository;

    /** 리워드 서비스 — QUIZ_CORRECT 포인트 지급 위임 (REQUIRES_NEW 독립 트랜잭션) */
    private final RewardService rewardService;

    /**
     * JSON 파싱용 ObjectMapper (스레드 안전, 클래스 로딩 시 1회 초기화).
     * Quiz.options JSON 컬럼(예: ["선택지A","선택지B"])을 List&lt;String&gt;으로 역직렬화할 때 사용한다.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ────────────────────────────────────────────────────────────────
    // 퀴즈 목록 조회
    // ────────────────────────────────────────────────────────────────

    /**
     * 특정 영화의 PUBLISHED 퀴즈 목록을 조회한다.
     *
     * <p>영화 상세 페이지 하단 "이 영화 퀴즈" 섹션에서 사용한다.
     * 정답({@code correctAnswer})은 응답에 포함되지 않는다 — 보안상 클라이언트에 노출 금지.</p>
     *
     * @param movieId 조회할 영화 ID (VARCHAR(50))
     * @return PUBLISHED 상태 퀴즈 DTO 목록 (없으면 빈 리스트)
     */
    public List<QuizResponse> getMovieQuizzes(String movieId) {
        log.debug("영화별 퀴즈 목록 조회: movieId={}", movieId);

        List<Quiz> quizzes = quizRepository.findByMovieIdAndStatus(
                movieId, Quiz.QuizStatus.PUBLISHED
        );

        return quizzes.stream()
                .map(quiz -> QuizResponse.from(quiz, parseOptions(quiz.getOptions())))
                .toList();
    }

    /**
     * 오늘 날짜(quizDate = 오늘)의 PUBLISHED 퀴즈 목록을 조회한다.
     *
     * <p>메인 페이지 또는 퀴즈 전용 탭의 "오늘의 퀴즈" 섹션에서 사용한다.
     * quizDate가 null인 퀴즈(즉시 출제 가능)는 별도 메서드({@link #getAllPublishedQuizzes})를 통해 제공한다.</p>
     *
     * @return 오늘 날짜의 PUBLISHED 퀴즈 DTO 목록 (없으면 빈 리스트)
     */
    public List<QuizResponse> getTodayQuizzes() {
        LocalDate today = LocalDate.now();
        log.debug("오늘의 퀴즈 조회: date={}", today);

        List<Quiz> quizzes = quizRepository.findByQuizDateAndStatus(
                today, Quiz.QuizStatus.PUBLISHED
        );

        return quizzes.stream()
                .map(quiz -> QuizResponse.from(quiz, parseOptions(quiz.getOptions())))
                .toList();
    }

    /**
     * PUBLISHED 상태 전체 퀴즈 목록을 조회한다 (날짜 무관).
     *
     * <p>퀴즈 목록 전체를 제공해야 하는 경우의 fallback 메서드이다.</p>
     *
     * @return 전체 PUBLISHED 퀴즈 DTO 목록
     */
    public List<QuizResponse> getAllPublishedQuizzes() {
        log.debug("전체 PUBLISHED 퀴즈 목록 조회");

        return quizRepository.findByStatus(Quiz.QuizStatus.PUBLISHED)
                .stream()
                .map(quiz -> QuizResponse.from(quiz, parseOptions(quiz.getOptions())))
                .toList();
    }

    // ────────────────────────────────────────────────────────────────
    // 정답 제출
    // ────────────────────────────────────────────────────────────────

    /**
     * 퀴즈 정답을 제출하고 채점 결과와 리워드 지급 여부를 반환한다.
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>퀴즈 존재 확인 — {@code quizId}로 PUBLISHED 상태 퀴즈 조회</li>
     *   <li>사용자 존재 확인 — {@code userId}로 User 엔티티 조회 (QuizParticipation FK 목적)</li>
     *   <li>기존 참여 기록 조회 — 중복 제출 여부 판단</li>
     *   <li>채점 — {@code correctAnswer.trim().equalsIgnoreCase(userAnswer.trim())}</li>
     *   <li>참여 기록 저장/업데이트 — 신규면 INSERT, 기존이면 {@code submit()} 호출로 UPDATE</li>
     *   <li>리워드 지급 — 정답이고 최초 정답인 경우만 {@code QUIZ_CORRECT} 리워드 지급</li>
     * </ol>
     *
     * <h4>리워드 중복 지급 방지 로직</h4>
     * <p>제출 전에 {@code existsByQuiz_QuizIdAndUser_UserIdAndIsCorrect(true)} 로 확인한다.
     * 이미 정답 기록이 있으면 이번 제출이 정답이더라도 리워드를 지급하지 않는다.</p>
     *
     * @param userId     정답을 제출하는 사용자 ID (JWT에서 추출)
     * @param quizId     대상 퀴즈 ID
     * @param request    정답 제출 요청 DTO ({@code answer} 필드)
     * @return 채점 결과 DTO ({@code correct}, {@code explanation}, {@code rewardPoint})
     * @throws BusinessException {@link ErrorCode#NOT_FOUND} 퀴즈가 PUBLISHED 상태가 아니거나 존재하지 않을 때
     * @throws BusinessException {@link ErrorCode#NOT_FOUND} 사용자가 존재하지 않을 때
     */
    @Transactional
    public SubmitResponse submitAnswer(String userId, Long quizId, SubmitRequest request) {
        // ① 퀴즈 존재 및 PUBLISHED 상태 확인
        Quiz quiz = quizRepository.findById(quizId)
                .filter(q -> q.getStatus() == Quiz.QuizStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.QUIZ_NOT_FOUND,
                        "존재하지 않거나 출제 중이 아닌 퀴즈입니다: quizId=" + quizId
                ));

        // ② 사용자 검증은 JWT 인증 단계에서 이미 처리됨 — users 테이블 쓰기 소유는 김민규(MyBatis),
        //    JPA에서 fetch 하지 않고 String userId만 보관 (설계서 §15.4)

        // ③ 리워드 중복 지급 방지를 위한 사전 확인 — 이미 정답 기록이 있으면 리워드 지급 불가
        boolean alreadyCorrect = participationRepository
                .existsByQuiz_QuizIdAndUserIdAndIsCorrect(quizId, userId, true);

        // ④ 채점 — 대소문자 무시, 앞뒤 공백 제거 후 비교
        String userAnswer = request.answer() != null ? request.answer().trim() : "";
        String correctAnswer = quiz.getCorrectAnswer() != null ? quiz.getCorrectAnswer().trim() : "";
        boolean isCorrect = correctAnswer.equalsIgnoreCase(userAnswer);

        log.info("퀴즈 채점: userId={}, quizId={}, isCorrect={}, alreadyCorrect={}",
                userId, quizId, isCorrect, alreadyCorrect);

        // ⑤ 참여 기록 저장/업데이트
        saveOrUpdateParticipation(quiz, userId, quizId, userAnswer, isCorrect);

        // ⑥ 리워드 지급 — 정답이고 최초 정답인 경우에만 지급
        //    alreadyCorrect=true이면 이번 제출이 정답이라도 리워드를 건너뛴다
        if (isCorrect && !alreadyCorrect) {
            grantQuizReward(userId, quizId);
        }

        // ⑦ 응답 반환 — rewardPoint는 정답 최초 지급인 경우에만 실제 값, 나머지는 0
        int awardedPoint = (isCorrect && !alreadyCorrect) ? quiz.getRewardPoint() : 0;

        return new SubmitResponse(isCorrect, quiz.getExplanation(), awardedPoint);
    }

    // ────────────────────────────────────────────────────────────────
    // private 헬퍼 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 퀴즈 참여 기록을 저장하거나 기존 기록을 업데이트한다.
     *
     * <p>동일 (quiz_id, user_id) 조합에 UNIQUE 제약이 있으므로,
     * 기존 기록이 있으면 {@link QuizParticipation#submit(String, Boolean)} 도메인 메서드로 업데이트하고,
     * 없으면 신규 INSERT한다.</p>
     *
     * @param quiz       참여한 퀴즈 엔티티
     * @param userId     사용자 ID (String FK)
     * @param quizId     퀴즈 ID (기존 기록 조회용)
     * @param userAnswer 사용자가 제출한 답변
     * @param isCorrect  채점 결과
     */
    private void saveOrUpdateParticipation(Quiz quiz, String userId, Long quizId,
                                            String userAnswer, boolean isCorrect) {
        participationRepository
                .findByQuiz_QuizIdAndUserId(quizId, userId)
                .ifPresentOrElse(
                        // 기존 기록 있음 — 재제출: submit() 도메인 메서드로 업데이트
                        existing -> {
                            log.debug("퀴즈 재제출 — 기존 참여 기록 업데이트: userId={}, quizId={}", userId, quizId);
                            existing.submit(userAnswer, isCorrect);
                        },
                        // 기존 기록 없음 — 신규 참여 INSERT
                        () -> {
                            log.debug("퀴즈 최초 참여 — 신규 기록 저장: userId={}, quizId={}", userId, quizId);
                            QuizParticipation participation = QuizParticipation.builder()
                                    .quiz(quiz)
                                    .userId(userId)
                                    .selectedOption(userAnswer)
                                    .isCorrect(isCorrect)
                                    .submittedAt(LocalDateTime.now())
                                    .build();
                            participationRepository.save(participation);
                        }
                );
    }

    /**
     * QUIZ_CORRECT 리워드를 지급한다.
     *
     * <p>{@link RewardService#grantReward}는 REQUIRES_NEW 독립 트랜잭션으로 실행되므로
     * 리워드 지급 실패가 퀴즈 참여 기록 저장 롤백을 유발하지 않는다.
     * 참조 키는 "quiz_{quizId}" 형식으로 사용하여 퀴즈별 지급 이력 추적을 가능하게 한다.</p>
     *
     * @param userId 리워드를 받을 사용자 ID
     * @param quizId 정답을 맞춘 퀴즈 ID
     */
    private void grantQuizReward(String userId, Long quizId) {
        try {
            rewardService.grantReward(
                    userId,
                    "QUIZ_CORRECT",         // reward_policy.action_type 매핑값
                    "quiz_" + quizId,       // 퀴즈별 참조 키 (중복 지급 방지 세션 키)
                    0                       // overrideAmount=0: reward_policy의 points_amount 사용
            );
            log.info("퀴즈 리워드 지급 완료: userId={}, quizId={}", userId, quizId);
        } catch (Exception e) {
            // 리워드 지급 실패는 퀴즈 제출 성공에 영향을 주지 않는다 (warn 로그만 기록)
            log.warn("퀴즈 리워드 지급 실패 (퀴즈 제출은 정상 처리): userId={}, quizId={}, error={}",
                    userId, quizId, e.getMessage());
        }
    }

    /**
     * Quiz.options JSON 문자열을 List&lt;String&gt;으로 파싱한다.
     *
     * <p>options 컬럼에는 JSON 배열 형태로 선택지가 저장된다.
     * 예: {@code ["선택지A", "선택지B", "선택지C", "선택지D"]}
     * 파싱 실패(null, 빈 문자열, malformed JSON) 시 빈 리스트를 반환하고 warn 로그를 남긴다.</p>
     *
     * @param optionsJson options 컬럼의 JSON 문자열 (nullable)
     * @return 파싱된 선택지 목록 (실패 시 빈 리스트)
     */
    private List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(optionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("quiz.options JSON 파싱 실패 — 빈 목록으로 fallback. json={}, error={}",
                    optionsJson, e.getMessage());
            return Collections.emptyList();
        }
    }
}
