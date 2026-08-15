package com.example.emotiondiary.config;

import com.example.emotiondiary.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 설정 클래스
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 비밀번호 암호화에 사용할 인코더 빈 등록
     *
     * - BCrypt: 단방향 해시 알고리즘 (복호화 불가)
     * - 같은 비밀번호라도 매번 다른 해시값 생성 (salt 내장)
     * - 회원가입 시 암호화, 로그인 시 비교에 사용됨
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Spring Security의 핵심 설정 - HTTP 보안 필터 체인 구성
     *
     * SecurityFilterChain: 요청이 컨트롤러에 도달하기 전에 거치는 보안 필터들의 묶음
     * 요청 → [CORS 필터] → [CSRF 필터] → [인증 필터] → [인가 필터] → 컨트롤러
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화
                // - CSRF: 브라우저가 쿠키를 자동 전송하는 것을 악용한 공격
                // - REST API는 쿠키 대신 JWT 토큰을 사용하므로 CSRF 방어가 불필요
                .csrf(csrf -> csrf.disable())

                // CORS 설정 적용 (아래 corsConfigurationSource() 메서드 참조)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 폼 로그인 비활성화 (REST API이므로 로그인 페이지 불필요)
                .formLogin(form -> form.disable())

                // HTTP Basic 인증 비활성화 (ID/PW를 헤더에 평문으로 보내는 방식 → 보안 취약)
                .httpBasic(basic -> basic.disable())

                // 세션 미사용 설정
                // - STATELESS: 서버가 세션을 생성하지 않음
                // - JWT 기반 인증에서는 토큰 자체에 인증 정보가 담겨 있으므로 세션 불필요
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // URL별 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",       // 인증 관련 (로그인, 회원가입) → 누구나 접근 가능
                                "/swagger-ui/**",     // Swagger UI 페이지
                                "/swagger-ui.html",   // Swagger UI 진입점
                                "/v3/api-docs/**"     // Swagger API 문서 JSON
                        ).permitAll()                 // 위 경로는 인증 없이 허용
                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요 (JWT 토큰 필수)
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }

    /**
     * CORS (Cross-Origin Resource Sharing) 설정
     *
     * 브라우저는 보안상 다른 출처(origin)로의 요청을 기본 차단함
     * 예) 프론트엔드(localhost:5173) → 백엔드(localhost:8080) 요청 시 CORS 에러 발생
     * 이 설정으로 허용할 출처, 메서드, 헤더를 명시적으로 지정
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 허용할 출처 (프론트엔드 개발 서버 주소)
        config.setAllowedOrigins(List.of("http://localhost:5173"));

        // 허용할 HTTP 메서드
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 허용할 요청 헤더 (* = 모든 헤더)
        config.setAllowedHeaders(List.of("*"));

        // 프론트엔드에서 읽을 수 있도록 노출할 응답 헤더
        // → 로그인 후 응답의 Authorization 헤더에서 JWT 토큰을 꺼낼 수 있게 함
        config.setExposedHeaders(List.of("Authorization"));

        // 쿠키/인증 정보 포함 허용 (withCredentials: true 요청 허용)
        config.setAllowCredentials(true);

        // /api/** 경로에만 위 CORS 설정 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}