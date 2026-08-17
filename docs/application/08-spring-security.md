# 08. Spring Security와 비밀번호 암호화

- 커밋: `249a35c`
- 커밋 메시지: `feat: Spring Security 설정 및 비밀번호 암호화 적용`

## 이번 단계에서 한 일

Spring Security를 프로젝트에 추가하고 API별 접근 정책을 설정했다.

회원가입 비밀번호는 더 이상 평문으로 저장하지 않고 BCrypt 해시값으로 저장하도록 변경했다.

```text
Spring Security
├─ 요청이 Controller에 도착하기 전에 보안 검사
├─ 공개 API와 보호 API 구분
├─ 폼 로그인과 HTTP Basic 비활성화
├─ 서버 세션을 사용하지 않도록 설정
└─ 프론트엔드 요청을 위한 CORS 설정

비밀번호 보호
├─ BCryptPasswordEncoder 등록
├─ 회원가입 비밀번호 해시 생성
└─ users.password에 해시값 저장
```

## 변경된 파일

```text
emotiondiary/
├─ build.gradle
└─ src/main/java/com/example/emotiondiary/
   ├─ config/
   │  └─ SecurityConfig.java
   └─ service/
      └─ AuthService.java
```

## Spring Security란?

Spring Security는 Spring 애플리케이션의 인증과 인가를 담당하는 보안 라이브러리다.

```text
인증 Authentication
└─ 요청을 보낸 사용자가 누구인지 확인

인가 Authorization
└─ 해당 사용자가 이 기능을 사용할 권한이 있는지 확인
```

예를 들면:

```text
로그인 성공
→ 사용자가 누구인지 확인됨
→ 인증 성공

일반 사용자가 관리자 API 요청
→ 사용자는 확인됐지만 관리자 권한이 없음
→ 인가 실패
```

Spring Security는 요청이 Controller에 도착하기 전에 여러 필터를 거치게 한다.

```text
클라이언트 요청
→ Spring Security Filter Chain
→ 인증과 권한 검사
→ 통과하면 Controller
→ 실패하면 요청 차단
```

## 의존성 추가

`build.gradle`에 두 의존성을 추가했다.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
testImplementation 'org.springframework.security:spring-security-test'
```

### `spring-boot-starter-security`

Spring Security의 기본 기능을 프로젝트에 추가한다.

이 의존성만 추가해도 Spring Boot가 기본 보안 설정을 자동으로 적용한다.

```text
Security 의존성 발견
→ 기본 보안 필터 생성
→ 모든 요청을 보호
→ 인증되지 않은 요청 차단
```

기본 설정은 임시 사용자와 로그인 방식도 제공하지만, 이 프로젝트에서는 REST API와 JWT에 맞는 설정을 직접 작성한다.

### `spring-security-test`

Spring Security가 적용된 API를 테스트할 때 사용하는 기능을 제공한다.

테스트에서 임시 인증 사용자를 만들거나 권한을 지정할 수 있다.

```java
@WithMockUser(roles = "USER")
```

이번 커밋에서는 테스트 라이브러리만 추가했고 구체적인 보안 테스트는 아직 작성하지 않았다.

## SecurityConfig

```java
@Configuration
public class SecurityConfig {
}
```

Spring Security의 정책을 직접 설정하는 클래스다.

### `@Configuration`

이 클래스가 Spring 설정 클래스라는 뜻이다.

내부의 `@Bean` 메서드가 반환한 객체를 Spring이 관리한다.

```text
SecurityConfig
├─ PasswordEncoder Bean
├─ SecurityFilterChain Bean
└─ CorsConfigurationSource Bean
```

## Bean이란?

Bean은 Spring이 생성하고 관리하는 객체다.

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

이 설정으로 Spring은 `PasswordEncoder` 객체를 한 번 만들고, 필요한 클래스에 주입해준다.

```text
SecurityConfig에서 PasswordEncoder 생성
→ Spring 컨테이너에 Bean으로 등록
→ AuthService 생성자에 자동 주입
```

## BCryptPasswordEncoder

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

BCrypt는 비밀번호를 안전한 해시값으로 바꾸는 알고리즘이다.

```text
평문 비밀번호
test1234

BCrypt 해시
$2a$10$...
```

### 암호화가 아니라 해시

일반적인 암호화는 열쇠를 이용해 원래 값으로 복호화할 수 있다.

BCrypt 해시는 원래 비밀번호로 되돌리는 복호화 기능이 없다.

```text
test1234
→ BCrypt 해시 생성 가능

