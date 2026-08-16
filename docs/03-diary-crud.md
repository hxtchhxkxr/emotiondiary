# 03. Diary CRUD 구현

- 커밋: `0be29e5`
- 커밋 메시지: `feat: Diary 도메인 CRUD 기능 구현`

## 이번 단계에서 한 일

감정 일기를 생성하고, 조회하고, 수정하고, 삭제하는 REST API를 구현했다.

CRUD는 데이터를 다룰 때 가장 기본이 되는 네 가지 기능을 뜻한다.

| 이름 | 의미 | Diary 기능 |
|---|---|---|
| Create | 생성 | 새 일기 작성 |
| Read | 조회 | 일기 목록과 상세 조회 |
| Update | 수정 | 기존 일기 내용 변경 |
| Delete | 삭제 | 일기 제거 |

이번 커밋에서는 다음 계층을 만들었다.

```text
Controller
→ Service
→ Repository
→ MariaDB
```

그리고 요청과 응답에 사용할 DTO, DB 테이블과 연결할 Entity를 추가했다.

## 추가된 파일

```text
src/main/java/com/example/emotiondiary/
├─ controller/
│  └─ DiaryController.java
├─ service/
│  └─ DiaryService.java
├─ repository/
│  └─ DiaryRepository.java
├─ entity/
│  └─ Diary.java
└─ dto/
   ├─ DiaryRequest.java
   ├─ DiaryResponse.java
   └─ DiaryListResponse.java
```

## 전체 요청 흐름

클라이언트가 일기 생성 요청을 보냈을 때의 흐름은 다음과 같다.

```text
클라이언트
→ DiaryController가 HTTP 요청을 받음
→ JSON을 DiaryRequest로 변환
→ DiaryService가 일기 생성 로직 실행
→ DiaryRepository가 Diary를 DB에 저장
→ 저장된 Diary를 DiaryResponse로 변환
→ 클라이언트에게 JSON 응답
```

각 계층은 서로 다른 역할을 맡는다.

| 계층 | 역할 |
|---|---|
| Controller | HTTP 요청과 응답 처리 |
| Service | 실제 기능과 업무 규칙 처리 |
| Repository | 데이터베이스 접근 |
| Entity | DB 테이블과 연결되는 객체 |
| DTO | 계층 사이에서 전달할 데이터 |

## Diary Entity

`Diary`는 `diary` 테이블과 연결되는 JPA Entity다.

```java
@Entity
@Table(name = "diary")
public class Diary {
}
```

### `@Entity`

이 클래스가 JPA에서 관리할 데이터 객체라는 뜻이다.

쉽게 말하면 다음 관계를 만든다.

```text
Diary 객체 한 개
↕
diary 테이블의 행 한 개
```

### `@Table`

```java
@Table(name = "diary")
```

연결할 테이블 이름이 `diary`라는 뜻이다.

## Diary 필드

```java
@Id
@Column(length = 36)
private String id;
```

일기를 구분하는 기본키다. UUID 문자열을 저장하기 위해 길이를 36으로 지정했다.

```java
@Column(nullable = false)
private Long date;
```

일기 날짜를 숫자로 저장한다. `nullable = false`이므로 반드시 값이 있어야 한다.

```java
@Column(nullable = false, length = 2000)
private String content;
```

일기 내용을 저장한다. 최대 길이는 2,000자다.

```java
@Column(name = "emotion_id", nullable = false)
private Integer emotionId;
```

감정 번호를 저장한다. Java 필드 이름은 `emotionId`이고 DB 컬럼 이름은 `emotion_id`다.

```java
@Column(name = "created_at", updatable = false)
private LocalDateTime createdAt;

@Column(name = "updated_at")
private LocalDateTime updatedAt;
```

일기를 만든 시각과 마지막으로 수정한 시각을 저장한다.

테이블 구조를 간단히 표현하면 다음과 같다.

```text
diary
├─ id: varchar(36), 기본키
├─ date: bigint, 필수
├─ content: varchar(2000), 필수
├─ emotion_id: integer, 필수
├─ created_at: datetime
└─ updated_at: datetime
```

## UUID로 ID 생성

```java
this.id = UUID.randomUUID().toString();
```

