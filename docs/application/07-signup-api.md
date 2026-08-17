# 07. 회원가입 API와 공통 예외 처리

- 커밋: `7775e4e`
- 커밋 메시지: `feat: 회원가입 API 및 공통 예외 처리 추가`

## 이번 단계에서 한 일

사용자가 이메일, 비밀번호, 닉네임으로 회원가입할 수 있는 API를 추가했다.

회원가입 요청값을 검사하고, 이미 가입된 이메일인지 DB에서 확인한 뒤 User를 저장한다.

또한 예외 처리를 `ErrorCode`와 `BusinessException` 중심으로 정리해 여러 종류의 오류를 같은 방식으로 처리할 수 있게 만들었다.

```text
회원가입 기능
├─ SignUpRequest로 요청 받기
├─ 이메일·비밀번호·닉네임 검증
├─ @UniqueEmail로 이메일 중복 확인
├─ User Entity 생성
└─ users 테이블에 저장

예외 처리 개선
├─ ErrorCode에서 에러 정보 관리
├─ BusinessException으로 비즈니스 오류 표현
└─ GlobalExceptionHandler에서 공통 응답 생성
```

## 변경된 파일

```text
src/main/java/com/example/emotiondiary/
├─ controller/
│  └─ AuthController.java
├─ service/
│  └─ AuthService.java
├─ dto/auth/
│  └─ SignUpRequest.java
├─ validation/
│  ├─ UniqueEmail.java
│  └─ UniqueEmailValidator.java
└─ exception/
   ├─ ErrorCode.java
   ├─ BusinessException.java
   ├─ DiaryNotFoundException.java
   └─ GlobalExceptionHandler.java
```

## 회원가입 전체 흐름

```text
POST /api/auth/signup
→ JSON을 SignUpRequest로 변환
→ @Valid로 요청값 검증
→ @UniqueEmail로 이메일 중복 조회
→ AuthService.signup() 호출
→ 이메일 중복 여부 다시 확인
→ User.create()로 일반 사용자 생성
→ userRepository.save()로 DB 저장
→ 201 Created 응답
```

검증이나 저장 과정에서 문제가 생기면 Service 실행 또는 DB 저장을 중단하고 오류 응답을 반환한다.

## 회원가입 요청 DTO

`SignUpRequest`는 회원가입 요청 JSON을 받는 DTO다.

```java
@Getter
@NoArgsConstructor
public class SignUpRequest {
    private String email;
    private String password;
    private String nickname;
}
```

요청 예시는 다음과 같다.

```json
{
  "email": "test@example.com",
  "password": "test1234",
  "nickname": "홍길동"
}
```

Spring은 JSON을 읽고 다음과 같은 SignUpRequest 객체를 만든다.

```text
request.email    = "test@example.com"
request.password = "test1234"
request.nickname = "홍길동"
```

## 이메일 검증

```java
@NotBlank(message = "이메일은 필수입니다")
@Email(message = "이메일 형식이 올바르지 않습니다")
@UniqueEmail
private String email;
```

이메일에는 세 단계의 검증이 적용된다.

### `@NotBlank`

값이 `null`, 빈 문자열 또는 공백만 있는 문자열인지 확인한다.

```text
null  → 실패
""    → 실패
"   " → 실패
```

### `@Email`

문자열이 이메일 형식인지 확인한다.

```text
test@example.com → 성공
test             → 실패
test@            → 실패
```

### `@UniqueEmail`

DB에 같은 이메일이 이미 저장되어 있는지 확인하는 프로젝트 전용 검증이다.

```text
users 테이블에 이메일 없음 → 성공
users 테이블에 이메일 있음 → 실패
```

## 비밀번호 검증

```java
@NotBlank(message = "비밀번호는 필수입니다")
@Size(min = 8, max = 32, message = "비밀번호는 8~32자입니다")
@Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
        message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다"
)
private String password;
```

비밀번호는 다음 조건을 모두 만족해야 한다.

```text
값이 비어 있지 않음
길이가 8~32자
영문을 하나 이상 포함
숫자를 하나 이상 포함
```

예시는 다음과 같다.

