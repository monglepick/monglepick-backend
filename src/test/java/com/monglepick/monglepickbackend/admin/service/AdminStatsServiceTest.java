package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.StatsDto.OverviewResponse;
import com.monglepick.monglepickbackend.admin.dto.StatsDto.TrendsResponse;
import com.monglepick.monglepickbackend.domain.community.entity.PostStatus;
import com.monglepick.monglepickbackend.domain.community.mapper.PostMapper;
import com.monglepick.monglepickbackend.domain.review.mapper.ReviewMapper;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminStatsService} 서비스 통계 단위 테스트.
 *
 * <p>회귀 배경:</p>
 * <ul>
 *   <li>서비스 통계 KPI 카드가 기간 필터(7/30/90일)와 분리되어 움직이지 않던 문제</li>
 *   <li>신규 가입 카드와 차트의 집계 기준이 달라 같은 기간인데 값이 맞지 않던 문제</li>
 *   <li>일별 집계가 자정 경계에서 중복될 수 있던 문제</li>
 * </ul>
 *
 * <p>회귀 차단:</p>
 * <ul>
 *   <li>{@link AdminStatsService#getOverview(String)} 가 기간별 일간 추이 합계/최댓값으로 KPI 를 계산하는지</li>
 *   <li>{@link AdminStatsService#getTrends(String)} 가 KST 기준 정확한 날짜 개수만 반환하는지</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Mock
    private UserMapper userMapper;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private AdminStatsService adminStatsService;

    @Test
    @DisplayName("getOverview(period) 는 선택 기간의 일별 추이 기준으로 DAU/MAU/신규 가입을 계산한다")
    void getOverview_alignsKpisWithSelectedPeriod() {
        when(userMapper.countByLastLoginAtBetween(any(), any()))
                .thenReturn(12L, 8L, 15L, 11L, 9L, 10L, 14L);
        when(userMapper.countByCreatedAtBetween(any(), any()))
                .thenReturn(1L, 0L, 2L, 0L, 1L, 3L, 2L);
        when(reviewMapper.count()).thenReturn(128L);
        when(reviewMapper.findAverageRating()).thenReturn(4.356d);
        when(postMapper.countByStatus(eq(PostStatus.PUBLISHED.name()), isNull())).thenReturn(42L);

        OverviewResponse response = adminStatsService.getOverview("7d");

        assertThat(response.dau()).isEqualTo(15L);
        assertThat(response.mau()).isEqualTo(79L);
        assertThat(response.newUsersWeek()).isEqualTo(9L);
        assertThat(response.newUsers()).isEqualTo(9L);
        assertThat(response.totalReviews()).isEqualTo(128L);
        assertThat(response.avgRating()).isEqualTo(4.36d);
        assertThat(response.totalPosts()).isEqualTo(42L);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userMapper, times(7)).countByLastLoginAtBetween(startCaptor.capture(), endCaptor.capture());

        List<LocalDateTime> starts = startCaptor.getAllValues();
        List<LocalDateTime> ends = endCaptor.getAllValues();
        LocalDate today = LocalDate.now(KST);

        assertThat(starts).isSorted();
        assertThat(starts.getFirst()).isEqualTo(today.minusDays(6).atStartOfDay());
        assertThat(ends.getLast()).isEqualTo(today.plusDays(1).atStartOfDay());
    }

    @Test
    @DisplayName("getTrends(period) 는 KST 기준 기간 길이만큼 날짜 오름차순 추이를 반환한다")
    void getTrends_returnsExactlySelectedPeriod() {
        when(userMapper.countByLastLoginAtBetween(any(), any())).thenReturn(3L);
        when(userMapper.countByCreatedAtBetween(any(), any())).thenReturn(1L);
        when(reviewMapper.countByCreatedAtBetween(any(), any())).thenReturn(2L);
        when(postMapper.countByStatusAndCreatedAtBetween(eq(PostStatus.PUBLISHED.name()), any(), any()))
                .thenReturn(4L);

        TrendsResponse response = adminStatsService.getTrends("30d");

        LocalDate today = LocalDate.now(KST);

        assertThat(response.trends()).hasSize(30);
        assertThat(response.trends().getFirst().date()).isEqualTo(today.minusDays(29).format(DATE_FMT));
        assertThat(response.trends().getLast().date()).isEqualTo(today.format(DATE_FMT));
        assertThat(response.trends())
                .allSatisfy(item -> {
                    assertThat(item.dau()).isEqualTo(3L);
                    assertThat(item.newUsers()).isEqualTo(1L);
                    assertThat(item.reviews()).isEqualTo(2L);
                    assertThat(item.posts()).isEqualTo(4L);
                });
    }
}
