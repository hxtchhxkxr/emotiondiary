package com.example.emotiondiary.controller;

import com.example.emotiondiary.dto.DiaryListResponse;
import com.example.emotiondiary.dto.DiaryResponse;
import com.example.emotiondiary.security.dto.CustomUserDetails;
import com.example.emotiondiary.security.jwt.JwtAuthenticationFilter;
import com.example.emotiondiary.service.DiaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("DiaryController 슬라이스 테스트 (@WebMvcTest)")
@WebMvcTest(
        controllers = DiaryController.class, // Controller 한 개만 콕 집어 로드
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)  // JWT 필터 우회 — Controller 로직만 검증
class DiaryControllerSliceTest {

    @Autowired MockMvc mvc;
    @Autowired
    ObjectMapper om;

    @MockitoBean DiaryService diaryService;   // 실제 Service 가 아닌 가짜를 컨텍스트에 주입

    @Test
    @DisplayName("GET /api/diaries — Service 가 돌려준 리스트를 그대로 응답한다")
    void list_returnsServiceResult() throws Exception {
        DiaryResponse d1 = DiaryResponse.builder()
                .id("id-1").date(100L).content("첫째").emotionId(3).build();
        DiaryResponse d2 = DiaryResponse.builder()
                .id("id-2").date(200L).content("둘째").emotionId(5).build();

        when(diaryService.list(any(), any(Long.class), any(Long.class), anyString()))
                .thenReturn(DiaryListResponse.builder()
                        .items(List.of(d1, d2))
                        .total(2).build());

        mvc.perform(get("/api/diaries")
                        .param("from", "0")
                        .param("to", "999")
                        .with(user("u@example.com").roles("USER")))

                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].content").value("첫째"))
                .andExpect(jsonPath("$.items[1].emotionId").value(5));
    }

    @Test
    @DisplayName("POST /api/diaries — emotionId 범위 밖이면 400 (Bean Validation 이 Controller 앞에서 차단)")
    void create_invalidEmotionId_returns400() throws Exception {
        ObjectNode body = om.createObjectNode();
        body.put("date", 100L);
        body.put("content", "x");
        body.put("emotionId", 99);   // @Max(5) 위반

        mvc.perform(post("/api/diaries")
                        .with(user("u@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}