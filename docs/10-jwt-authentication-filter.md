# 10. JWT 인증 필터와 사용자별 일기 접근

- 커밋: `0b531aa`
- 커밋 메시지: `feat: JWT 인증 필터 및 사용자별 일기 접근 적용`

## 이번 단계에서 한 일

이전 단계에서는 로그인에 성공하면 JWT를 발급했다.

하지만 토큰을 발급하는 것만으로는 Spring Security가 사용자를 자동으로 알아보지 못한다. 클라이언트가 보낸 JWT를 읽고, 검증하고, 현재 사용자의 인증 정보로 등록하는 과정이 필요하다.

이번 단계에서는 `JwtAuthenticationFilter`가 그 작업을 담당하도록 만들었다.

```text
클라이언트
└─ Authorization: Bearer <Access Token>
   ↓
JwtAuthenticationFilter
├─ Authorization 헤더 확인
├─ JWT 추출
├─ 서명과 만료 시간 검증
├─ 사용자 정보를 인증 객체로 변환
└─ SecurityContext에 인증 정보 저장
   ↓
DiaryController
└─ @AuthenticationPrincipal로 현재 사용자 조회
   ↓
DiaryService
└─ 현재 사용자 소유의 일기만 처리
```

## 변경된 파일

```text
src/main/java/com/example/emotiondiary/
├─ config/
│  └─ SecurityConfig.java
├─ controller/
│  └─ DiaryController.java
└─ security/
   ├─ dto/
   │  └─ CustomUserDetails.java
   ├─ jwt/
   │  └─ JwtAuthenticationFilter.java
   └─ service/
      └─ CustomUserDetailsService.java
```

## 전체 인증 흐름

로그인 이후 일기 목록을 요청한다고 가정해보자.

```http
GET /api/diaries?from=1&to=9999999999999
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

서버 내부에서는 다음 순서로 처리된다.

```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant F as JwtAuthenticationFilter
    participant P as MemberJwtTokenProvider
    participant S as SecurityContext
    participant D as DiaryController
    participant DS as DiaryService
    participant R as DiaryRepository

    C->>F: 요청 + Bearer Access Token
    F->>F: Authorization 헤더에서 JWT 추출
    F->>P: parse(token)
    P-->>F: Claims 반환
    F->>F: CustomUserDetails와 Authentication 생성
    F->>S: 인증 정보 저장
    F->>D: 다음 필터를 거쳐 요청 전달
    D->>S: @AuthenticationPrincipal 조회
    S-->>D: 현재 사용자 정보
    D->>DS: list(userId, from, to, sort)
    DS->>R: 사용자 ID가 포함된 조건으로 조회
    R-->>DS: 현재 사용자 소유 일기
    DS-->>C: 일기 목록 응답
```

## 필터란?

필터는 요청이 Controller에 도착하기 전에 실행되는 검사 단계다.

```text
HTTP 요청
→ 여러 Security Filter
→ Controller
→ Service
→ Repository
```

JWT 인증 필터는 요청 헤더에 담긴 토큰을 검사하여 요청을 보낸 사용자가 누구인지 알아낸다.

## JwtAuthenticationFilter

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
}
```

각 어노테이션의 역할은 다음과 같다.

```text
@Slf4j
└─ log.debug(), log.error() 등의 로그 기능 제공

@Component
└─ 객체를 Spring Bean으로 등록

@RequiredArgsConstructor
└─ final 필드를 받는 생성자를 Lombok이 생성

OncePerRequestFilter
└─ 하나의 HTTP 요청마다 한 번 실행되는 필터
```

### 주요 필드와 메서드

```text
JwtAuthenticationFilter
├─ HEADER: JWT를 찾을 요청 헤더 이름
├─ PREFIX: 토큰 앞에 붙는 인증 방식
├─ tokenProvider: JWT 생성·검증 담당
├─ doFilterInternal(): JWT 인증 처리
└─ resolveToken(): 헤더에서 JWT 문자열 추출
```

## Authorization 헤더

JWT는 다음 형식으로 요청 헤더에 담는다.

```http
Authorization: Bearer <token>
```

코드에서는 헤더 이름과 접두사를 상수로 관리한다.

```java
private static final String HEADER = "Authorization";
private static final String PREFIX = "Bearer ";
```

`Bearer` 뒤에는 반드시 공백이 한 칸 있어야 한다.

```text
올바른 형식
Bearer eyJhbGci...

잘못된 형식
BearereyJhbGci...
eyJhbGci...
```