BCrypt 해시
→ test1234로 복호화 불가
```

로그인할 때는 해시를 복호화하지 않고 입력한 비밀번호가 해시와 일치하는지 비교한다.

```java
passwordEncoder.matches(rawPassword, encodedPassword);
```

이 비교 기능은 이후 로그인 API에서 사용한다.

## Salt

BCrypt는 해시를 만들 때 무작위 salt 값을 함께 사용한다.

따라서 같은 비밀번호를 여러 번 encode해도 결과가 매번 달라진다.

```text
encode("test1234") → $2a$10$abc...
encode("test1234") → $2a$10$xyz...
```

해시 문자열은 다르지만 두 값 모두 다음 비교에 성공할 수 있다.

```java
passwordEncoder.matches("test1234", encodedPassword);
```

같은 비밀번호를 사용하는 사용자들의 DB 값이 똑같이 보이지 않도록 해준다.

## Work Factor

BCrypt는 해시 계산을 일부러 느리게 만든다.

`new BCryptPasswordEncoder()`는 기본 strength를 사용하며, 일반적으로 생성된 해시 앞부분에서 비용 값을 확인할 수 있다.

```text
$2a$10$...
     └ 비용 값
```

비용 값이 높을수록 해시 계산이 더 오래 걸려 무차별 비밀번호 대입 공격을 어렵게 한다. 너무 높으면 정상적인 로그인도 느려지므로 적절한 값을 사용해야 한다.

## 회원가입 비밀번호 저장 변경

이전 코드는 요청으로 받은 비밀번호를 그대로 저장했다.

```java
User user = User.create(
        request.getEmail(),
        request.getPassword(),
        request.getNickname()
);
```

이번 커밋에서는 PasswordEncoder를 주입받았다.

```java
private final PasswordEncoder passwordEncoder;
```

`@RequiredArgsConstructor`가 생성자를 만들고 Spring이 등록된 BCryptPasswordEncoder를 주입한다.

저장할 때는 `encode()`를 호출한다.

```java
User user = User.create(
        request.getEmail(),
        passwordEncoder.encode(request.getPassword()),
        request.getNickname()
);
```

전체 흐름은 다음과 같다.

```text
회원가입 요청
→ 평문 비밀번호 test1234
→ passwordEncoder.encode()
→ BCrypt 해시 생성
→ User.password에 해시 저장
→ users 테이블에 INSERT
```

이제 DB를 조회해도 원래 비밀번호가 그대로 보이지 않는다.

## 기존 평문 사용자 데이터

이전 커밋에서 가입한 사용자의 비밀번호는 평문일 수 있다.

```text
기존 데이터: test1234
새 데이터: $2a$10$...
```

나중에 `passwordEncoder.matches()`로 로그인할 때 기존 평문 값은 정상적인 BCrypt 해시가 아니므로 비교할 수 없다.

학습용 프로젝트에서는 기존 사용자를 삭제하고 새 회원가입 요청을 보내는 방식으로 다시 만들 수 있다.

```sql
DELETE FROM diary;
DELETE FROM users;
ALTER TABLE users AUTO_INCREMENT = 1;
```

User가 Diary의 외래키로 연결되어 있으므로 Diary를 먼저 삭제한다.

운영 서비스에서는 사용자를 삭제하지 않고 별도의 비밀번호 전환 정책을 세워야 한다.

## SecurityFilterChain

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http)
        throws Exception {
}
```

HTTP 요청이 Controller에 도달하기 전에 거치는 보안 필터와 규칙을 설정한다.

```text
HTTP 요청
→ CORS 처리
→ CSRF 처리
→ 인증 처리
→ 인가 처리
→ Controller
```

Spring Boot가 제공하는 기본 보안 설정 대신 이 프로젝트에서 작성한 `SecurityFilterChain`을 사용한다.

여기서 설정을 덮어쓴다는 것은 Spring Security 코드를 수정한다는 뜻이 아니다. 자동으로 제공되던 기본 정책 대신 개발자가 등록한 Bean의 정책을 사용한다는 뜻이다.

## CSRF 비활성화

```java
.csrf(csrf -> csrf.disable())
```

CSRF는 브라우저가 쿠키를 자동으로 전송하는 성질을 악용해 사용자가 원하지 않는 요청을 보내게 만드는 공격이다.

이 프로젝트는 이후 쿠키 기반 세션 대신 Authorization 헤더의 JWT를 사용하려고 한다.

```http
Authorization: Bearer JWT
```

