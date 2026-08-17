package com.example.emotiondiary.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 매 HTTP 요청마다 traceId 를 생성해 MDC 에 주입하는 필터.
 * - 클라이언트가 X-Trace-Id 헤더를 보내면 그 값을 사용 (분산 트레이싱 상황 대비)
 * - 없으면 새 UUID 를 생성
 * - 응답 헤더에도 실어 클라이언트가 "이 요청 어떻게 됐나" 를 되물을 수 있게 함
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // Spring Security 필터보다 먼저 실행되도록
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId = (incoming != null && !incoming.isBlank())
                ? incoming
                : UUID.randomUUID().toString().substring(0, 8);   // 짧은 8자리

        try {
            MDC.put(TRACE_ID_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);

            // 진단용 — MDC.put 직후 로그
            log.info("TraceIdFilter ▶ MDC.get(traceId) = {}, thread = {}",
                    MDC.get(TRACE_ID_KEY), Thread.currentThread().getName());

            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);     // 스레드 풀에서 다음 요청에 누수되지 않도록 필수
        }
    }
}