# 04. 요청값 검증과 전역 예외 처리

- 커밋: `ebbf5d7`
- 커밋 메시지: `feat: Diary 요청 검증 및 전역 예외 처리 추가`

## 이번 단계에서 한 일

이전 단계에서는 일기 CRUD가 동작했지만, 잘못된 값이 들어오는 상황을 제대로 처리하지 못했다.

예를 들어 다음과 같은 요청이 들어올 수 있다.

```json
{
  "date": null,
  "content": "",
  "emotionId": 100
}
```

이번 커밋에서는 이런 잘못된 요청을 검사하고, 오류가 발생했을 때 일정한 JSON 형식으로 응답하도록 개선했다.

```text
요청값 검증
├─ 날짜가 있는지 확인
├─ 일기 내용 길이 확인
└─ 감정 번호가 1~5인지 확인

예외 처리
├─ 일기를 찾을 수 없음 → 404
├─ 요청값이 올바르지 않음 → 400
└─ 예상하지 못한 서버 오류 → 500
```

## 변경된 파일

```text
src/main/java/com/example/emotiondiary/
├─ controller/
│  └─ DiaryController.java
├─ service/
│  └─ DiaryService.java
├─ dto/
│  ├─ DiaryRequest.java
│  └─ ErrorResponse.java
└─ exception/
   ├─ DiaryNotFoundException.java
   └─ GlobalExceptionHandler.java
```

## 검증이 필요한 이유

클라이언트가 보내는 값은 항상 올바르다고 믿을 수 없다.

검증 없이 데이터를 저장하면 다음과 같은 문제가 생길 수 있다.

- 날짜가 없는 일기가 저장될 수 있다.
- 내용이 비어 있거나 너무 긴 일기가 저장될 수 있다.
- 감정 번호로 약속하지 않은 값이 저장될 수 있다.
- 잘못된 값 때문에 DB 오류가 발생할 수 있다.
- 클라이언트가 무엇을 잘못했는지 알기 어렵다.

따라서 Controller에서 요청을 받은 직후 값을 검사한다.

```text
클라이언트 요청
→ 요청 JSON을 DiaryRequest로 변환
→ DiaryRequest 검증
→ 성공하면 Service 실행
→ 실패하면 400 응답
```

## DiaryRequest 검증

`DiaryRequest`의 각 필드에 Jakarta Validation 어노테이션을 추가했다.

```java
public class DiaryRequest {

    @NotNull(message = "date is required")
    private Long date;

    @NotNull(message = "content is required")
    @Size(
        min = 1,
        max = 2000,
        message = "content must be between 1 and 2000 characters"
    )
    private String content;

    @NotNull(message = "emotionId is required")
    @Min(value = 1, message = "emotionId must be between 1 and 5")
    @Max(value = 5, message = "emotionId must be between 1 and 5")
    private Integer emotionId;
}
```

## `@NotNull`

```java
@NotNull(message = "date is required")
private Long date;
```

값이 `null`이면 검증에 실패한다.

```json
{
  "date": null
}
```

오류 메시지는 다음 문자열을 사용한다.

```text
date is required
```

`@NotNull`은 값이 존재하는지만 확인한다.

문자열이 빈 문자열인지까지 확인하려면 `@NotBlank` 같은 다른 어노테이션을 사용할 수 있다. 이번 커밋에서는 `content`의 빈 문자열을 `@Size(min = 1)`로 검사한다.

## `@Size`

```java
@Size(
    min = 1,
    max = 2000,
    message = "content must be between 1 and 2000 characters"
)
private String content;
```

문자열의 길이를 검사한다.

```text
0글자       → 실패
1글자       → 성공
2,000글자   → 성공
2,001글자   → 실패
```

Entity의 DB 컬럼 길이도 2,000자로 설정되어 있다.

```java
@Column(nullable = false, length = 2000)
private String content;
```

DTO 검증과 DB 컬럼 제한을 함께 사용하면 잘못된 값이 DB까지 도달하기 전에 막을 수 있다.

## `@Min`과 `@Max`

```java
@Min(value = 1, message = "emotionId must be between 1 and 5")
@Max(value = 5, message = "emotionId must be between 1 and 5")
private Integer emotionId;
```

감정 번호가 1 이상, 5 이하인지 검사한다.

```text
emotionId = 0 → 실패
emotionId = 1 → 성공
emotionId = 3 → 성공
emotionId = 5 → 성공
emotionId = 6 → 실패
```

## Controller의 `@Valid`

DTO에 검증 어노테이션만 붙여서는 자동 검증이 시작되지 않는다.

Controller의 요청 객체 앞에 `@Valid`를 붙여야 한다.

```java
public ResponseEntity<DiaryResponse> create(
        @Valid @RequestBody DiaryRequest request
)
```

각 어노테이션의 역할은 다음과 같다.

```text
@RequestBody
└─ 요청 JSON을 DiaryRequest 객체로 변환

@Valid
└─ DiaryRequest의 검증 어노테이션 실행
```

검증은 일기 생성과 수정 요청에 모두 적용했다.

```java
@PostMapping
public ResponseEntity<DiaryResponse> create(
        @Valid @RequestBody DiaryRequest request
)
```

