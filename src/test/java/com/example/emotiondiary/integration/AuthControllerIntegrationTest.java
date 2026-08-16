package com.example.emotiondiary.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("회원가입 → 로그인 E2E 통합 테스트")
@SpringBootTest
@ActiveProfiles("test") // application-test.yaml 활성화
@Transactional   // 테스트 종료 시 자동 롤백
class AuthControllerIntegrationTest {

    @Autowired WebApplicationContext ctx;
    @Autowired
    ObjectMapper om;

    private MockMvc mvc;

    void setup() {
        this.mvc = MockMvcBuilders.webAppContextSetup(ctx)
                .apply(springSecurity())   // Security 필터 체인 포함
                .build();
    }

    @Test
    @DisplayName("회원가입 후 같은 계정으로 로그인하면 accessToken 을 발급받는다")
    void signup_then_login_issuesToken() throws Exception {
        setup();

        ObjectNode signup = om.createObjectNode();
        signup.put("email", "test1@example.com");
        signup.put("password", "pass1234");
        signup.put("nickname", "길동이");

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(signup)))
                .andDo(print())
                .andExpect(status().isCreated());

        ObjectNode login = om.createObjectNode();
        login.put("email", "test1@example.com");
        login.put("password", "pass1234");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(login)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("비밀번호가 영문+숫자 패턴을 위반하면 400 VALIDATION_ERROR")
    void signup_invalidPassword_returns400() throws Exception {
        setup();

        ObjectNode req = om.createObjectNode();
        req.put("email", "bad@example.com");
        req.put("password", "onlyletters");   // 숫자 없음 → 패턴 위반
        req.put("nickname", "홍길동");

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}