UUID는 거의 중복되지 않는 고유한 식별자다.

예시는 다음과 같다.

```text
39b8143c-94a1-4df1-85a8-6c934bcd2818
```

일기를 저장할 때마다 새로운 UUID를 만들어 `id`로 사용한다.

## `@PrePersist`와 `@PreUpdate`

### 최초 저장 전

```java
@PrePersist
protected void onCreate() {
    this.id = UUID.randomUUID().toString();
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
}
```

`@PrePersist`가 붙은 메서드는 Entity가 처음 INSERT되기 직전에 자동으로 실행된다.

```text
diaryRepository.save(diary)
→ onCreate() 자동 실행
→ UUID와 생성·수정 시각 설정
→ INSERT SQL 실행
```

### 수정 전

```java
@PreUpdate
protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
}
```

`@PreUpdate`가 붙은 메서드는 UPDATE SQL이 실행되기 전에 자동으로 실행된다.

따라서 일기를 수정하면 `updatedAt`도 현재 시각으로 바뀐다.

## 생성자와 Builder

```java
@Builder
public Diary(Long date, String content, Integer emotionId) {
    this.date = date;
    this.content = content;
    this.emotionId = emotionId;
}
```

`@Builder`를 사용하면 다음처럼 필드 이름을 보면서 객체를 만들 수 있다.

```java
Diary diary = Diary.builder()
        .date(request.getDate())
        .content(request.getContent())
        .emotionId(request.getEmotionId())
        .build();
```

값이 무엇을 뜻하는지 쉽게 알 수 있고, 생성자 인자의 순서를 실수할 가능성도 줄어든다.

## 캡슐화와 `update()`

Diary의 필드는 모두 `private`이고 `@Setter`가 없다.

따라서 외부에서 필드를 마음대로 바꿀 수 없다.

```java
public void update(Long date, String content, Integer emotionId) {
    this.date = date;
    this.content = content;
    this.emotionId = emotionId;
}
```

수정할 때는 Entity가 제공하는 `update()` 메서드를 사용한다.

```text
필드 직접 변경 방지
→ 정해진 update() 메서드로 변경
→ 객체의 변경 방식을 한곳에서 관리
```

## DTO

DTO는 계층 사이에서 데이터를 전달하는 객체다.

Entity를 API 요청과 응답에 직접 사용하지 않고 별도의 DTO로 분리했다.

```text
요청 JSON → Request DTO → Entity
Entity → Response DTO → 응답 JSON
```

### `DiaryRequest`

```java
public class DiaryRequest {
    private Long date;
    private String content;
    private Integer emotionId;
}
```

일기 생성과 수정 요청을 받을 때 사용한다.

요청 예시:

```json
{
  "date": 20260816,
  "content": "오늘은 즐거운 하루였다.",
  "emotionId": 1
}
```

`@NoArgsConstructor`는 Spring이 JSON을 Java 객체로 변환할 때 사용할 기본 생성자를 만들어준다.

### `DiaryResponse`

```java
public class DiaryResponse {
    private String id;
    private Long date;
    private String content;
    private Integer emotionId;
}
```

일기 한 개를 응답할 때 사용한다.

```java
public static DiaryResponse from(Diary diary) {
    return DiaryResponse.builder()
            .id(diary.getId())
            .date(diary.getDate())
            .content(diary.getContent())
            .emotionId(diary.getEmotionId())
            .build();
}
```

`from()`은 Diary Entity를 DiaryResponse DTO로 변환한다.

```text
Diary Entity
→ DiaryResponse.from(diary)
→ DiaryResponse DTO
```

### `DiaryListResponse`

```java
public class DiaryListResponse {
    private List<DiaryResponse> items;
    private int total;
}
```

일기 목록과 전체 개수를 함께 응답한다.

응답 예시:

```json
{
  "items": [
    {
      "id": "39b8143c-94a1-4df1-85a8-6c934bcd2818",
      "date": 20260816,
      "content": "오늘은 즐거운 하루였다.",
      "emotionId": 1
    }
  ],
  "total": 1
}
```

## DiaryRepository

```java
public interface DiaryRepository
        extends JpaRepository<Diary, String> {
}
```

