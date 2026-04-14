package com.monglepick.monglepickbackend.global.constants;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 관리자 세부 역할(Role) 상수 enum — 2026-04-09 P2-⑫ 신규.
 *
 * <p>기존 {@code users.user_role} ({@link UserRole}) 은 {@code USER / ADMIN} 이진 구조로
 * "관리자 페이지 접근 권한" 만 제어했다. 그러나 실제 운영에서는 "게시판 모더레이션만 수행하는
 * 계정" / "결제 환불만 처리하는 재무 담당" / "고객센터 문의만 담당" 같이 업무별로 권한을
 * 세분화할 필요가 있다. 본 enum 은 {@code admin.admin_role} 컬럼(VARCHAR(50))에 저장되는
 * 세부 역할 값을 정의한다.</p>
 *
 * <h3>DB 구조와의 관계</h3>
 * <p>{@link com.monglepick.monglepickbackend.domain.user.entity.Admin#getAdminRole()} 은
 * {@code String} 타입을 유지한다. 이유는:</p>
 * <ul>
 *   <li><b>유연성</b>: 장기적으로 역할 추가 시 DDL 변경(컬럼 타입 변경) 없이 enum 상수만
 *       추가하면 된다. Java enum → DB enum 매핑은 한 번 결정하면 변경이 어렵다.</li>
 *   <li><b>하위 호환</b>: 기존 운영 DB 에 저장된 값을 파괴적으로 재해석하지 않는다. 본 enum 에
 *       정의되지 않은 레거시 값도 조회 자체는 가능해야 한다 (특히 마이그레이션 과도기).</li>
 * </ul>
 *
 * <p>그래서 본 enum 은 "저장 타입" 이 아니라 <b>허용값 검증 + 라벨 제공 + 유틸리티</b>
 * 역할만 수행한다. 엔티티 필드를 enum 으로 변경하는 것은 별도 이슈로 분리한다.</p>
 *
 * <h3>역할별 권한 매핑 (권고 — 강제 적용 아님)</h3>
 * <ul>
 *   <li>{@link #SUPER_ADMIN}: 모든 관리자 엔드포인트 접근. 관리자 계정 관리, 시스템 설정, 역할 변경 포함.</li>
 *   <li>{@link #ADMIN}: 일반 관리자. 관리자 계정 관리와 시스템 설정 제외한 모든 운영 기능.</li>
 *   <li>{@link #MODERATOR}: 게시판 관리(신고/혐오표현/게시글/리뷰/모더레이션 큐) 전담.</li>
 *   <li>{@link #FINANCE_ADMIN}: 결제·포인트·구독·환불·리워드 정책 전담.</li>
 *   <li>{@link #SUPPORT_ADMIN}: 고객센터(공지/FAQ/티켓/도움말) 전담.</li>
 *   <li>{@link #DATA_ADMIN}: 영화 데이터 마스터·장르·파이프라인 전담.</li>
 *   <li>{@link #AI_OPS_ADMIN}: AI 운영(퀴즈 생성/채팅 로그/모델 버전) 전담.</li>
 *   <li>{@link #STATS_ADMIN}: 통계/분석 전담 (대시보드/통계 탭 조회 전용).
 *       2026-04-14 추가 — 운영팀에서 쓰기 권한 없이 데이터만 확인해야 하는
 *       분석가/기획자 롤을 분리하기 위함.</li>
 * </ul>
 *
 * <h3>Spring Security 강제 적용은 별도 이슈</h3>
 * <p>현재 {@code SecurityConfig} 는 {@code /api/v1/admin/**} 에 {@code authenticated()} 만
 * 체크한다. 즉 본 enum 값이 저장·노출되더라도 엔드포인트 레벨 권한 차단(@PreAuthorize) 은
 * 적용되지 않는다. 이는 100+ 개 엔드포인트에 일괄 어노테이션을 다는 것이 리스크가 커서
 * 역할별 권한 매핑 정책을 먼저 문서화한 뒤 별도 PR 로 진행할 계획이다. 본 작업은
 * "역할 라벨링 인프라" 까지만 확보한다.</p>
 */
public enum AdminRole {

    /** 최고 관리자 — 모든 기능 + 관리자 계정 관리 + 시스템 설정 */
    SUPER_ADMIN("SUPER_ADMIN", "최고 관리자", "모든 기능 + 관리자 계정 관리 + 시스템 설정"),

    /** 일반 관리자 — 관리자 계정/시스템 설정 제외한 모든 운영 기능 */
    ADMIN("ADMIN", "일반 관리자", "관리자 계정·시스템 설정 제외 전체 운영 기능"),

    /** 모더레이터 — 게시판 관리 전담 (신고/혐오표현/게시글/리뷰/모더레이션 큐) */
    MODERATOR("MODERATOR", "모더레이터", "게시판 관리 전담 — 신고/혐오표현/게시글/리뷰"),

    /** 재무 관리자 — 결제·포인트·구독·환불·리워드 정책 */
    FINANCE_ADMIN("FINANCE_ADMIN", "재무 관리자", "결제·포인트·구독·환불·리워드 정책"),

    /** 고객센터 관리자 — 공지/FAQ/티켓/도움말 */
    SUPPORT_ADMIN("SUPPORT_ADMIN", "고객센터 관리자", "공지·FAQ·티켓·도움말"),

    /** 데이터 관리자 — 영화 마스터·장르·파이프라인 */
    DATA_ADMIN("DATA_ADMIN", "데이터 관리자", "영화 마스터·장르·데이터 파이프라인"),

    /** AI 운영 관리자 — 퀴즈 생성/채팅 로그/모델 버전 */
    AI_OPS_ADMIN("AI_OPS_ADMIN", "AI 운영 관리자", "AI 퀴즈 생성·채팅 로그·모델 버전 관리"),

    /**
     * 통계/분석 관리자 — 대시보드/통계 탭 조회 전용 (2026-04-14 추가).
     *
     * <p>쓰기 권한이 필요 없는 분석가/기획자 롤. 통계 12탭(개요·추이·추천·검색·행동·
     * 리텐션·매출·구독·포인트 경제·AI 서비스·커뮤니티·참여도·콘텐츠 성과·전환 퍼널·
     * 이탈 위험) 에 대한 접근만 허가하는 것이 권고 범위다. 실제 엔드포인트 레벨
     * 권한 차단은 {@code SecurityConfig @PreAuthorize} 적용 이후부터 강제된다.</p>
     */
    STATS_ADMIN("STATS_ADMIN", "통계/분석 관리자", "대시보드·통계·분석 탭 조회 전용");

    /** DB 에 저장되는 문자열 값 (정규화된 대문자 코드) */
    private final String code;

    /** 관리자 페이지에서 노출되는 한국어 라벨 */
    private final String koreanLabel;

    /** 해당 역할의 업무 범위 설명 — UI 툴팁/helper text 에 활용 */
    private final String description;

    AdminRole(String code, String koreanLabel, String description) {
        this.code = code;
        this.koreanLabel = koreanLabel;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getKoreanLabel() {
        return koreanLabel;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 주어진 문자열이 허용된 역할 코드인지 검사한다.
     *
     * <p>null / 빈 문자열 / 공백만 입력 / 정의되지 않은 값 모두 {@code false} 를 반환한다.
     * 대소문자 무시는 하지 않는다 — {@code "Admin"} 처럼 잘못된 케이스는 차단한다 (DB 일관성 유지).</p>
     *
     * @param raw 검사할 문자열 (nullable)
     * @return 허용값이면 true, 그 외 false
     */
    public static boolean isAllowed(String raw) {
        if (raw == null || raw.isBlank()) return false;
        return Arrays.stream(values())
                .map(AdminRole::getCode)
                .anyMatch(code -> Objects.equals(code, raw));
    }

    /**
     * 허용된 역할 코드 전체 목록을 반환한다 (문자열 리스트).
     *
     * <p>검증 실패 시 사용자에게 허용값을 안내할 때 사용한다.</p>
     *
     * @return 쉼표로 구분된 허용값 문자열 (예: "SUPER_ADMIN, ADMIN, MODERATOR, ...")
     */
    public static String allowedCodesAsString() {
        return Arrays.stream(values())
                .map(AdminRole::getCode)
                .collect(Collectors.joining(", "));
    }

    /**
     * 문자열로부터 {@link AdminRole} 을 안전하게 역변환한다.
     *
     * <p>정의되지 않은 값이면 {@code null} 을 반환한다 (예외 throw 하지 않음). 호출 측에서
     * null 체크 또는 {@link #isAllowed(String)} 을 먼저 사용하는 것을 권장한다.</p>
     *
     * @param raw 역변환할 코드 문자열
     * @return 일치하는 enum 또는 null
     */
    public static AdminRole fromCodeOrNull(String raw) {
        if (raw == null) return null;
        for (AdminRole role : values()) {
            if (role.code.equals(raw)) return role;
        }
        return null;
    }
}