브라우저가 이 헤더를 자동으로 넣어주지 않기 때문에 일반적인 Bearer Token 기반 REST API 구성에서는 CSRF를 비활성화할 수 있다.

다만 나중에 인증 정보를 쿠키에 저장하고 브라우저가 자동 전송하도록 변경한다면 CSRF 보호 여부를 다시 검토해야 한다.

## CORS 설정 적용

```java
.cors(cors ->
        cors.configurationSource(corsConfigurationSource())
)
```

아래에서 정의한 CORS 정책을 Spring Security에 적용한다.

## 폼 로그인 비활성화

```java
.formLogin(form -> form.disable())
```

Spring Security가 제공하는 HTML 로그인 페이지를 사용하지 않는다.

이 프로젝트는 프론트엔드가 JSON으로 로그인 요청을 보내는 REST API를 만들 예정이므로 별도의 로그인 화면이 필요 없다.

## HTTP Basic 비활성화

```java
.httpBasic(basic -> basic.disable())
```

HTTP Basic 인증을 사용하지 않는다.

HTTP Basic은 요청마다 다음과 같은 헤더로 사용자 이름과 비밀번호를 보낸다.

```http
Authorization: Basic base64값
```

Base64는 암호화가 아니기 때문에 값을 다시 확인할 수 있다. HTTPS를 사용하면 전송 구간은 보호되지만, 이 프로젝트는 ID와 비밀번호를 매 요청마다 보내는 대신 JWT를 사용할 계획이므로 HTTP Basic을 비활성화한다.

## 세션 미사용

```java
.sessionManagement(session ->
        session.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS
        )
)
```

`STATELESS`는 서버가 로그인 상태를 HTTP 세션에 저장하지 않는다는 뜻이다.

세션 방식은 일반적으로 다음처럼 동작한다.

```text
로그인
→ 서버에 세션 저장
→ 클라이언트가 세션 ID 전송
→ 서버가 저장된 세션 확인
```

JWT 방식은 다음처럼 동작한다.

```text
로그인
→ 서버가 JWT 발급
→ 클라이언트가 JWT 보관
→ 요청마다 JWT 전송
→ 서버가 JWT 검증
```

이 커밋에서는 JWT 필터가 아직 없지만, 이후 JWT 인증을 사용하기 위해 미리 Stateless 정책을 설정했다.

