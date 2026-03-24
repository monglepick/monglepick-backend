package com.monglepick.monglepickbackend.domain.reward.controller;

import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.AttendanceResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.AttendanceStatusResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.BalanceResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.CheckRequest;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.CheckResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.DeductRequest;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.DeductResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.EarnRequest;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.EarnResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.ExchangeResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.HistoryResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.PointItemResponse;
import com.monglepick.monglepickbackend.domain.reward.service.AttendanceService;
import com.monglepick.monglepickbackend.domain.reward.service.PointItemService;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 포인트 컨트롤러 — 포인트 시스템 REST API 엔드포인트.
 *
 * <p>AI Agent(monglepick-agent) 내부 호출과 클라이언트(monglepick-client) 호출을
 * 모두 처리한다. 모든 응답은 DTO를 직접 반환한다 (ApiResponse 래퍼 미사용).</p>
 *
 * <h3>엔드포인트 목록</h3>
 * <table>
 *   <tr><th>메서드</th><th>경로</th><th>용도</th><th>호출자</th></tr>
 *   <tr><td>POST</td><td>/api/v1/point/check</td><td>잔액 확인</td><td>Agent</td></tr>
 *   <tr><td>POST</td><td>/api/v1/point/deduct</td><td>포인트 차감</td><td>Agent</td></tr>
 *   <tr><td>POST</td><td>/api/v1/point/earn</td><td>포인트 획득</td><td>내부 서비스</td></tr>
 *   <tr><td>GET</td><td>/api/v1/point/balance</td><td>잔액 조회</td><td>Agent + 클라이언트</td></tr>
 *   <tr><td>GET</td><td>/api/v1/point/history</td><td>변동 이력</td><td>클라이언트</td></tr>
 *   <tr><td>POST</td><td>/api/v1/point/attendance</td><td>출석 체크</td><td>클라이언트</td></tr>
 *   <tr><td>GET</td><td>/api/v1/point/attendance/status</td><td>출석 현황</td><td>클라이언트</td></tr>
 *   <tr><td>GET</td><td>/api/v1/point/items</td><td>아이템 목록</td><td>클라이언트</td></tr>
 *   <tr><td>POST</td><td>/api/v1/point/items/{itemId}/exchange</td><td>아이템 교환</td><td>클라이언트</td></tr>
 * </table>
 *
 * <h3>Agent 연동 주의사항</h3>
 * <p>Agent의 {@code point_client.py}는 응답 JSON을 직접 파싱한다.
 * 따라서 CheckResponse, DeductResponse의 필드명을 변경하면 Agent가 동작하지 않는다.</p>
 * <ul>
 *   <li>CheckResponse: {@code {allowed, balance, cost, message, maxInputLength}}</li>
 *   <li>DeductResponse: {@code {success, balanceAfter, transactionId}}</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>현재는 인증 없이 접근 가능하다. 향후 Agent 호출은 X-Service-Key 헤더,
 * 클라이언트 호출은 JWT 인증을 적용할 예정이다.</p>
 */
@RestController
@RequestMapping("/api/v1/point")
@Slf4j
@RequiredArgsConstructor
public class PointController {

    /** 포인트 서비스 (잔액 확인/차감/획득/이력 비즈니스 로직) */
    private final PointService pointService;

    /** 출석 체크 서비스 (출석/연속 출석/보너스 포인트 지급) */
    private final AttendanceService attendanceService;

    /** 포인트 아이템 서비스 (아이템 조회/교환) */
    private final PointItemService pointItemService;

    // ──────────────────────────────────────────────
    // Agent 내부 호출 엔드포인트
    // ──────────────────────────────────────────────

    /**
     * 포인트 잔액 확인 (Agent → Backend).
     *
     * <p>AI Agent가 추천 실행 전에 사용자의 포인트 잔액이 충분한지 확인한다.
     * 잔액 부족이더라도 200 OK로 응답하며, {@code allowed=false}로 구분한다.</p>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * POST /api/v1/point/check
     * Content-Type: application/json
     *
     * {"userId": "user123", "cost": 10}
     * }</pre>
     *
     * <h4>응답 예시</h4>
     * <pre>{@code
     * {"allowed": true, "balance": 100, "cost": 10, "message": "포인트가 충분합니다.", "maxInputLength": 200}
     * }</pre>
     *
     * @param request 잔액 확인 요청 (userId, cost)
     * @return 200 OK + CheckResponse (잔액 충분 여부, 등급별 글자 수 제한 포함)
     */
    @PostMapping("/check")
    public ResponseEntity<CheckResponse> checkPoint(@Valid @RequestBody CheckRequest request) {
        log.debug("POST /api/v1/point/check — userId={}, cost={}", request.userId(), request.cost());

        CheckResponse response = pointService.checkPoint(request.userId(), request.cost());
        return ResponseEntity.ok(response);
    }

