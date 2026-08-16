# 09. JWT 로그인과 토큰 발급

- 커밋: `c8fc80c`
- 커밋 메시지: `feat: JWT 로그인 및 토큰 발급 기능 추가`

## 이번 단계에서 한 일

이메일과 비밀번호로 로그인하는 API를 추가했다.

로그인 정보가 올바르면 서버가 Access Token과 Refresh Token을 만들어 응답한다.

```text
로그인 요청
→ 이메일로 사용자 조회
→ BCrypt로 비밀번호 비교
→ Access Token 생성
→ Refresh Token 생성
→ TokenResponse 반환
```

이번 커밋은 토큰을 발급하는 단계다. 요청의 토큰을 읽어 사용자를 인증하는 필터는 다음 커밋에서 추가한다.

## 변경된 파일

```text
emotiondiary/
├─ build.gradle
├─ src/main/resources/
│  └─ application.yaml
└─ src/main/java/com/example/emotiondiary/
   ├─ controller/
   │  └─ AuthController.java
   ├─ service/
   │  └─ AuthService.java
   ├─ dto/auth/
   │  ├─ LoginRequest.java
   │  └─ TokenResponse.java
   └─ security/jwt/
      └─ MemberJwtTokenProvider.java
```

## JWT란?

JWT는 JSON Web Token의 줄임말이다.

서버가 사용자를 확인했다는 정보를 담아 클라이언트에게 전달하는 문자열이다.

예시는 다음과 같다.

```text
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.서명값
```

JWT는 점을 기준으로 세 부분으로 나뉜다.

```text
Header.Payload.Signature
```

```text
Header
└─ 토큰 종류와 서명 알고리즘

Payload
└─ 사용자 ID, 이메일, 권한, 만료시간 같은 정보

Signature
└─ 토큰이 서버에서 만들어졌고 변조되지 않았는지 확인하는 서명
```

## Header

Header에는 보통 다음과 같은 정보가 들어간다.

```json
{
  "alg": "HS256"
}
```

`alg`는 토큰 서명에 사용한 알고리즘을 뜻한다.

이 프로젝트에서는 비밀키를 사용하는 HMAC 계열 알고리즘을 사용한다.

## Payload

Payload에는 토큰이 전달할 정보가 들어간다.

Access Token의 예시는 다음과 같다.

```json
{
  "sub": "1",
  "email": "test@example.com",
  "role": "USER",
  "iat": 1786840000,
  "exp": 1786841800
}
```

Payload는 암호화된 비밀 공간이 아니다. Base64 방식으로 표현되어 누구나 내용을 확인할 수 있다.

따라서 비밀번호, 주민등록번호, 비밀키 같은 민감한 값은 JWT에 넣으면 안 된다.

## Signature

Signature는 Header와 Payload가 변조되지 않았는지 검사할 때 사용한다.

```text
Header + Payload
→ 서버 비밀키로 서명
→ Signature 생성
```

누군가 Payload의 role을 `USER`에서 `ADMIN`으로 바꾸면 기존 Signature와 맞지 않게 된다.

```text
토큰 내용 변경
→ 서명 검증 실패
→ 잘못된 토큰으로 처리
```

Signature는 Payload를 숨기는 기능이 아니라 변조 여부를 확인하는 기능이다.

## Claim이란?

Claim은 JWT Payload에 들어 있는 각각의 정보다.

### 표준 Claim

```text
sub
└─ Subject, 토큰의 주인

iat
└─ Issued At, 토큰 생성 시각

exp
└─ Expiration, 토큰 만료 시각
```

### 커스텀 Claim

프로젝트에서 직접 정한 정보다.

```text
email
└─ 사용자 이메일

role
└─ USER 또는 ADMIN 권한
```

## JWT 라이브러리 추가

`build.gradle`에 JJWT 의존성을 추가했다.

