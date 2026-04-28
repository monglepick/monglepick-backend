package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.constants.UserItemStatus;
import com.monglepick.monglepickbackend.domain.reward.entity.PointItem;
import com.monglepick.monglepickbackend.domain.reward.entity.UserItem;
import com.monglepick.monglepickbackend.domain.reward.repository.PointItemRepository;
import com.monglepick.monglepickbackend.domain.reward.repository.UserItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 등급 자동 칭호 지급 서비스 (2026-04-28 신규, B안 v3.5 꾸미기 6슬롯 확장).
 *
 * <p>6등급 팝콘 시스템(NORMAL → BRONZE → SILVER → GOLD → PLATINUM → DIAMOND) 달성 시,
 * 해당 등급에 대응하는 칭호({@code TITLE_GENERIC}) PointItem 을 자동으로 user_items 에 INSERT 한다.
 * 운영자가 운영 중 칭호명/이미지를 자유롭게 갱신할 수 있도록 칭호 데이터는 {@code point_items} 테이블의
 * 시드 행으로 관리하며, 본 서비스는 이름으로 매핑하여 멱등 지급한다.</p>
 *
 * <h3>지급 정책</h3>
 * <ul>
 *   <li><b>발급 시점</b>: {@link RewardService#processReward} 의 ⑩ 단계(등급 승격 감지)에서 호출.</li>
 *   <li><b>멱등성</b>: 이미 ACTIVE/EQUIPPED 로 동일 칭호를 보유 중이면 INSERT 스킵.
 *       (EXPIRED/USED 는 보유로 보지 않으므로 재발급 가능 — 단 등급 칭호는 영구 무기한이라 사실상 발생 안 함.)</li>
 *   <li><b>회수 정책</b>: 등급 강등 시 칭호는 회수하지 않는다 (트로피 개념). 단, 강등된 사용자가
 *       하위 등급 칭호를 새로 착용하려 하면 가능. 일종의 "달성 기록" 으로 영구 보유.</li>
 *   <li><b>유효기간</b>: 영구 (durationDays=null → expires_at=null).</li>
 *   <li><b>source</b>: {@code "GRADE_AUTO"} — 분석/감사 추적용. EXCHANGE/REWARD/ADMIN 과 구분된다.</li>
 * </ul>
 *
 * <h3>등급 → 칭호 매핑 (PointItemInitializer 시드와 동기화)</h3>
 * <table border="1">
 *   <tr><th>등급</th><th>한글명</th><th>칭호 itemName</th></tr>
 *   <tr><td>NORMAL  </td><td>알갱이    </td><td>{@code "칭호 - 알갱이"}    </td></tr>
 *   <tr><td>BRONZE  </td><td>강냉이    </td><td>{@code "칭호 - 강냉이"}    </td></tr>
 *   <tr><td>SILVER  </td><td>팝콘      </td><td>{@code "칭호 - 팝콘"}      </td></tr>
 *   <tr><td>GOLD    </td><td>카라멜팝콘</td><td>{@code "칭호 - 카라멜팝콘"}</td></tr>
 *   <tr><td>PLATINUM</td><td>몽글팝콘  </td><td>{@code "칭호 - 몽글팝콘"}  </td></tr>
 *   <tr><td>DIAMOND </td><td>몽아일체  </td><td>{@code "칭호 - 몽아일체"}  </td></tr>
 * </table>
 *
 * <h3>예외 정책</h3>
 * <p>RewardService 호출 경로에서 본 서비스의 실패가 본 기능(리뷰/출석/구매 등) 에 영향을 주면 안 된다.
 * 따라서 모든 단일 처리는 try/catch 로 감싸 warn 로그만 남기고 swallow 한다 — 칭호 미지급은
 * 사용자에게 큰 문제가 아니지만 메인 트랜잭션이 롤백되는 건 심각한 회귀.</p>
 *
 * @see RewardService 등급 승격 감지 호출자
 * @see PointItem 칭호 PointItem 시드 (PointItemInitializer.buildCanonicalItems)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GradeTitleService {

    /** 등급 코드 → 칭호 PointItem itemName 매핑 (시드와 동기화 필수). */
    private static final Map<String, String> GRADE_TO_TITLE_NAME = Map.of(
            "NORMAL",   "칭호 - 알갱이",
            "BRONZE",   "칭호 - 강냉이",
            "SILVER",   "칭호 - 팝콘",
            "GOLD",     "칭호 - 카라멜팝콘",
            "PLATINUM", "칭호 - 몽글팝콘",
            "DIAMOND",  "칭호 - 몽아일체"
    );

    /** PointItem (시드) 조회용 */
    private final PointItemRepository pointItemRepository;

    /** UserItem (보유 인벤토리) 조회/저장용 */
    private final UserItemRepository userItemRepository;

    /**
     * 등급 승격 시 해당 등급의 칭호를 자동 지급한다 (멱등).
     *
     * <p>본 메서드는 {@code REQUIRES_NEW} 로 별도 트랜잭션을 연다. 호출자(RewardService) 의
     * 메인 트랜잭션과 분리되어, 칭호 INSERT 실패가 본 기능(리뷰/출석/구매) 롤백을 유발하지 않는다.
     * 동시에 칭호 INSERT 자체는 원자적이라 부분 실패 위험 없음.</p>
     *
     * @param userId    수령자 사용자 ID (VARCHAR(50))
     * @param gradeCode 승격된 등급 코드 (NORMAL/BRONZE/SILVER/GOLD/PLATINUM/DIAMOND)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantTitleForGrade(String userId, String gradeCode) {
        if (userId == null || userId.isBlank() || gradeCode == null) {
            log.debug("등급 칭호 지급 스킵 (인자 결손): userId={}, gradeCode={}", userId, gradeCode);
            return;
        }

        String titleName = GRADE_TO_TITLE_NAME.get(gradeCode);
        if (titleName == null) {
            /* 매핑되지 않은 등급 코드 — 시드 누락 또는 신규 등급 추가 미반영. 운영 모니터링 필요 */
            log.warn("등급 코드에 매핑된 칭호가 없음: userId={}, gradeCode={}", userId, gradeCode);
            return;
        }

        try {
            Optional<PointItem> titleItemOpt = pointItemRepository.findFirstByItemNameAndIsActiveTrue(titleName);
            if (titleItemOpt.isEmpty()) {
                /* PointItemInitializer 가 시드하지 않은 환경 — 부팅 순서 또는 비활성화 상태.
                   운영 환경에선 부팅 직후 즉시 발생하면 안 되며 발생 시 알람. */
                log.warn("등급 칭호 시드 미발견 (PointItem 비활성/누락): userId={}, gradeCode={}, expectedName={}",
                        userId, gradeCode, titleName);
                return;
            }
            PointItem titleItem = titleItemOpt.get();

            /* 멱등 — 이미 보유 중이면 스킵 (등급 강등 후 재승급 시 ACTIVE 1개 유지 보장) */
            boolean already = userItemRepository.existsActiveByUserAndPointItem(
                    userId, titleItem.getPointItemId());
            if (already) {
                log.debug("등급 칭호 이미 보유 중 — 중복 INSERT 스킵: userId={}, gradeCode={}, pointItemId={}",
                        userId, gradeCode, titleItem.getPointItemId());
                return;
            }

            /* INSERT — 무기한 보유 (durationDays=null → expires_at=null), source="GRADE_AUTO" */
            UserItem entity = UserItem.builder()
                    .userId(userId)
                    .pointItem(titleItem)
                    .acquiredAt(LocalDateTime.now())
                    .expiresAt(null)
                    .status(UserItemStatus.ACTIVE)
                    .source("GRADE_AUTO")
                    .remainingQuantity(1)
                    .build();
            UserItem saved = userItemRepository.save(entity);

            log.info("등급 칭호 자동 지급 완료: userId={}, gradeCode={}, titleName={}, userItemId={}",
                    userId, gradeCode, titleName, saved.getUserItemId());
        } catch (Exception e) {
            /* 메인 트랜잭션과 분리돼 있어 swallow 안전. 칭호 누락은 운영 보정 가능 (관리자 수동 지급). */
            log.warn("등급 칭호 자동 지급 실패 — 본 기능 영향 없음: userId={}, gradeCode={}, error={}",
                    userId, gradeCode, e.getMessage(), e);
        }
    }
}