    /**
     * 포인트 차감 (Agent → Backend).
     *
     * <p>AI Agent가 추천 완료 후 포인트를 차감한다. 비관적 락으로 동시성을 보장한다.
     * 잔액 부족 시 {@code InsufficientPointException}이 발생하며,
     * {@code GlobalExceptionHandler}가 적절한 에러 응답을 생성한다.</p>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * POST /api/v1/point/deduct
     * Content-Type: application/json
     *
     * {"userId": "user123", "amount": 10, "sessionId": "sess-abc", "description": "AI 추천 사용"}
     * }</pre>
     *
     * <h4>성공 응답 (200 OK)</h4>
     * <pre>{@code
     * {"success": true, "balanceAfter": 90, "transactionId": 42}
     * }</pre>
     *
     * @param request 차감 요청 (userId, amount, sessionId, description)
     * @return 200 OK + DeductResponse (차감 후 잔액, 이력 ID)
     * @throws com.monglepick.monglepickbackend.global.exception.InsufficientPointException 잔액 부족 시
     * @throws com.monglepick.monglepickbackend.global.exception.BusinessException          포인트 레코드 없음
     */
    @PostMapping("/deduct")
    public ResponseEntity<DeductResponse> deductPoint(@Valid @RequestBody DeductRequest request) {
        log.info("POST /api/v1/point/deduct — userId={}, amount={}, sessionId={}",
                request.userId(), request.amount(), request.sessionId());

        DeductResponse response = pointService.deductPoint(
                request.userId(),
                request.amount(),
                request.sessionId(),
                request.description()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 포인트 획득 (내부 서비스 호출).
     *
     * <p>출석 체크, 퀴즈 보상, 이벤트 보너스 등 내부 서비스에서
     * 포인트를 지급할 때 호출한다. 등급 재계산이 자동으로 수행된다.</p>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * POST /api/v1/point/earn
     * Content-Type: application/json
     *
     * {"userId": "user123", "amount": 50, "pointType": "earn", "description": "출석 체크 보상"}
     * }</pre>
     *
     * <h4>성공 응답 (200 OK)</h4>
     * <pre>{@code
     * {"balanceAfter": 150, "grade": "BRONZE"}
     * }</pre>
     *
     * @param request 획득 요청 (userId, amount, pointType, description, referenceId)
     * @return 200 OK + EarnResponse (획득 후 잔액, 등급)
     * @throws com.monglepick.monglepickbackend.global.exception.BusinessException 포인트 레코드 없음
     */
    @PostMapping("/earn")
    public ResponseEntity<EarnResponse> earnPoint(@Valid @RequestBody EarnRequest request) {
        log.info("POST /api/v1/point/earn — userId={}, amount={}, type={}",
                request.userId(), request.amount(), request.pointType());

        EarnResponse response = pointService.earnPoint(
                request.userId(),
                request.amount(),
                request.pointType(),
                request.description(),
                request.referenceId()
        );
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // 내부 + 클라이언트 겸용 엔드포인트
    // ──────────────────────────────────────────────

    /**
     * 포인트 잔액 조회 (Agent + 클라이언트 겸용).
     *
     * <p>사용자의 현재 포인트 잔액, 등급, 누적 획득 포인트를 조회한다.
     * 포인트 레코드가 없으면 기본값(잔액 0, BRONZE, 누적 0)을 반환한다.</p>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * GET /api/v1/point/balance?userId=user123
     * }</pre>
     *
     * <h4>응답 예시</h4>
     * <pre>{@code
     * {"balance": 100, "grade": "BRONZE", "totalEarned": 200}
     * }</pre>
     *
     * @param userId 사용자 ID
     * @return 200 OK + BalanceResponse (잔액, 등급, 누적 획득)
     */
    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(@RequestParam String userId) {
        log.debug("GET /api/v1/point/balance — userId={}", userId);

        BalanceResponse response = pointService.getBalance(userId);
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // 클라이언트 전용 엔드포인트
    // ──────────────────────────────────────────────

    /**
     * 포인트 변동 이력 조회 (클라이언트 전용).
     *
     * <p>사용자의 포인트 변동 내역을 최신순으로 페이징 조회한다.
     * 클라이언트의 "포인트 내역" 화면에서 사용된다.</p>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * GET /api/v1/point/history?userId=user123&page=0&size=20
     * }</pre>
     *
     * <h4>응답 예시</h4>
     * <pre>{@code
     * {
     *   "content": [
     *     {"id": 42, "pointChange": -10, "pointAfter": 90, "pointType": "spend", "description": "AI 추천 사용", "createdAt": "..."},
     *     {"id": 41, "pointChange": 50, "pointAfter": 100, "pointType": "earn", "description": "출석 체크 보상", "createdAt": "..."}
     *   ],
     *   "totalElements": 15,
     *   "totalPages": 1,
     *   ...
     * }
     * }</pre>
     *
     * @param userId 사용자 ID
     * @param page   페이지 번호 (0-based, 기본값 0)
     * @param size   페이지 크기 (기본값 20)
     * @return 200 OK + Page of HistoryResponse (변동 이력 페이지)
     */
    @GetMapping("/history")
    public ResponseEntity<Page<HistoryResponse>> getHistory(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 페이지 크기 상한 100으로 제한 (대량 조회 방지)
        int safeSize = Math.min(size, 100);
        log.debug("GET /api/v1/point/history — userId={}, page={}, size={}", userId, page, safeSize);

        Page<HistoryResponse> response = pointService.getHistory(userId, PageRequest.of(page, safeSize));
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // 출석 체크 엔드포인트 (클라이언트 전용)
    // ──────────────────────────────────────────────

    /**
     * 출석 체크 수행 (클라이언트 전용).
     *
     * <p>사용자의 오늘 출석을 기록하고, 연속 출석일(streak)에 따른
     * 보너스 포인트를 지급한다. 하루에 한 번만 출석할 수 있으며,
     * 이미 출석한 경우 409 Conflict 에러를 반환한다.</p>
     *
     * <h4>보너스 정책</h4>
     * <ul>
     *   <li>기본 출석 (1~6일): 10P</li>
     *   <li>7일 연속: 30P (기본 10P + 보너스 20P)</li>
     *   <li>30일 연속: 60P (기본 10P + 보너스 50P)</li>
     * </ul>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * POST /api/v1/point/attendance?userId=user123
     * }</pre>
     *
     * <h4>성공 응답 (200 OK)</h4>
     * <pre>{@code
     * {"checkDate": "2026-03-23", "streakCount": 7, "earnedPoints": 30, "currentBalance": 230}
     * }</pre>
     *
     * <h4>중복 출석 에러 (409 Conflict)</h4>
     * <pre>{@code
     * {"code": "P003", "message": "오늘 이미 출석했습니다"}
     * }</pre>
     *
     * @param userId 사용자 ID
     * @return 200 OK + AttendanceResponse (출석일, 연속일수, 획득 포인트, 잔액)
     * @throws com.monglepick.monglepickbackend.global.exception.BusinessException 이미 출석한 경우 (ALREADY_ATTENDED)
     */
    @PostMapping("/attendance")
    public ResponseEntity<AttendanceResponse> checkIn(@RequestParam String userId) {
        // TODO: JWT 구현 후 @AuthenticationPrincipal로 userId 추출로 변경
        log.info("POST /api/v1/point/attendance — userId={}", userId);

        AttendanceResponse response = attendanceService.checkIn(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 출석 현황 조회 (클라이언트 전용).
     *
     * <p>사용자의 현재 연속 출석일, 총 출석일, 오늘 출석 여부,
     * 이번 달 출석 날짜 목록을 조회한다. 클라이언트의 출석 체크 화면에서
     * 캘린더 및 현황 요약 표시에 사용된다.</p>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * GET /api/v1/point/attendance/status?userId=user123
     * }</pre>
     *
     * <h4>응답 예시</h4>
     * <pre>{@code
     * {
     *   "currentStreak": 7,
     *   "totalDays": 42,
     *   "checkedToday": true,
     *   "monthlyDates": ["2026-03-01", "2026-03-02", "2026-03-03", ...]
     * }
     * }</pre>
     *
     * @param userId 사용자 ID
     * @return 200 OK + AttendanceStatusResponse (연속일수, 총일수, 오늘출석여부, 월간날짜목록)
     */
    @GetMapping("/attendance/status")
    public ResponseEntity<AttendanceStatusResponse> getAttendanceStatus(@RequestParam String userId) {
        // TODO: JWT 구현 후 @AuthenticationPrincipal로 userId 추출로 변경
        log.debug("GET /api/v1/point/attendance/status — userId={}", userId);

        AttendanceStatusResponse response = attendanceService.getStatus(userId);
        return ResponseEntity.ok(response);
    }

    // ──────────────────────────────────────────────
    // 포인트 아이템 엔드포인트 (클라이언트 전용)
    // ──────────────────────────────────────────────

    /**
     * 포인트 아이템 목록 조회 (클라이언트 전용).
     *
     * <p>활성 상태인 포인트 아이템 목록을 조회한다.
     * category 파라미터가 있으면 해당 카테고리만 필터링하고,
     * 없으면 전체 활성 아이템을 반환한다.</p>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * GET /api/v1/point/items                    ← 전체 아이템
     * GET /api/v1/point/items?category=ai        ← AI 카테고리만
     * GET /api/v1/point/items?category=coupon    ← 쿠폰 카테고리만
     * }</pre>
     *
     * <h4>응답 예시</h4>
     * <pre>{@code
     * [
     *   {"itemId": 1, "name": "AI 추천 1회", "description": "AI 영화 추천 1회 이용권", "price": 50, "category": "ai"},
     *   {"itemId": 2, "name": "프로필 꾸미기", "description": "특별 프로필 프레임", "price": 100, "category": "avatar"}
     * ]
     * }</pre>
     *
     * @param category 아이템 카테고리 (선택, 예: "general", "coupon", "avatar", "ai")
     * @return 200 OK + 아이템 목록 (가격 오름차순, 없으면 빈 리스트)
     */
    @GetMapping("/items")
    public ResponseEntity<List<PointItemResponse>> getItems(
            @RequestParam(required = false) String category) {
        log.debug("GET /api/v1/point/items — category={}", category);

        if (category != null) {
            return ResponseEntity.ok(pointItemService.getItemsByCategory(category));
        }
        return ResponseEntity.ok(pointItemService.getActiveItems());
    }

    /**
     * 포인트 아이템 교환 (클라이언트 전용).
     *
     * <p>사용자의 보유 포인트로 지정된 아이템을 교환(구매)한다.
     * 아이템의 가격만큼 포인트가 차감되며, 잔액 부족 시 에러를 반환한다.</p>
     *
     * <h4>요청 예시</h4>
     * <pre>{@code
     * POST /api/v1/point/items/1/exchange?userId=user123
     * }</pre>
     *
     * <h4>성공 응답 (200 OK)</h4>
     * <pre>{@code
     * {"success": true, "balanceAfter": 150, "itemName": "AI 추천 1회"}
     * }</pre>
     *
     * <h4>에러 응답</h4>
     * <ul>
     *   <li>404 — 아이템 미존재 또는 비활성: {@code {"code": "P004", "message": "아이템을 찾을 수 없습니다"}}</li>
     *   <li>402 — 포인트 부족: {@code {"code": "P001", "message": "포인트가 부족합니다"}}</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @param itemId 교환 대상 아이템 ID
     * @return 200 OK + ExchangeResponse (성공 여부, 잔액, 아이템명)
     * @throws com.monglepick.monglepickbackend.global.exception.BusinessException 아이템 미존재/비활성
     * @throws com.monglepick.monglepickbackend.global.exception.InsufficientPointException 잔액 부족
     */
    @PostMapping("/items/{itemId}/exchange")
    public ResponseEntity<ExchangeResponse> exchangeItem(
            @RequestParam String userId,
            @PathVariable Long itemId) {
        // TODO: JWT 구현 후 @AuthenticationPrincipal로 userId 추출로 변경
        log.info("POST /api/v1/point/items/{}/exchange — userId={}", itemId, userId);

        ExchangeResponse response = pointItemService.exchangeItem(userId, itemId);
        return ResponseEntity.ok(response);
    }
}