`JpaRepository<Diary, String>`의 의미는 다음과 같다.

```text
Diary  → 관리할 Entity
String → Diary의 ID 타입
```

JpaRepository를 상속하면 기본 CRUD 메서드를 직접 구현하지 않아도 된다.

```java
diaryRepository.save(diary);
diaryRepository.findById(id);
diaryRepository.findAll();
diaryRepository.delete(diary);
```

Spring Data JPA가 실행 시점에 Repository 구현 객체를 자동으로 만들어준다.

## 메서드 이름으로 쿼리 만들기

```java
List<Diary> findByDateBetweenOrderByDateDesc(
        Long from,
        Long to
);
```

Spring Data JPA는 메서드 이름을 읽고 필요한 쿼리를 자동으로 만든다.

```text
findBy
→ 조건 조회

DateBetween
→ date가 from과 to 사이

OrderByDateDesc
→ date 내림차순 정렬
```

대략 다음 SQL과 같은 역할을 한다.

```sql
SELECT *
FROM diary
WHERE date BETWEEN ? AND ?
ORDER BY date DESC;
```

오래된 날짜부터 조회하는 메서드도 있다.

```java
findByDateBetweenOrderByDateAsc(from, to)
```

- `Asc`: 오름차순, 오래된 날짜부터
- `Desc`: 내림차순, 최신 날짜부터

## DiaryService

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {
}
```

DiaryService는 일기 기능의 실제 처리 순서를 담당한다.

### `@Service`

이 클래스가 서비스 계층이라는 것을 Spring에 알려준다.

### `@RequiredArgsConstructor`

`final` 필드를 받는 생성자를 Lombok이 만들어준다.

```java
private final DiaryRepository diaryRepository;
```

Spring은 생성자를 통해 DiaryRepository를 주입한다.

### 읽기 전용 트랜잭션

```java
@Transactional(readOnly = true)
```

클래스의 기본 작업을 읽기 전용으로 설정한다.

목록 조회와 단건 조회는 데이터를 변경하지 않으므로 읽기 전용 트랜잭션을 사용한다.

데이터를 생성·수정·삭제하는 메서드에는 별도의 `@Transactional`을 붙인다.

```java
@Transactional
public DiaryResponse create(...) {
}
```

## 목록 조회

```java
public DiaryListResponse list(Long from, Long to, String sort)
```

`sort` 값에 따라 Repository 메서드를 선택한다.

```java
List<Diary> diaries = "oldest".equals(sort)
        ? diaryRepository.findByDateBetweenOrderByDateAsc(from, to)
        : diaryRepository.findByDateBetweenOrderByDateDesc(from, to);
```

```text
sort=oldest
→ 오래된 날짜부터 조회

그 외 값 또는 기본값 latest
→ 최신 날짜부터 조회
```

조회한 Entity 목록은 Response DTO 목록으로 변환한다.

```java
List<DiaryResponse> items = diaries.stream()
        .map(DiaryResponse::from)
        .toList();
```

기존 반복문으로 표현하면 다음과 같은 뜻이다.

```java
List<DiaryResponse> items = new ArrayList<>();

for (Diary diary : diaries) {
    items.add(DiaryResponse.from(diary));
}
```

## 단건 조회

```java
Diary diary = diaryRepository.findById(id)
        .orElseThrow(() ->
                new IllegalArgumentException("Diary not found: " + id));
```

`findById()`의 결과는 `Optional<Diary>`다.

```text
일기가 있음  → Diary 반환
일기가 없음  → Optional.empty()
```

`orElseThrow()`는 결과가 비어 있으면 예외를 발생시킨다.

## 일기 생성

```java
@Transactional
public DiaryResponse create(DiaryRequest request)
```

처리 순서는 다음과 같다.

```text
DiaryRequest 받기
→ Diary.builder()로 Entity 생성
→ diaryRepository.save() 호출
→ @PrePersist 실행
→ INSERT SQL 실행
→ DiaryResponse로 변환
```

## 일기 수정과 변경 감지

```java
@Transactional
public DiaryResponse update(String id, DiaryRequest request)
```

수정할 일기를 조회한 후 Entity의 값을 변경한다.

```java
diary.update(
        request.getDate(),
        request.getContent(),
        request.getEmotionId()
);
```

별도의 `save()` 호출이 없지만 트랜잭션이 끝날 때 JPA가 변경된 필드를 발견한다.

```text
Diary 조회
→ update()로 값 변경
→ 트랜잭션 종료
→ JPA가 변경 감지
→ @PreUpdate 실행
→ UPDATE SQL 자동 실행
```

이 기능을 변경 감지 또는 Dirty Checking이라고 한다.

## 일기 삭제

```java
Diary diary = diaryRepository.findById(id)
        .orElseThrow(...);

