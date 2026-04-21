package com.monglepick.monglepickbackend.domain.search.entity;

/**
 * 월드컵 후보 산정 방식.
 *
 * <p>CATEGORY는 관리자 큐레이션 후보 풀에서 선택하고,
 * GENRE는 사용자가 고른 장르를 모두 만족하는 movies 후보군에서 선택한다.</p>
 */
public enum WorldcupSourceType {
    CATEGORY,
    GENRE
}