| 비밀번호 | 결과 | 이유 |
|---|---|---|
| `test1234` | 성공 | 영문과 숫자 포함, 8자 |
| `12345678` | 실패 | 영문 없음 |
| `password` | 실패 | 숫자 없음 |
| `a1` | 실패 | 8자 미만 |

### 정규식의 의미

```text
^(?=.*[A-Za-z])(?=.*\d).+$
```

처음 보면 복잡하지만 핵심은 다음 두 조건이다.

```text
(?=.*[A-Za-z])
└─ 영문이 하나 이상 있는지 확인

(?=.*\d)
└─ 숫자가 하나 이상 있는지 확인
```

## 닉네임 검증

```java
@NotBlank(message = "닉네임은 필수입니다")
@Size(min = 2, max = 20)
private String nickname;
```

닉네임은 비어 있을 수 없고 길이는 2~20자여야 한다.

`@Size`에 별도 메시지를 작성하지 않았으므로 검증 라이브러리의 기본 메시지가 사용된다.

## 커스텀 검증이 필요한 이유

`@Email`과 `@Size`는 값 자체만 보면 검사할 수 있다.

하지만 이메일 중복 여부는 DB를 조회해야 알 수 있다.

```text
이메일 형식 검사
└─ 입력값만 확인하면 됨

이메일 중복 검사
└─ users 테이블 조회가 필요함
```

이처럼 프로젝트 상황에 맞는 검증이 필요할 때 커스텀 Validator를 만들 수 있다.

## `@UniqueEmail` 어노테이션

```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
public @interface UniqueEmail {
}
```

### `@Target(ElementType.FIELD)`

`@UniqueEmail`을 필드에 붙일 수 있다는 뜻이다.

```java
@UniqueEmail
private String email;
```

### `@Retention(RetentionPolicy.RUNTIME)`

프로그램 실행 중에도 이 어노테이션 정보를 유지한다.

검증 라이브러리가 실행 시점에 어노테이션을 찾아야 하므로 `RUNTIME`이 필요하다.

### `@Constraint`

```java
@Constraint(validatedBy = UniqueEmailValidator.class)
```

이 어노테이션의 실제 검증은 `UniqueEmailValidator`가 담당한다는 뜻이다.

```text
@UniqueEmail 발견
→ UniqueEmailValidator 실행
```

## 커스텀 검증의 기본 속성

```java
String message() default "이미 가입된 이메일입니다";
```

검증 실패 시 사용할 기본 메시지다.

```java
Class<?>[] groups() default {};
```

여러 검증을 그룹으로 나눠 실행할 때 사용한다. 현재 프로젝트에서는 별도로 사용하지 않지만 Jakarta Validation 커스텀 어노테이션에 필요한 기본 형식이다.

```java
Class<? extends Payload>[] payload() default {};
```

검증 오류에 추가 정보를 담을 때 사용하는 메타데이터다. 현재 프로젝트에서는 사용하지 않는다.

## UniqueEmailValidator

```java
@Component
@RequiredArgsConstructor
public class UniqueEmailValidator
        implements ConstraintValidator<UniqueEmail, String> {
}
```

`ConstraintValidator<UniqueEmail, String>`의 의미는 다음과 같다.

```text
UniqueEmail
└─ 처리할 검증 어노테이션

String
└─ 검증할 값의 타입
```

이 Validator는 문자열 이메일을 검사한다.

### UserRepository 주입

```java
private final UserRepository userRepository;
```

이메일 중복 검사를 위해 users 테이블을 조회해야 하므로 UserRepository를 주입받는다.

### `isValid()`

```java
@Override
public boolean isValid(
        String email,
        ConstraintValidatorContext context
) {
    if (email == null) return true;
    return !userRepository.existsByEmail(email);
}
```

검증 결과는 boolean으로 반환한다.

```text
true  → 검증 성공
false → 검증 실패
```

### null을 true로 처리하는 이유

```java
if (email == null) return true;
```

`@UniqueEmail`은 중복 여부만 담당한다.

값이 없는 문제는 이미 `@NotBlank`가 담당한다.

```text
email이 null
├─ @UniqueEmail → 중복 검사의 대상이 아니므로 통과
└─ @NotBlank → 필수값 오류 처리
```