`Bearer`는 이 문자열을 가진 사람이 인증 권한을 가진다는 뜻으로 사용하는 인증 방식이다. 따라서 토큰이 노출되지 않도록 주의해야 한다.

## resolveToken()

```java
private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HEADER);
    if (header != null && header.startsWith(PREFIX)) {
        return header.substring(PREFIX.length());
    }
    return null;
}
```

이 메서드는 `Authorization` 헤더에서 실제 JWT 부분만 꺼낸다.

```text
Authorization: Bearer abc.def.ghi
               └─────┘ └─────────┘
                PREFIX     JWT

substring(PREFIX.length())
→ abc.def.ghi
```

헤더가 없거나 `Bearer `로 시작하지 않으면 `null`을 반환한다.

## doFilterInternal()

`doFilterInternal()`은 JWT 인증 처리의 중심 메서드다.

### 1. 토큰 추출

```java
String token = resolveToken(request);
```

요청 헤더에 올바른 형식의 토큰이 있는지 확인한다.

### 2. 토큰 검증과 Claims 추출

```java
Claims claims = tokenProvider.parse(token);
```

`MemberJwtTokenProvider.parse()`는 다음 내용을 확인한다.

```text
JWT 검증
├─ 서버의 시크릿 키로 만든 서명인지
├─ 토큰 내용이 변조되지 않았는지
└─ 만료 시간이 지나지 않았는지
```

정상 토큰이면 Payload에 들어 있던 `Claims`를 반환한다.

### 3. 사용자 정보 추출

```java
Long userId = Long.valueOf(claims.getSubject());
String email = claims.get("email", String.class);
String role = claims.get("role", String.class);
```

로그인할 때 Access Token에 저장한 값을 다시 꺼내는 코드다.

```text
Claims
├─ sub   → 사용자 ID
├─ email → 이메일
└─ role  → USER 또는 ADMIN
```

`subject`는 문자열이므로 `Long.valueOf()`를 이용해 `Long`으로 바꾼다.

### 4. CustomUserDetails 생성

```java
CustomUserDetails principal =
        new CustomUserDetails(userId, email, "", role);
```

`principal`은 현재 인증된 사용자를 나타내는 객체다.

JWT 인증에서는 이 단계에서 비밀번호를 다시 검사하지 않는다. 로그인할 때 이미 비밀번호를 확인하고 토큰을 발급했기 때문에 빈 문자열을 넣는다.

### 5. Authentication 생성

```java
UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
```

이름에 `Token`이 들어가지만 여기서 말하는 Token은 JWT 문자열이 아니다. Spring Security가 사용하는 인증 정보 객체다.

```text
Authentication
├─ principal: 현재 사용자 정보
├─ credentials: 인증에 사용한 비밀번호 등의 정보
└─ authorities: 사용자의 권한 목록
```

`credentials`가 `null`인 이유는 인증 이후 비밀번호를 계속 보관할 필요가 없기 때문이다.

권한까지 전달하는 세 번째 생성자를 사용하면 Spring Security는 이 객체를 인증이 완료된 객체로 취급한다.

### 6. SecurityContext에 저장

```java
SecurityContextHolder.getContext().setAuthentication(auth);
```

`SecurityContext`는 현재 요청을 처리하는 동안 인증 정보를 보관하는 공간이다.

```text
SecurityContext
└─ Authentication
   └─ principal
      ├─ id
      ├─ email
      └─ role
```

이곳에 저장했기 때문에 이후 Controller에서 현재 사용자를 꺼낼 수 있다.

### 7. 다음 필터로 전달

```java
chain.doFilter(request, response);
```

JWT 처리가 끝나도 요청 처리는 계속되어야 한다. 이 코드는 다음 필터로 요청과 응답을 전달한다.

이 호출을 빠뜨리면 정상 요청도 Controller까지 도착하지 못한다.

## 토큰이 잘못된 경우

```java
catch (JwtException e) {
    log.debug("Invalid JWT: {}", e.getMessage());
    SecurityContextHolder.clearContext();
}
```

토큰이 만료되거나 위조되면 `parse()`가 `JwtException`을 발생시킨다.

필터는 오류를 로그에 남기고 인증 정보를 비운다. 그 후에도 `chain.doFilter()`를 호출한다.

```text
잘못된 JWT
→ JWT 인증 실패
→ SecurityContext 비우기
→ 인증되지 않은 상태로 다음 보안 단계 이동
→ 보호된 API라면 Spring Security가 접근 차단
```