```groovy
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

### `jjwt-api`

코드에서 사용하는 JWT 생성과 파싱 인터페이스를 제공한다.

```java
Jwts.builder()
Jwts.parser()
```

### `jjwt-impl`

API가 실제로 동작하도록 내부 구현을 제공한다.

애플리케이션 실행 시 필요하므로 `runtimeOnly`로 추가했다.

### `jjwt-jackson`

JWT의 JSON 데이터를 만들고 읽는 데 Jackson을 사용하도록 연결한다.

## JWT 설정

`application.yaml`에 다음 설정을 추가했다.

```yaml
jwt:
  member:
    secret: ${JWT_MEMBER_SECRET}
    access-exp-min: 30
    refresh-exp-min: 1440
```

`jwt`는 `spring` 내부가 아니라 최상위에 작성한다.

```text
spring:
  ...

jwt:
  ...
```

코드에서 `${jwt.member.secret}` 경로로 읽기 때문이다.

## JWT Secret

```yaml
secret: ${JWT_MEMBER_SECRET}
```

JWT를 만들고 검증할 때 사용할 비밀키를 환경변수에서 가져온다.

비밀키를 아는 서버만 올바른 Signature를 만들 수 있다.

```text
토큰 생성
→ 비밀키로 서명

토큰 검증
→ 같은 비밀키로 서명 확인
```

실제 비밀키를 Git에 올라가는 `application.yaml`에 직접 작성하면 안 된다.

로컬에서는 Git에서 제외된 `application-local.yaml` 또는 환경변수에 작성한다.

```yaml
jwt:
  member:
    secret: 32바이트이상의충분히긴로컬비밀키
```

HS256 계열 키는 최소 256비트, 즉 32바이트 이상이어야 한다.

영문과 숫자처럼 UTF-8에서 1바이트인 문자만 사용한다면 32자 이상으로 이해하기 쉽다. 한글은 문자 수와 바이트 수가 다르므로 실제 바이트 길이를 기준으로 생각해야 한다.

## 토큰 만료시간

```yaml
access-exp-min: 30
refresh-exp-min: 1440
```

이름의 `min`은 분 단위를 뜻한다.

```text
Access Token  → 30분
Refresh Token → 1,440분 = 24시간 = 1일
```

설명이나 요구사항이 15분과 14일이라면 다음처럼 설정해야 한다.

```yaml
access-exp-min: 15
refresh-exp-min: 20160
```

```text
14일 × 24시간 × 60분 = 20,160분
```

이 문서는 실제 커밋의 설정인 Access 30분, Refresh 1일을 기준으로 설명한다.

## Access Token과 Refresh Token

두 토큰은 역할이 다르다.

### Access Token

인증이 필요한 API를 호출할 때 사용한다.

```http
Authorization: Bearer AccessToken
```

유효시간을 짧게 설정해 탈취됐을 때 사용할 수 있는 시간을 줄인다.

이 프로젝트의 Access Token에는 다음 정보가 들어간다.

```text
userId
email
role
생성 시각
만료 시각
```

### Refresh Token

Access Token이 만료됐을 때 새로운 Access Token을 발급받기 위해 사용한다.

Access Token보다 긴 유효시간을 가진다.

이 프로젝트의 Refresh Token에는 최소 정보인 사용자 ID와 시간 정보만 들어간다.

```text
userId
생성 시각
만료 시각
```

```text
Access Token 만료
→ Refresh Token을 서버에 전달
→ 서버가 Refresh Token 검증
→ 새 Access Token 발급
```

이번 커밋에서는 Refresh Token을 생성해서 반환할 뿐, 재발급 API나 DB 저장 기능은 아직 없다.

## LoginRequest

```java
@Getter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
```

로그인 요청 JSON을 받는 DTO다.

요청 예시는 다음과 같다.

```json
{
  "email": "test@example.com",
  "password": "test1234"
}
```

### 이메일 검증

```java
@NotBlank
@Email
```

- 이메일이 비어 있지 않은지 확인한다.
- 이메일 형식이 올바른지 확인한다.

### 비밀번호 검증

```java
@NotBlank
```

비밀번호가 비어 있지 않은지만 확인한다.

회원가입 때처럼 길이와 조합을 다시 검사하지 않는 이유는 이미 가입된 사용자가 입력한 값이 저장된 비밀번호와 일치하는지만 확인하면 되기 때문이다.

## TokenResponse

로그인 성공 시 반환할 DTO다.

```java
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long accessTokenExpiresIn;
}
```

응답 예시는 다음과 같다.

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 30
}
```