각 검증 어노테이션이 한 가지 책임에 집중하도록 만든 것이다.

### 이메일 중복 결과

```java
return !userRepository.existsByEmail(email);
```

`existsByEmail()` 결과 앞에 `!`를 붙여 반대로 만든다.

```text
DB에 이메일 있음
→ existsByEmail() = true
→ !true = false
→ 검증 실패

DB에 이메일 없음
→ existsByEmail() = false
→ !false = true
→ 검증 성공
```

## AuthController

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
}
```

회원가입이나 로그인처럼 인증과 관련된 API를 담당하는 Controller다.

이번 커밋에서는 회원가입 API만 구현했다.

```java
@PostMapping("/signup")
public ResponseEntity<Void> signup(
        @Valid @RequestBody SignUpRequest request
) {
    authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
}
```

최종 주소는 Controller의 공통 주소와 메서드 주소를 합친 값이다.

```text
/api/auth + /signup
→ POST /api/auth/signup
```

### 성공 응답

회원가입에 성공하면 다음 상태를 반환한다.

```text
201 Created
```

반환 타입이 `ResponseEntity<Void>`이므로 응답 본문은 없다.

## AuthService

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
}
```

회원가입의 실제 처리 순서를 담당한다.

```java
private final UserRepository userRepository;
```

User를 조회하고 저장하기 위해 UserRepository를 사용한다.

## 회원가입 트랜잭션

```java
@Transactional
public Long signup(SignUpRequest request)
```

회원가입은 users 테이블에 데이터를 INSERT하는 작업이므로 쓰기 가능한 트랜잭션을 사용한다.

메서드가 정상적으로 끝나면 변경 내용을 DB에 반영하고, 처리 중 예외가 발생하면 저장 작업을 취소한다.

## 서비스에서 이메일 재확인

```java
if (userRepository.existsByEmail(request.getEmail())) {
    throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
}
```

DTO의 `@UniqueEmail`이 이미 중복을 확인했지만 Service에서도 다시 확인한다.

```text
@UniqueEmail
└─ 요청 검증 단계의 빠른 오류 응답

AuthService의 중복 확인
└─ 비즈니스 로직 단계의 방어
```

다만 두 번 조회하는 것만으로 동시에 들어온 요청의 중복을 완벽하게 막을 수는 없다.

두 요청이 거의 같은 순간에 조회하면 둘 다 이메일이 없다고 판단할 수 있기 때문이다.

최종적으로는 User Entity의 DB unique 제약조건이 중복 저장을 막는다.

```java
@Column(unique = true)
private String email;
```

## User 생성과 저장

```java
User user = User.create(
        request.getEmail(),
        request.getPassword(),
        request.getNickname()
);
```

`User.create()`는 일반 사용자 권한인 `Role.USER`를 자동으로 설정한다.

```text
email
password
nickname
role = USER
```

다음 코드로 DB에 저장한다.

```java
return userRepository.save(user).getId();
```

처리 순서는 다음과 같다.

```text
userRepository.save(user)
→ User의 @PrePersist 실행
→ createdAt 설정
→ users 테이블에 INSERT
→ DB가 id 자동 생성
→ 저장된 User의 id 반환
```

Controller는 반환된 ID를 응답에 사용하지 않지만 Service는 생성된 사용자 ID를 반환하도록 작성되어 있다.

## 이 단계의 비밀번호 저장 방식

이번 커밋에서는 다음 값을 그대로 User에 전달한다.

```java
request.getPassword()
```

따라서 아직 비밀번호가 평문으로 저장된다.

```text
요청 비밀번호: test1234
DB 비밀번호: test1234
```

이 방식은 실제 서비스에서는 안전하지 않다.

다음 커밋에서 Spring Security와 BCrypt를 추가해 암호화된 해시값을 저장하도록 개선한다.

## ErrorCode enum

기존에는 오류 코드와 상태를 예외 처리 메서드마다 직접 작성했다.

```java
.code("DIARY_NOT_FOUND")
HttpStatus.NOT_FOUND
```

이번 커밋에서는 모든 에러 정보를 `ErrorCode` enum 한곳에서 관리한다.