이번 커밋에서는 만료 토큰과 위조 토큰의 응답 형식을 구분하지 않는다. 이후 단계에서 `AuthenticationEntryPoint`를 이용해 `ErrorResponse` 형식으로 통일한다.

## CustomUserDetails

Spring Security는 사용자 정보를 `UserDetails`라는 형식으로 다룬다.

프로젝트의 `User` 엔티티를 보안 코드에 직접 사용하지 않고 `CustomUserDetails`로 변환했다.

```java
public class CustomUserDetails implements UserDetails {
    private final Long id;
    private final String email;
    private final String password;
    private final String role;
}
```

### 필드와 메서드 정리

```text
CustomUserDetails
├─ id: 사용자 DB ID
├─ email: 로그인 이메일
├─ password: BCrypt로 저장된 비밀번호
├─ role: USER 또는 ADMIN
├─ from(): User 엔티티를 CustomUserDetails로 변환
├─ getAuthorities(): role을 Spring 권한 형식으로 변환
├─ getUsername(): email 반환
├─ isAccountNonExpired(): 계정 만료 여부
├─ isAccountNonLocked(): 계정 잠금 여부
├─ isCredentialsNonExpired(): 비밀번호 만료 여부
└─ isEnabled(): 계정 활성화 여부
```

### from()

```java
public static CustomUserDetails from(User user) {
    return new CustomUserDetails(
            user.getId(),
            user.getEmail(),
            user.getPassword(),
            user.getRole().name()
    );
}
```

DB에서 조회한 `User` 엔티티를 Spring Security가 이해하는 사용자 정보로 바꾼다.

### getAuthorities()

```java
return List.of(new SimpleGrantedAuthority("ROLE_" + role));
```

프로젝트의 `role` 값이 `USER`라면 Spring Security 권한은 `ROLE_USER`가 된다.

```text
USER  → ROLE_USER
ADMIN → ROLE_ADMIN
```

Spring Security에서 `hasRole("ADMIN")`은 내부적으로 `ROLE_ADMIN` 권한을 확인한다.

### 계정 상태 메서드

```java
isAccountNonExpired()
isAccountNonLocked()
isCredentialsNonExpired()
isEnabled()
```

현재는 모두 `true`를 반환한다. 즉 계정 만료, 잠금, 비밀번호 만료, 비활성화 기능을 아직 사용하지 않는다는 뜻이다.

나중에 `User`에 계정 상태 필드를 추가한다면 이 메서드에서 실제 값을 반환하도록 바꿀 수 있다.

## CustomUserDetailsService

```java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .map(CustomUserDetails::from)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + email));
    }
}
```

`UserDetailsService`는 사용자 이름을 받아 DB에서 사용자 정보를 조회하는 Spring Security 인터페이스다.

이 프로젝트에서는 사용자 이름 대신 이메일을 사용한다.

```text
email 입력
→ UserRepository.findByEmail(email)
→ User 엔티티 조회
→ CustomUserDetails로 변환
```

주의할 점은 이번 JWT 필터가 `CustomUserDetailsService`를 직접 호출하지 않는다는 것이다.

```text
현재 JwtAuthenticationFilter
JWT Claims에서 사용자 정보 추출
→ 요청마다 DB를 조회하지 않음
```

따라서 `CustomUserDetailsService`는 이번 인증 흐름에 만들어졌지만 실제 JWT 인증에는 아직 사용되지 않는다.

JWT의 장점인 빠른 인증에는 도움이 되지만, DB에서 사용자의 권한을 변경하거나 계정을 정지해도 이미 발급한 Access Token이 만료되기 전까지 이전 정보가 사용될 수 있다는 점을 기억해야 한다.

## SecurityConfig에 필터 등록

필터 클래스를 `@Component`로 등록하는 것만으로는 원하는 보안 필터 순서에 자동으로 들어가지 않는다. `SecurityFilterChain`에 명시적으로 추가해야 한다.

### 필터 주입

```java
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
}
```

`@RequiredArgsConstructor`가 생성자를 만들고 Spring이 `JwtAuthenticationFilter` Bean을 주입한다.

### 필터 순서 지정

```java
.addFilterBefore(
        jwtAuthenticationFilter,
        UsernamePasswordAuthenticationFilter.class
)
```

JWT 필터를 `UsernamePasswordAuthenticationFilter`보다 먼저 실행하라는 뜻이다.