각 필드의 의미는 다음과 같다.

```text
accessToken
└─ API 인증에 사용할 토큰

refreshToken
└─ 토큰 재발급에 사용할 토큰

tokenType
└─ Authorization 헤더에 사용할 방식인 Bearer

accessTokenExpiresIn
└─ Access Token 만료까지 남은 시간
```

현재 코드의 주석은 `accessTokenExpiresIn`을 초 단위라고 설명하지만 실제로는 `getAccessExpMin()` 값을 그대로 넣어 분 값인 30을 반환한다.

초 단위 응답이 목적이라면 다음처럼 수정해야 한다.

```java
.accessTokenExpiresIn(tokenProvider.getAccessExpMin() * 60)
```

그 경우 30분은 1,800초로 반환된다.

## MemberJwtTokenProvider

```java
@Component
public class MemberJwtTokenProvider {
}
```

회원용 JWT를 만들고 검증하는 도구 클래스다.

`@Component`가 붙어 있으므로 Spring이 객체를 생성하고 AuthService에 주입해준다.

전체 역할은 다음과 같다.

```text
MemberJwtTokenProvider
├─ key: JWT 서명과 검증에 사용할 비밀키
├─ accessExpMin: Access Token 만료시간
├─ refreshExpMin: Refresh Token 만료시간
├─ createAccessToken(): Access Token 생성
├─ createRefreshToken(): Refresh Token 생성
├─ parse(): 토큰 검증 및 Claim 추출
└─ getUserId(): 토큰에서 사용자 ID 추출
```

## 설정값 주입

```java
public MemberJwtTokenProvider(
        @Value("${jwt.member.secret}") String secret,
        @Value("${jwt.member.access-exp-min}") long accessSec,
        @Value("${jwt.member.refresh-exp-min}") long refreshSec
)
```

`@Value`가 `application.yaml`의 값을 생성자 매개변수에 넣어준다.

```text
jwt.member.secret
→ secret

jwt.member.access-exp-min
→ accessSec

jwt.member.refresh-exp-min
→ refreshSec
```

`accessSec`, `refreshSec`라는 변수명은 초처럼 보이지만 실제 설정값은 분이다. 다음처럼 이름을 바꾸면 단위를 이해하기 쉽다.

```java
long accessExpMin
long refreshExpMin
```

## SecretKey 생성

```java
this.key = Keys.hmacShaKeyFor(
        secret.getBytes(StandardCharsets.UTF_8)
);
```

설정에서 읽은 문자열을 UTF-8 바이트로 바꾼 뒤 JWT 서명에 사용할 SecretKey 객체로 만든다.

```text
비밀키 문자열
→ UTF-8 바이트 배열
→ SecretKey
```

## Access Token 생성

```java
public String createAccessToken(User user)
```

먼저 현재 시각과 만료 시각을 계산한다.

```java
Date now = new Date();
Date exp = new Date(
        now.getTime() + accessExpMin * 60 * 1000
);
```

`Date.getTime()`은 밀리초 단위를 사용한다.

```text
분
× 60
→ 초
× 1,000
→ 밀리초
```

30분이라면 다음 값을 현재 시각에 더한다.

```text
30 × 60 × 1,000 = 1,800,000밀리초
```

다음으로 JWT를 만든다.

```java
return Jwts.builder()
        .subject(String.valueOf(user.getId()))
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .issuedAt(now)
        .expiration(exp)
        .signWith(key)
        .compact();
```