```java
public enum ErrorCode {
    DUPLICATE_EMAIL(
        HttpStatus.BAD_REQUEST,
        "DUPLICATE_EMAIL",
        "이미 가입된 이메일입니다"
    )
}
```

각 ErrorCode는 세 가지 정보를 가진다.

```text
status
└─ HTTP 상태

code
└─ 클라이언트가 오류를 구분할 문자열

defaultMessage
└─ 기본 오류 메시지
```

## ErrorCode 목록

| HTTP 상태 | ErrorCode | 의미 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 요청값 검증 실패 |
| 400 | `DUPLICATE_EMAIL` | 이메일 중복 |
| 401 | `INVALID_CREDENTIALS` | 로그인 정보 불일치 |
| 401 | `UNAUTHORIZED` | 인증 필요 |
| 401 | `INVALID_TOKEN` | 잘못된 토큰 |
| 401 | `EXPIRED_TOKEN` | 만료된 토큰 |
| 403 | `FORBIDDEN` | 접근 권한 없음 |
| 404 | `DIARY_NOT_FOUND` | 일기 없음 |
| 404 | `USER_NOT_FOUND` | 사용자 없음 |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 서버 오류 |

이번 단계에서 실제로 모두 사용하지는 않지만 이후 인증과 JWT 기능에서 사용할 코드까지 미리 정의했다.

## BusinessException

```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
}
```

비즈니스 규칙을 지키지 못했을 때 사용하는 공통 예외다.

예를 들면:

```text
이미 가입된 이메일
존재하지 않는 사용자
존재하지 않는 일기
잘못된 로그인 정보
```

이런 오류들은 단순한 프로그램 고장이 아니라 서비스 규칙에 따른 예상 가능한 오류다.

## BusinessException 생성자

기본 메시지를 사용하는 생성자:

```java
public BusinessException(ErrorCode errorCode) {
    super(errorCode.getDefaultMessage());
    this.errorCode = errorCode;
}
```

사용 예시:

```java
throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
```

직접 메시지를 지정하는 생성자:

```java
public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
}
```

사용 예시:

```java
throw new BusinessException(
        ErrorCode.DIARY_NOT_FOUND,
        "Diary not found with id: " + id
);
```

## DiaryNotFoundException 변경

기존에는 `RuntimeException`을 직접 상속했다.

이번 커밋에서는 `BusinessException`을 상속하도록 변경했다.

```java
public class DiaryNotFoundException
        extends BusinessException {

    public DiaryNotFoundException(String id) {
        super(
            ErrorCode.DIARY_NOT_FOUND,
            "Diary not found with id: " + id
        );
    }
}
```

상속 구조는 다음과 같다.

```text
RuntimeException
└─ BusinessException
   └─ DiaryNotFoundException
```

따라서 GlobalExceptionHandler는 `DiaryNotFoundException`을 위한 별도 처리 메서드 없이 `BusinessException` 처리기 하나로 처리할 수 있다.

