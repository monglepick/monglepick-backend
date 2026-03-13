package com.monglepick.monglepickbackend.domain.auth.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User {

    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String userEmail;

    public CustomOAuth2User(Map<String, Object> attributes,
                            Collection<? extends GrantedAuthority> authorities,
                            String userEmail) {
        this.attributes = attributes;
        this.authorities = authorities;
        this.userEmail = userEmail;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return userEmail;
    }

}