각 메서드의 역할은 다음과 같다.

```text
subject()
└─ sub에 사용자 ID 저장

claim("email", ...)
└─ 이메일 저장

claim("role", ...)
└─ USER 또는 ADMIN 저장

issuedAt()
└─ iat에 생성 시각 저장

expiration()
└─ exp에 만료 시각 저장

signWith()
└─ 비밀키로 서명

compact()
└─ 최종 JWT 문자열 생성
```

## Refresh Token 생성

```java
public String createRefreshToken(User user)
```

Refresh Token은 Access Token과 비슷하게 만들지만 이메일과 권한을 넣지 않는다.

```java
return Jwts.builder()
        .subject(String.valueOf(user.getId()))
        .issuedAt(now)
        .expiration(exp)
        .signWith(key)
        .compact();
```

토큰 재발급에는 사용자 ID만 있으면 되므로 필요한 정보만 담는다.

## 토큰 파싱과 검증

```java
public Claims parse(String token)
```

JWT 문자열을 검증한 뒤 Payload의 Claim을 반환한다.

```java
Jws<Claims> jws = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token);

return jws.getPayload();
```

처리 순서는 다음과 같다.

```text
JWT 문자열 받기
→ 같은 비밀키로 Signature 검증
→ 만료시간 확인
→ 검증 성공 시 Payload 반환
```

검증에 실패하면 예외가 발생한다.

```text
토큰 만료
→ ExpiredJwtException

서명 위조 또는 잘못된 형식
→ JwtException
```

현재 `catch` 블록은 예외를 잡은 뒤 그대로 다시 던진다.

```java
catch (ExpiredJwtException e) {
    throw e;
}
```

따라서 지금은 `try-catch`가 없어도 결과가 같다. 이후 만료 토큰과 잘못된 토큰을 서로 다른 ErrorCode로 변환할 때 활용할 수 있다.

## 사용자 ID 추출

```java
public Long getUserId(String token) {
    return Long.valueOf(parse(token).getSubject());
}
```

처리 순서는 다음과 같다.

```text
parse(token)으로 검증
→ sub Claim 가져오기
→ String을 Long으로 변환
→ 사용자 ID 반환
```

예를 들어 `sub`가 `"3"`이면 `3L`을 반환한다.

## AuthService 로그인

AuthService에 MemberJwtTokenProvider를 주입했다.

```java
private final MemberJwtTokenProvider tokenProvider;
```

로그인 메서드는 읽기 작업만 수행하므로 클래스의 `@Transactional(readOnly = true)` 설정을 사용한다.

```java
public TokenResponse login(LoginRequest request)
```

## 이메일로 사용자 조회

```java
User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() ->
                new BusinessException(
                        ErrorCode.INVALID_CREDENTIALS
                )
        );
```

이메일에 해당하는 사용자가 없으면 `INVALID_CREDENTIALS` 오류를 발생시킨다.

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 일치하지 않습니다"
}
```

사용자가 없다는 사실을 별도로 알려주지 않고 비밀번호 오류와 같은 메시지를 사용한다.

```text
이메일 없음
→ 이메일 또는 비밀번호 불일치

비밀번호 틀림
→ 이메일 또는 비밀번호 불일치
```

이렇게 하면 공격자가 어떤 이메일이 실제로 가입되어 있는지 확인하기 어려워진다.

## BCrypt 비밀번호 비교

```java
if (!passwordEncoder.matches(
        request.getPassword(),
        user.getPassword()
)) {
    throw new BusinessException(
            ErrorCode.INVALID_CREDENTIALS
    );
}
```

두 값의 의미는 다음과 같다.

```text
request.getPassword()
└─ 사용자가 입력한 평문 비밀번호

user.getPassword()
└─ DB에 저장된 BCrypt 해시
```

BCrypt 해시를 복호화하지 않고 `matches()`로 일치 여부를 확인한다.

```text
입력 비밀번호와 해시 일치
→ true
→ 로그인 계속