## URL별 접근 권한

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(
                "/api/auth/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**"
        ).permitAll()
        .anyRequest().authenticated()
)
```

### `requestMatchers()`

같은 접근 규칙을 적용할 URL을 선택한다.

```text
/api/auth/**
└─ 회원가입과 이후 로그인 API

/swagger-ui/**
/swagger-ui.html
/v3/api-docs/**
└─ API 문서 확인 경로
```

`**`는 뒤에 어떤 하위 경로가 와도 포함한다는 뜻이다.

```text
/api/auth/signup
/api/auth/login
/api/auth/reissue
```

### `permitAll()`

```java
.permitAll()
```

선택한 경로는 인증 없이 누구나 접근할 수 있다.

회원가입과 로그인은 아직 인증되지 않은 사용자도 접근할 수 있어야 한다.

### `authenticated()`

```java
.anyRequest().authenticated()
```

앞에서 공개하지 않은 나머지 모든 요청은 인증이 필요하다.

```text
/api/auth/signup → 공개
/swagger-ui/**   → 공개
/api/diaries     → 인증 필요
그 밖의 요청     → 인증 필요
```

URL 규칙은 위에서부터 순서대로 확인하기 때문에 구체적인 공개 경로를 먼저 작성하고 마지막에 전체 규칙을 작성한다.

## 현재 보호 API를 호출하면 어떻게 될까?

이 커밋에서는 폼 로그인과 HTTP Basic을 껐고 JWT 인증 필터는 아직 만들지 않았다.

따라서 `/api/diaries`처럼 보호된 API에 인증 정보를 넣을 방법이 아직 없다.

```text
GET /api/diaries
→ Spring Security 필터
→ 인증 정보 없음
→ authenticated() 조건 실패
→ 요청 차단
```

현재 기본 처리에 따라 403 응답 등이 나타날 수 있다. 다음 JWT 커밋들에서 토큰 발급과 인증 필터를 추가하면 보호된 API를 호출할 수 있게 된다.

## CORS란?

CORS는 브라우저에서 서로 다른 출처 사이의 요청을 허용할지 결정하는 규칙이다.

출처는 프로토콜, 호스트, 포트의 조합이다.

```text
프론트엔드: http://localhost:5173
백엔드:     http://localhost:8080
```

포트가 다르므로 브라우저는 서로 다른 출처로 판단한다.

백엔드가 허용하지 않으면 브라우저가 응답 사용을 차단할 수 있다.

## 허용할 출처

```java
config.setAllowedOrigins(
        List.of("http://localhost:5173")
);
```

프론트엔드 개발 서버인 `http://localhost:5173`의 요청을 허용한다.

운영 배포 시에는 실제 프론트엔드 주소로 변경해야 한다.

## 허용할 HTTP 메서드

```java
config.setAllowedMethods(
        List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
);
```

각 메서드의 의미는 다음과 같다.

```text
GET     → 조회
POST    → 생성
PUT     → 수정
DELETE  → 삭제
OPTIONS → 브라우저의 사전 요청
```

브라우저는 실제 요청 전에 서버가 해당 요청을 허용하는지 `OPTIONS`로 확인할 수 있다. 이를 Preflight 요청이라고 한다.

## 허용할 요청 헤더

```java
config.setAllowedHeaders(List.of("*"));
```

프론트엔드에서 보내는 모든 요청 헤더를 허용한다.

이후 JWT를 보낼 `Authorization` 헤더도 포함된다.

## 노출할 응답 헤더

```java
config.setExposedHeaders(
        List.of("Authorization")
);
```

브라우저 JavaScript가 응답의 `Authorization` 헤더를 읽을 수 있게 허용한다.

일반적인 응답 헤더 중 일부는 별도 설정 없이 읽을 수 있지만, `Authorization` 같은 헤더는 노출 설정이 필요할 수 있다.

## 인증 정보 포함 허용

```java
config.setAllowCredentials(true);
```

브라우저 요청에서 쿠키나 인증 관련 정보를 포함할 수 있도록 허용한다.

`allowCredentials(true)`를 사용할 때는 허용 출처를 `*`로 열기보다 현재 코드처럼 구체적인 출처를 지정하는 것이 중요하다.

현재 프로젝트는 JWT를 Authorization 헤더로 보낼 계획이므로 실제 쿠키 사용 여부에 맞춰 이 설정은 이후 다시 검토할 수 있다.

## CORS 적용 경로

```java
source.registerCorsConfiguration(
        "/api/**",
        config
);
```

작성한 CORS 정책을 `/api/**` 경로에 적용한다.

```text
/api/auth/signup → 적용
/api/diaries     → 적용
/swagger-ui      → 이 CORS 설정 대상 아님
```

## 회원가입 요청 흐름

```text
프론트엔드에서 POST /api/auth/signup
→ CORS 정책 확인
→ /api/auth/**는 permitAll
→ AuthController.signup()
→ SignUpRequest 검증
→ AuthService.signup()
→ 비밀번호 BCrypt encode
→ User Entity 생성
→ users 테이블에 해시 저장
→ 201 Created
```

## Security 설정 전과 후

### 적용 전

```text
모든 API
→ 별도의 보안 검사 없이 Controller 접근 가능

회원가입 비밀번호
→ 평문 저장
```

### 적용 후

```text
공개 API
→ 인증 없이 접근 가능

보호 API
→ 인증이 있어야 접근 가능

회원가입 비밀번호
→ BCrypt 해시 저장
```

## 이번 단계의 한계

이번 커밋은 보안 정책과 비밀번호 해시 저장을 준비한 단계다.

아직 다음 기능은 없다.

- 로그인 API
- Access Token과 Refresh Token 발급
- JWT 검증
- SecurityContext에 사용자 인증 저장
- 실제 로그인 사용자 ID로 일기 처리
- 인증·인가 실패의 JSON 응답 통일

또한 `/api/auth/**` 전체를 공개했기 때문에 이후 로그아웃처럼 인증이 필요한 인증 관련 경로가 추가되면 URL 규칙을 더 구체적으로 나누는 것이 안전하다.

## 이번 단계 요약

```text
Spring Security와 BCrypt
├─ Spring Security 의존성 추가
├─ SecurityFilterChain 직접 설정
├─ CSRF 비활성화
├─ CORS 설정
├─ 폼 로그인 비활성화
├─ HTTP Basic 비활성화
├─ Stateless 세션 정책
├─ 공개 경로와 보호 경로 구분
├─ BCryptPasswordEncoder Bean 등록
└─ 회원가입 비밀번호를 해시로 저장
```

이번 커밋을 통해 REST API와 JWT 인증을 위한 보안 기본 구조를 만들고, 사용자 비밀번호를 평문이 아닌 BCrypt 해시로 안전하게 저장하게 됐다.
