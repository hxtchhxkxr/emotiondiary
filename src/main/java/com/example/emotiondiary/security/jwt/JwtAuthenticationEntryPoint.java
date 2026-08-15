package com.example.emotiondiary.security.jwt;

import com.example.emotiondiary.dto.ErrorResponse;
import com.example.emotiondiary.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String ATTR_ERROR_CODE = "auth.error.code";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErrorCode code = (ErrorCode) request.getAttribute(ATTR_ERROR_CODE);
        if (code == null) code = ErrorCode.UNAUTHORIZED;

        response.setStatus(code.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.builder()
                        .code(code.getCode())
                        .message(code.getDefaultMessage())
                        .build());
    }
}