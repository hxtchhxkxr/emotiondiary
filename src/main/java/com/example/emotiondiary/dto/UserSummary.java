package com.example.emotiondiary.dto;

import com.example.emotiondiary.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserSummary {
    private Long id;
    private String email;
    private String nickname;
    private String role;

    public static UserSummary from(User u) {
        return UserSummary.builder()
                .id(u.getId())
                .email(u.getEmail())
                .nickname(u.getNickname())
                .role(u.getRole().name())
                .build();
    }
}