```java
@PutMapping("/{id}")
public ResponseEntity<DiaryResponse> update(
        @PathVariable String id,
        @Valid @RequestBody DiaryRequest request
)
```

## 검증 성공 흐름

```text
POST /api/diaries
→ JSON을 DiaryRequest로 변환
→ @Valid 실행
→ date 검사 성공
→ content 검사 성공
→ emotionId 검사 성공
→ DiaryService.create() 실행
→ DB에 일기 저장
→ 201 Created
```

## 검증 실패 흐름

검증이 하나라도 실패하면 Service는 실행되지 않는다.

```text
POST /api/diaries
→ JSON을 DiaryRequest로 변환
→ @Valid 실행
→ 검증 실패
→ MethodArgumentNotValidException 발생
→ GlobalExceptionHandler가 처리
→ 400 VALIDATION_ERROR 응답
```

## 예외란 무엇인가?

예외는 프로그램을 실행하는 중 정상적으로 처리할 수 없는 상황이 발생했다는 표시다.

예를 들어 존재하지 않는 일기를 조회하면 다음과 같은 상황이 된다.

```text
요청한 ID로 DB 조회
→ 결과 없음
→ 일기를 반환할 수 없음
→ 예외 발생
```

예외를 아무 처리 없이 두면 클라이언트는 일관되지 않은 오류 응답을 받을 수 있다.

따라서 예외 종류에 맞는 HTTP 상태와 JSON을 반환하도록 처리한다.

## DiaryNotFoundException

존재하지 않는 일기를 요청했을 때 사용할 전용 예외를 만들었다.

```java
public class DiaryNotFoundException extends RuntimeException {

    public DiaryNotFoundException(String id) {
        super("Diary not found with id: " + id);
    }
}
```

### `extends RuntimeException`

`DiaryNotFoundException`이 실행 중 발생하는 예외라는 뜻이다.

### `super()`

```java
super("Diary not found with id: " + id);
```

부모인 `RuntimeException`에 오류 메시지를 전달한다.

예를 들어 ID가 `abc-123`이면 메시지는 다음과 같다.

```text
Diary not found with id: abc-123
```

## Service에서 예외 발생시키기

이전에는 `IllegalArgumentException`을 사용했지만, 이번 커밋부터 의미가 명확한 `DiaryNotFoundException`을 사용한다.

```java
Diary diary = diaryRepository.findById(id)
        .orElseThrow(() -> new DiaryNotFoundException(id));
```

다음 세 메서드에 적용했다.

- 일기 단건 조회
- 일기 수정
- 일기 삭제

처리 흐름은 다음과 같다.

```text
diaryRepository.findById(id)
├─ 일기 있음 → Diary 반환
└─ 일기 없음 → DiaryNotFoundException 발생
```

전용 예외를 사용하면 오류의 의미를 클래스 이름만 보고도 알 수 있다.

## ErrorResponse

모든 오류를 일정한 JSON 형식으로 반환하기 위한 DTO다.

```java
@Getter
@Builder
public class ErrorResponse {
    private String code;
    private String message;
}
```

각 필드의 의미는 다음과 같다.

```text
code
└─ 프로그램이 오류 종류를 구분할 때 사용하는 값

message
└─ 사람이 읽을 수 있는 오류 설명
```

응답 예시는 다음과 같다.

```json
{
  "code": "DIARY_NOT_FOUND",
  "message": "Diary not found with id: abc-123"
}
```

클라이언트는 HTTP 상태뿐 아니라 `code`를 이용해 오류 종류를 정확히 구분할 수 있다.

## GlobalExceptionHandler

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
}
```

애플리케이션의 여러 Controller에서 발생하는 예외를 한곳에서 처리한다.

### `@RestControllerAdvice`

모든 REST Controller에 공통으로 적용되는 예외 처리 클래스라는 뜻이다.

Controller마다 같은 `try-catch`를 반복해서 작성할 필요가 없어진다.

예외 처리기가 없다면:

```java
@GetMapping("/{id}")
public ResponseEntity<?> getById(...) {
    try {
        // 조회
    } catch (...) {
        // 오류 응답
    }
}
```

전역 예외 처리기를 사용하면:

```java
@GetMapping("/{id}")
public ResponseEntity<DiaryResponse> getById(...) {
    return ResponseEntity.ok(diaryService.getById(id));
}
```

Controller는 정상 흐름에 집중하고 예외 응답은 `GlobalExceptionHandler`가 담당한다.

### `@Slf4j`

Lombok이 로그를 출력할 수 있는 `log` 객체를 자동으로 만들어준다.

```java
log.error("Unhandled exception", e);
```

## `@ExceptionHandler`

```java
@ExceptionHandler(DiaryNotFoundException.class)
```

어떤 예외를 해당 메서드가 처리할지 지정한다.

```text
DiaryNotFoundException
→ handleDiaryNotFound()

MethodArgumentNotValidException
→ handleValidation()

