package com.monglepick.monglepickbackend.domain.user.entity;

import lombok.Getter;

@Getter
public enum SocialProviderType {

    NAVER("네이버"),
    GOOGLE("구글"),
    KAKAO("카카오");

    private final String description;

    SocialProviderType(String description) {
        this.description = description;
    }
}
