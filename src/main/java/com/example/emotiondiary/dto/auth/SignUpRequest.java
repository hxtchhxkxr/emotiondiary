package com.example.emotiondiary.dto.auth;

import com.example.emotiondiary.validation.UniqueEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequest {

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    @UniqueEmail // 위에서 만든 커스텀 검증 어노테이션
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, max = 32, message = "비밀번호는 8~32자입니다")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다")
    private String password;

    @NotBlank(message = "닉네임은 필수입니다")
    @Size(min = 2, max = 20)
    private String nickname;
}