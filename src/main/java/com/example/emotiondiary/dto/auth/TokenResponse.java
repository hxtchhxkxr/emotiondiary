package com.example.emotiondiary.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;          // "Bearer"
    private long   accessTokenExpiresIn;  // seconds
}