일치하지 않음
→ false
→ 401 INVALID_CREDENTIALS
```

## 토큰 응답 생성

이메일과 비밀번호가 모두 맞으면 두 토큰을 생성한다.

```java
return TokenResponse.builder()
        .accessToken(tokenProvider.createAccessToken(user))
        .refreshToken(tokenProvider.createRefreshToken(user))
        .tokenType("Bearer")
        .accessTokenExpiresIn(tokenProvider.getAccessExpMin())
        .build();
```

## AuthController 로그인 API

```java
@PostMapping("/login")
public ResponseEntity<TokenResponse> login(
        @Valid @RequestBody LoginRequest request
) {
    return ResponseEntity.ok(authService.login(request));
}
```

최종 API 주소는 다음과 같다.

```text
POST /api/auth/login
```

이 경로는 이전 SecurityConfig에서 `/api/auth/**`에 포함되어 있으므로 인증 없이 호출할 수 있다.

로그인에 성공하면 `200 OK`와 TokenResponse를 반환한다.

## 로그인 성공 흐름

```text
POST /api/auth/login
→ LoginRequest 생성
→ @Valid로 이메일과 비밀번호 검증
→ 이메일로 User 조회
→ BCrypt matches()로 비밀번호 비교
→ Access Token 생성
→ Refresh Token 생성
→ TokenResponse 생성
→ 200 OK
```

## 로그인 실패 흐름

```text
이메일 없음 또는 비밀번호 틀림
→ BusinessException(INVALID_CREDENTIALS)
→ GlobalExceptionHandler
→ 401 Unauthorized
```

응답 예시:

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 일치하지 않습니다"
}
```

## 테스트 예시

### 로그인 요청

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "test@example.com",
  "password": "test1234"
}
```

### 성공 응답

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 30
}
```

실제 토큰 문자열은 훨씬 길다.

JWT Payload를 확인할 수 있는 도구를 사용할 때는 운영 환경의 실제 토큰이나 민감한 토큰을 외부 사이트에 붙여 넣지 않는 것이 안전하다.

## 이번 단계의 한계

이번 커밋에서는 토큰을 발급하지만 API 요청에서 Access Token을 읽어 인증하는 필터가 아직 없다.

```text
로그인 가능
→ 토큰 발급 가능
→ 하지만 보호 API에서 토큰을 처리하는 코드 없음
```

또한 아직 다음 기능이 없다.

- `Authorization: Bearer` 헤더 처리
- JWT 기반 Authentication 생성
- 실제 로그인 사용자 ID 사용
- Refresh Token DB 저장
- 토큰 재발급
- 로그아웃
- Access Token과 Refresh Token 종류 구분
- JWT 오류 응답 세분화

Access Token과 Refresh Token에 토큰 종류 Claim이 없고 같은 비밀키를 사용하므로, 이후 인증 필터에서는 Refresh Token이 일반 API 인증에 사용되지 않도록 구분하는 기능이 필요하다.

## 이번 단계 요약

```text
JWT 로그인과 토큰 발급
├─ JJWT 라이브러리 추가
├─ JWT 비밀키와 만료시간 설정
├─ LoginRequest 추가
├─ TokenResponse 추가
├─ 이메일로 사용자 조회
├─ BCrypt로 비밀번호 비교
├─ Access Token 생성
│  ├─ userId
│  ├─ email
│  ├─ role
│  ├─ 생성 시각
│  └─ 만료 시각
├─ Refresh Token 생성
│  ├─ userId
│  ├─ 생성 시각
│  └─ 만료 시각
├─ JWT 서명과 파싱 기능 추가
└─ POST /api/auth/login 추가
```

이번 커밋을 통해 사용자가 이메일과 비밀번호로 로그인하고, 이후 인증에 사용할 Access Token과 재발급에 사용할 Refresh Token을 받을 수 있게 됐다.