```text
요청
→ JwtAuthenticationFilter
→ UsernamePasswordAuthenticationFilter 위치
→ 인가 검사
→ Controller
```

폼 로그인을 비활성화했더라도 이 필터 클래스를 기준점으로 사용해 JWT 필터의 위치를 정할 수 있다.

## permitAll과 authenticated

기존 Security 설정은 다음 정책을 사용한다.

```java
.requestMatchers(
        "/api/auth/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**"
).permitAll()
.anyRequest().authenticated()
```

```text
permitAll()
└─ 인증하지 않아도 접근 가능

authenticated()
└─ 인증된 사용자만 접근 가능
```

`/api/auth/login`과 `/api/auth/signup`은 토큰이 없어도 접근할 수 있다.

`/api/diaries`는 JWT 인증에 성공해 `SecurityContext`에 인증 정보가 있어야 접근할 수 있다.

`permitAll()` 경로도 Security Filter Chain 자체를 건너뛰는 것은 아니다. 필터는 실행될 수 있지만, 최종 인가 단계에서 인증을 필수로 요구하지 않는 것이다.

## DiaryController의 변경

이전에는 인증 기능이 완성되지 않아 모든 요청에 임시 사용자 ID를 사용했다.

```java
private static final Long TEMP_USER_ID = 1L;
```

이 방식은 누가 요청하더라도 항상 1번 사용자로 처리되는 문제가 있다.

이번 커밋에서는 임시 ID를 제거하고 실제 인증 사용자 정보를 받는다.

```java
@AuthenticationPrincipal CustomUserDetails principal
```

`@AuthenticationPrincipal`은 `SecurityContext`의 `Authentication` 안에 들어 있는 `principal`을 메서드 매개변수로 가져온다.

```text
JwtAuthenticationFilter
→ SecurityContext에 CustomUserDetails 저장
→ @AuthenticationPrincipal이 꺼냄
→ principal.getId()로 사용자 ID 사용
```

### 목록 조회

```java
diaryService.list(principal.getId(), from, to, sort);
```

### 단건 조회

```java
diaryService.getById(principal.getId(), id);
```

### 생성

```java
diaryService.create(principal.getId(), request);
```

### 수정

```java
diaryService.update(principal.getId(), id, request);
```

### 삭제

```java
diaryService.delete(principal.getId(), id);
```

모든 일기 API가 토큰에서 얻은 실제 사용자 ID를 Service에 전달한다.

## 사용자별 일기 접근이 보장되는 이유

Controller가 사용자 ID를 전달하는 것만으로는 충분하지 않다. Repository 조회 조건에도 사용자 ID가 들어가야 한다.

이전 단계에서 작성한 Repository 메서드는 이미 사용자 조건을 포함하고 있다.

```java
findByUser_IdAndDateBetweenOrderByDateDesc(...)
findByUser_IdAndDateBetweenOrderByDateAsc(...)
findByIdAndUser_Id(...)
```

예를 들어 2번 사용자가 1번 사용자의 일기 ID를 알아내 수정 요청을 보내도 다음 조건으로 조회한다.

```text
일기 ID = 요청한 ID
AND
일기의 사용자 ID = 현재 인증 사용자 ID 2
```

두 조건을 모두 만족하지 않으므로 다른 사용자의 일기를 가져오지 못한다.

```text
인증 사용자 ID
→ Controller
→ Service
→ Repository 조회 조건
→ 본인 소유 일기만 반환
```

이것을 소유권 검사라고 볼 수 있다.

단순히 로그인 여부만 검사하는 것과는 다르다.

```text
인증 검사
└─ 로그인한 사용자인가?

소유권 검사
└─ 이 일기가 현재 사용자의 것인가?
```

## 요청 결과 비교

### 정상 Access Token

```text
JWT 검증 성공
→ 인증 정보 생성
→ 일기 API 접근 허용
→ 현재 사용자의 일기만 처리
```

### 토큰 없음

```text
resolveToken()이 null 반환
→ 인증 정보가 만들어지지 않음
→ 보호된 일기 API 접근 차단
```

### 만료되거나 위조된 토큰

```text
parse()에서 JwtException
→ SecurityContext 초기화
→ 인증되지 않은 상태
→ 보호된 일기 API 접근 차단
```

### 다른 사용자의 일기 ID 요청

```text
JWT 인증은 성공
→ 사용자 ID가 포함된 Repository 조건으로 조회
→ 소유자가 다르면 조회되지 않음
→ DiaryNotFoundException
```

## Postman에서 테스트하기