## GlobalExceptionHandler 변경

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ErrorResponse> handleBusiness(
        BusinessException e
) {
    ErrorCode ec = e.getErrorCode();

    return ResponseEntity.status(ec.getStatus())
            .body(ErrorResponse.builder()
                    .code(ec.getCode())
                    .message(e.getMessage())
                    .build());
}
```

BusinessException이 가진 ErrorCode에서 HTTP 상태와 코드 값을 가져온다.

```text
BusinessException 발생
→ getErrorCode()로 ErrorCode 조회
→ ErrorCode의 status로 HTTP 상태 결정
→ ErrorCode의 code로 응답 코드 결정
→ Exception의 message로 응답 메시지 결정
```

## 공통 예외 처리의 장점

기존 방식에서는 예외 종류마다 처리 메서드를 추가해야 했다.

```text
DiaryNotFoundException용 Handler
UserNotFoundException용 Handler
DuplicateEmailException용 Handler
InvalidCredentialsException용 Handler
```

변경된 방식에서는 BusinessException 하나로 처리할 수 있다.

```text
각 비즈니스 예외
→ BusinessException 상속 또는 직접 생성
→ handleBusiness() 한 곳에서 처리
```

장점은 다음과 같다.

- HTTP 상태와 오류 코드를 한곳에서 관리한다.
- 문자열 오타를 줄일 수 있다.
- 예외 처리 코드 중복이 줄어든다.
- 새로운 비즈니스 오류를 추가하기 쉽다.
- 모든 API가 같은 오류 응답 형식을 유지한다.

## 검증 오류 처리

검증 오류도 ErrorCode의 값을 사용하도록 변경했다.

```java
ErrorCode.VALIDATION_ERROR.getStatus()
ErrorCode.VALIDATION_ERROR.getCode()
ErrorCode.VALIDATION_ERROR.getDefaultMessage()
```

첫 번째 필드 오류가 있으면 구체적인 메시지를 사용한다.

```text
email : 이메일 형식이 올바르지 않습니다
```

구체적인 메시지를 찾지 못하면 ErrorCode의 기본 메시지를 사용한다.

```text
요청 값이 올바르지 않습니다
```

## 회원가입 성공 흐름

```text
POST /api/auth/signup
→ SignUpRequest 생성
→ email, password, nickname 검증
→ UniqueEmailValidator에서 DB 중복 조회
→ AuthController.signup()
→ AuthService.signup()
→ 이메일 중복 재확인
→ User.create()
→ role을 USER로 설정
→ userRepository.save()
→ users 테이블에 INSERT
→ 201 Created
```

## 이메일 형식 오류 흐름

```text
잘못된 이메일 요청
→ @Email 검증 실패
→ MethodArgumentNotValidException
→ GlobalExceptionHandler.handleValidation()
→ 400 VALIDATION_ERROR
```

응답 예시:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "email : 이메일 형식이 올바르지 않습니다"
}
```

## 중복 이메일 흐름

DTO 검증에서 발견한 경우:

```text
@UniqueEmail
→ existsByEmail()가 true
→ 검증 실패
→ 400 VALIDATION_ERROR
```

응답 예시:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "email : 이미 가입된 이메일입니다"
}
```

Service에서 발견한 경우:

```text
AuthService에서 existsByEmail()가 true
→ BusinessException(DUPLICATE_EMAIL)
→ handleBusiness()
→ 400 DUPLICATE_EMAIL
```

응답 예시:

```json
{
  "code": "DUPLICATE_EMAIL",
  "message": "이미 가입된 이메일입니다"
}
```

두 검사 위치에 따라 응답 코드가 다를 수 있다는 점을 알아두어야 한다.

## 테스트 예시

### 정상 회원가입

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "email": "test@example.com",
  "password": "test1234",
  "nickname": "홍길동"
}
```

기대 결과:

```text
201 Created
```

### 같은 요청 다시 보내기

```text
400 Bad Request
```

```json
{
  "code": "VALIDATION_ERROR",
  "message": "email : 이미 가입된 이메일입니다"
}
```

실제 처리 시점에 따라 `DUPLICATE_EMAIL`로 반환될 수도 있다.

## 이번 단계의 한계

이번 커밋에서는 비밀번호가 평문으로 저장된다.

```text
요청: test1234
DB 저장: test1234
```

실제 서비스에서는 절대로 평문 비밀번호를 저장하면 안 된다.

다음 커밋에서 Spring Security의 BCryptPasswordEncoder를 사용해 해시값으로 저장한다.

또한 아직 다음 기능은 없다.

- 로그인 API
- JWT 발급
- 인증 필터
- Refresh Token
- 로그아웃
- 관리자 권한 검사

## 이번 단계 요약

```text
회원가입 API와 공통 예외 처리
├─ POST /api/auth/signup
├─ SignUpRequest 검증
│  ├─ 이메일 형식
│  ├─ 비밀번호 길이와 조합
│  └─ 닉네임 길이
├─ @UniqueEmail 커스텀 검증
├─ UserRepository로 이메일 중복 조회
├─ User.create()로 일반 사용자 생성
├─ users 테이블에 저장
├─ ErrorCode enum 추가
├─ BusinessException 추가
├─ DiaryNotFoundException 공통 구조 적용
└─ GlobalExceptionHandler 응답 통일
```

이번 커밋을 통해 회원가입이 가능해졌고, 앞으로 로그인과 JWT 기능을 추가할 수 있는 공통 예외 처리 구조가 만들어졌다.