diaryRepository.delete(diary);
```

삭제 전에 일기가 존재하는지 확인한다.

일기가 있으면 DELETE SQL을 실행하고, 없으면 예외를 발생시킨다.

## DiaryController

```java
@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {
}
```

DiaryController는 `/api/diaries`로 들어오는 HTTP 요청을 받는다.

### `@RestController`

메서드가 반환한 객체를 JSON 응답으로 변환한다.

### `@RequestMapping`

이 Controller의 공통 주소를 지정한다.

```text
/api/diaries
```

## API 목록

| 기능 | HTTP 메서드 | 주소 | 성공 상태 |
|---|---|---|---:|
| 일기 목록 조회 | GET | `/api/diaries` | 200 |
| 일기 단건 조회 | GET | `/api/diaries/{id}` | 200 |
| 일기 생성 | POST | `/api/diaries` | 201 |
| 일기 수정 | PUT | `/api/diaries/{id}` | 200 |
| 일기 삭제 | DELETE | `/api/diaries/{id}` | 204 |

### 목록 조회

```http
GET /api/diaries?from=20260801&to=20260831&sort=latest
```

```java
@RequestParam Long from
```

`@RequestParam`은 URL의 쿼리 파라미터를 받는다.

`sort`를 보내지 않으면 기본값은 `latest`다.

```java
@RequestParam(defaultValue = "latest") String sort
```

### 단건 조회

```http
GET /api/diaries/{id}
```

```java
@PathVariable String id
```

`@PathVariable`은 URL 경로 안의 값을 받는다.

### 생성

```http
POST /api/diaries
Content-Type: application/json
```

```java
@RequestBody DiaryRequest request
```

`@RequestBody`는 요청 JSON을 DiaryRequest 객체로 변환한다.

생성에 성공하면 `201 Created`를 반환한다.

### 수정

```http
PUT /api/diaries/{id}
Content-Type: application/json
```

URL에서 일기 ID를 받고 요청 본문에서 변경할 내용을 받는다.

### 삭제

```http
DELETE /api/diaries/{id}
```

삭제에 성공하면 응답 본문 없이 `204 No Content`를 반환한다.

## ResponseEntity

`ResponseEntity`는 HTTP 상태 코드와 응답 본문을 함께 설정할 때 사용한다.

```java
ResponseEntity.ok(response)
```

```text
200 OK와 응답 본문
```

```java
ResponseEntity.status(HttpStatus.CREATED).body(response)
```

```text
201 Created와 응답 본문
```

```java
ResponseEntity.noContent().build()
```

```text
204 No Content와 빈 본문
```

## 이번 단계의 한계

이 커밋은 CRUD의 기본 구조를 만드는 단계이므로 아직 다음 기능은 없다.

- 요청값 검증
- 통일된 예외 응답
- 사용자와 일기의 연결
- 로그인과 인증
- 본인이 작성한 일기인지 확인하는 소유권 검사

이 기능들은 이후 커밋에서 단계적으로 추가된다.

## 이번 단계 요약

```text
Diary CRUD
├─ Diary Entity와 diary 테이블 연결
├─ UUID 기본키 자동 생성
├─ 생성·수정 시각 자동 기록
├─ Request와 Response DTO 분리
├─ JpaRepository로 DB 접근
├─ 메서드 이름 기반 날짜 조회 쿼리
├─ Service에서 CRUD 로직 처리
├─ 트랜잭션과 변경 감지 사용
└─ Controller에서 REST API 제공
```

이번 커밋을 통해 감정 일기 프로젝트의 가장 기본 기능인 일기 생성, 조회, 수정, 삭제가 가능해졌다.