### 1. 로그인

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "test1234"
}
```

응답의 `accessToken`을 복사한다.

### 2. 일기 목록 요청

```http
GET http://localhost:8080/api/diaries?from=0&to=9999999999999
Authorization: Bearer <복사한 accessToken>
```

Postman의 Authorization 탭을 사용한다면 Type을 `Bearer Token`으로 선택하고 토큰 문자열만 입력하면 된다.

### 3. 실패 상황 확인

다음 경우도 각각 테스트해본다.

```text
Authorization 헤더를 보내지 않기
Bearer 뒤의 토큰 한 글자 변경하기
Bearer와 토큰 사이 공백 제거하기
다른 사용자로 로그인한 토큰 사용하기
```

## 이번 코드에서 주의할 점

### 1. JWT Payload는 암호화되지 않는다

`email`과 `role`은 서명으로 변조를 방지하지만 누구나 디코딩하여 볼 수 있다. 비밀번호나 주민등록번호 같은 민감 정보는 JWT에 넣으면 안 된다.

### 2. Refresh Token을 인증에 사용하지 않도록 구분이 필요하다

현재 `parse()`는 서명과 만료 여부만 확인한다. Access Token인지 Refresh Token인지 구분하는 값은 검사하지 않는다.

Refresh Token에는 `email`과 `role`이 없지만 `sub`는 있기 때문에, 이후에는 토큰에 타입 클레임을 넣고 JWT 필터가 Access Token만 허용하도록 개선하는 것이 안전하다.

```text
Access Token  → API 인증에 사용
Refresh Token → Access Token 재발급에만 사용
```

### 3. JWT 정보와 DB 정보가 다를 수 있다

필터는 요청마다 DB를 조회하지 않고 JWT Claims를 신뢰한다. 사용자의 권한이 DB에서 변경되어도 기존 Access Token에는 이전 권한이 남아 있을 수 있다.

Access Token 만료 시간을 짧게 두는 이유 중 하나다.

### 4. 오류 응답 표준화는 아직 없다

이번 커밋은 잘못된 JWT를 잡아 인증 정보를 비우는 데까지만 처리한다. 토큰 없음, 만료, 위조를 서로 다른 `ErrorResponse`로 반환하는 기능은 뒤 단계에서 추가한다.

## 핵심 정리

```text
JwtAuthenticationFilter
├─ Authorization 헤더에서 Bearer Token 추출
├─ MemberJwtTokenProvider로 JWT 검증
├─ Claims에서 id, email, role 추출
├─ CustomUserDetails 생성
├─ Authentication 생성
└─ SecurityContext에 인증 정보 저장

CustomUserDetails
├─ Spring Security가 사용하는 현재 사용자 정보
├─ 프로젝트 사용자 ID와 이메일 보관
└─ USER를 ROLE_USER 형식의 권한으로 변환

SecurityConfig
└─ JWT 필터를 보안 필터 체인에 등록

DiaryController
├─ @AuthenticationPrincipal로 현재 사용자 조회
└─ 실제 사용자 ID를 DiaryService에 전달

DiaryService와 Repository
└─ 사용자 ID를 조회 조건에 포함하여 소유권 보호
```

이번 단계의 가장 중요한 변화는 다음 한 문장으로 정리할 수 있다.

> 클라이언트가 보낸 JWT를 Spring Security의 인증 정보로 바꾸고, 그 사용자 ID를 이용해 본인의 일기에만 접근하도록 연결했다.

## 복습 질문

1. 로그인 성공 후 JWT를 발급하는 것만으로 인증 처리가 끝나지 않는 이유는 무엇일까?
2. `OncePerRequestFilter`는 어떤 역할을 할까?
3. `resolveToken()`은 요청에서 무엇을 추출할까?
4. `SecurityContext`에는 어떤 객체가 저장될까?
5. `@AuthenticationPrincipal`은 사용자 정보를 어디에서 가져올까?
6. `USER` 역할 앞에 `ROLE_`을 붙이는 이유는 무엇일까?
7. 필터 마지막에 `chain.doFilter()`가 필요한 이유는 무엇일까?
8. 사용자 ID를 Controller에서만 확인하지 않고 Repository 조건에도 넣어야 하는 이유는 무엇일까?
9. 현재 JWT 필터가 요청마다 DB를 조회하지 않을 때 생기는 장단점은 무엇일까?
10. Access Token과 Refresh Token을 구분해서 검증해야 하는 이유는 무엇일까?