그 밖의 Exception
→ handleException()
```

## 일기를 찾을 수 없는 경우

```java
@ExceptionHandler(DiaryNotFoundException.class)
public ResponseEntity<ErrorResponse> handleDiaryNotFound(
        DiaryNotFoundException e
) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.builder()
                    .code("DIARY_NOT_FOUND")
                    .message(e.getMessage())
                    .build());
}
```

HTTP 상태는 `404 Not Found`를 사용한다.

응답 예시:

```json
{
  "code": "DIARY_NOT_FOUND",
  "message": "Diary not found with id: abc-123"
}
```

전체 흐름:

```text
GET /api/diaries/abc-123
→ DiaryService에서 ID 조회
→ 일기 없음
→ DiaryNotFoundException 발생
→ handleDiaryNotFound() 실행
→ 404 응답
```

## 요청값 검증에 실패한 경우

`@Valid` 검증에 실패하면 Spring이 `MethodArgumentNotValidException`을 발생시킨다.

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException e
) {
}
```

검증에 실패한 필드 중 첫 번째 오류를 가져온다.

```java
String message = e.getBindingResult()
        .getFieldErrors()
        .stream()
        .findFirst()
        .map(fe -> fe.getField() + " : " + fe.getDefaultMessage())
        .orElse("Validation failed");
```

각 단계의 의미는 다음과 같다.

```text
getBindingResult()
→ 전체 검증 결과

getFieldErrors()
→ 필드별 오류 목록

stream()
→ 오류 목록을 순서대로 처리

findFirst()
→ 첫 번째 오류 선택

map()
→ 필드 이름과 오류 메시지를 합침

orElse()
→ 오류 메시지가 없을 때 기본 문구 사용
```

예를 들어 `emotionId`가 10이면 다음 메시지가 만들어진다.

```text
emotionId : emotionId must be between 1 and 5
```

응답은 다음과 같다.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "emotionId : emotionId must be between 1 and 5"
}
```

HTTP 상태는 `400 Bad Request`다.

## 예상하지 못한 서버 오류

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("Unhandled exception", e);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build());
}
```

앞의 두 처리기에 해당하지 않는 나머지 예외를 처리한다.

```text
예상하지 못한 Exception
→ 서버 로그에 상세 오류 기록
→ 클라이언트에는 일반적인 오류 메시지 반환
```

클라이언트에게 Java 예외 내용이나 DB 정보 같은 내부 정보를 그대로 보여주지 않는 것이 중요하다.

응답 예시는 다음과 같다.

```json
{
  "code": "INTERNAL_ERROR",
  "message": "Internal server error"
}
```

HTTP 상태는 `500 Internal Server Error`다.

## 오류별 응답 정리

| 상황 | 예외 | HTTP 상태 | 에러 코드 |
|---|---|---:|---|
| 존재하지 않는 일기 | `DiaryNotFoundException` | 404 | `DIARY_NOT_FOUND` |
| 요청값 검증 실패 | `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| 예상하지 못한 오류 | `Exception` | 500 | `INTERNAL_ERROR` |

## 정상 요청과 오류 요청 비교

### 정상 요청

```json
{
  "date": 20260816,
  "content": "오늘도 공부했다.",
  "emotionId": 3
}
```

```text
@Valid 통과
→ DiaryService 실행
→ DB 저장
→ 201 Created
```

### 날짜 누락

```json
{
  "content": "오늘도 공부했다.",
  "emotionId": 3
}
```

```json
{
  "code": "VALIDATION_ERROR",
  "message": "date : date is required"
}
```

### 감정 번호 범위 오류

```json
{
  "date": 20260816,
  "content": "오늘도 공부했다.",
  "emotionId": 8
}
```

```json
{
  "code": "VALIDATION_ERROR",
  "message": "emotionId : emotionId must be between 1 and 5"
}
```

## 이번 단계의 한계

이번 커밋에서는 에러 코드와 메시지를 각 메서드에 문자열로 직접 작성했다.

```java
.code("DIARY_NOT_FOUND")
.code("VALIDATION_ERROR")
.code("INTERNAL_ERROR")
```

예외 종류가 많아지면 문자열이 여러 파일에 흩어지고 중복 코드가 늘어날 수 있다.

이후 커밋에서는 `ErrorCode` enum과 `BusinessException`을 추가해 에러 정보를 한곳에서 관리하도록 개선한다.

또한 현재 검증은 일기 요청값에만 적용되어 있으며 회원가입과 로그인 검증은 이후 단계에서 추가된다.

## 이번 단계 요약

```text
요청값 검증과 예외 처리
├─ DiaryRequest에 검증 어노테이션 추가
│  ├─ @NotNull
│  ├─ @Size
│  ├─ @Min
│  └─ @Max
├─ Controller에 @Valid 적용
├─ DiaryNotFoundException 추가
├─ ErrorResponse 형식 추가
└─ GlobalExceptionHandler 추가
   ├─ 400 VALIDATION_ERROR
   ├─ 404 DIARY_NOT_FOUND
   └─ 500 INTERNAL_ERROR
```

이번 커밋을 통해 잘못된 요청이 Service나 DB까지 들어가는 것을 막고, 오류가 발생했을 때 클라이언트가 이해할 수 있는 일정한 JSON 응답을 제공하게 됐다.
