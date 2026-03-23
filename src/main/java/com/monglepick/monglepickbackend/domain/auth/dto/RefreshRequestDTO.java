package com.monglepick.monglepickbackend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshRequestDTO {

        @NotBlank
        private String refreshToken;
}