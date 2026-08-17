# emotiondiary

Spring Boot 기반 감정 일기 프로젝트를 구현하고, 같은 프로젝트 위에서 테스트·성능·관측성을 단계적으로 학습한 기록입니다.

## 학습 목차

### Ⅰ. 애플리케이션 구현

1. [Spring Boot 개발 환경 구성](docs/application/01-development-environment.md)
   - 프로젝트 생성과 기본 개발 환경 구성
2. [로컬 DB 설정과 민감 정보 분리](docs/application/02-local-database-config.md)
   - MariaDB 연결과 환경 변수를 이용한 민감 정보 관리
3. [Diary CRUD 구현](docs/application/03-diary-crud.md)
   - 일기 등록·조회·수정·삭제 API 구현
4. [요청 검증과 전역 예외 처리](docs/application/04-validation-and-exception.md)
   - 입력값 검증과 일관된 오류 응답 구성
5. [User 도메인 추가](docs/application/05-user-domain.md)
   - 사용자 정보를 관리하는 도메인 구성
6. [Diary와 User의 다대일 관계 설정](docs/application/06-diary-user-relation.md)
   - JPA 연관관계를 이용한 일기 작성자 연결
7. [회원가입과 이메일 중복 검증](docs/application/07-signup-api.md)
   - 회원가입 API와 중복 회원 방지
8. [Spring Security와 BCrypt 적용](docs/application/08-spring-security.md)
   - 보안 설정과 비밀번호 암호화
9. [JWT 로그인과 토큰 발급](docs/application/09-jwt-login.md)
   - 로그인 검증과 Access Token 발급
10. [JWT 인증 필터와 사용자별 일기 접근](docs/application/10-jwt-authentication-filter.md)
    - 요청 인증과 작성자 기준의 일기 접근 제어
11. [Refresh Token 저장·회전·로그아웃](docs/application/11-refresh-token.md)
    - 토큰 재발급과 로그아웃 처리
12. [관리자 전용 API와 메서드 인가](docs/application/12-admin-authorization.md)
    - 역할을 기준으로 한 API 접근 제어
13. [인증·인가 오류 응답 표준화](docs/application/13-security-error-response.md)
    - Spring Security 오류 응답 형식 통일

### Ⅱ. 테스트·성능·관측성

1. [테스트 개요](docs/testing-observability/01-testing-overview.md)
   - 테스트의 목적과 피라미드, TDD·BDD·DDD의 개념
2. [JUnit 5](docs/testing-observability/02-junit5.md)
   - 테스트 생명주기, Assertion, 매개변수화 테스트
3. [Mockito](docs/testing-observability/03-mockito.md)
   - 외부 의존성을 격리한 Service 단위 테스트
4. [Spring Boot 통합 테스트](docs/testing-observability/04-spring-integration-test.md)
   - `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`를 이용한 계층별 검증
5. [성능 테스트 개요](docs/testing-observability/05-performance-test-overview.md)
   - TPS, p95, Error Rate와 부하 테스트 유형
6. [k6 부하 테스트](docs/testing-observability/06-k6-load-test.md)
   - 사용자 시나리오, VU, Threshold, Ramp-up 구성
7. [Prometheus와 Grafana](docs/testing-observability/07-prometheus-grafana.md)
   - Actuator 메트릭 수집과 시계열 대시보드 구성
8. [ELK와 traceId](docs/testing-observability/08-elk-trace-id.md)
   - 구조화 로그 수집과 요청 단위 추적
9. [프론트엔드 성능 최적화](docs/testing-observability/09-frontend-performance.md)
   - Lighthouse Before/After 측정과 Core Web Vitals, 정적 리소스 최적화
