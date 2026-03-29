package com.monglepick.monglepickbackend.admin.dto;

import java.util.Map;

/**
 * 시스템 관리 API DTO 모음.
 * - ServiceStatusResponse: 4개 서비스 헬스체크 집계
 * - SystemConfigResponse: 현재 설정값 조회 (읽기 전용)
 */
public class SystemDto {

    /**
     * 개별 서비스 헬스체크 결과.
     *
     * @param name         서비스 이름 (Spring Boot, AI Agent 등)
     * @param url          헬스체크 URL
     * @param connected    연결 성공 여부
     * @param status       상태 (up/down/slow)
     * @param responseTime 응답 시간 (ms)
     * @param uptime       업타임 문자열 (가능 시)
     */
    public record ServiceHealth(
            String name,
            String url,
            boolean connected,
            String status,
            Long responseTime,
            String uptime
    ) {}

    /**
     * 4개 서비스 헬스체크 집계 응답.
     */
    public record ServiceStatusResponse(
            ServiceHealth backend,
            ServiceHealth agent,
            ServiceHealth recommend,
            ServiceHealth nginx
    ) {}

    /**
     * 시스템 설정 항목.
     *
     * @param value       현재 값
     * @param description 설명
     */
    public record ConfigItem(
            String value,
            String description
    ) {}

    /**
     * 현재 설정값 조회 응답.
     */
    public record SystemConfigResponse(
            Map<String, ConfigItem> configs
    ) {}
}
