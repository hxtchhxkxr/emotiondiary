# 06. Diary와 User의 연관관계

- 커밋: `179ad11`
- 커밋 메시지: `feat: Diary에 User 연관관계 추가(@ManyToOne)`

## 이번 단계에서 한 일

이전까지 Diary와 User는 서로 연결되지 않은 독립적인 데이터였다.

```text
User
└─ 사용자 정보만 저장

Diary
└─ 일기 정보만 저장
```

따라서 어떤 사용자가 어떤 일기를 작성했는지 알 수 없었다.

이번 커밋에서는 Diary에 User 연관관계를 추가해 각 일기의 작성자를 저장하도록 변경했다.

```text
User 1명
└─ Diary 여러 개 작성 가능

Diary 1개
└─ 반드시 User 1명에게 소속
```

## 변경된 파일

```text
src/main/java/com/example/emotiondiary/
├─ entity/
│  └─ Diary.java
├─ repository/
│  └─ DiaryRepository.java
├─ service/
│  └─ DiaryService.java
└─ controller/
   └─ DiaryController.java
```

## 연관관계란?

연관관계는 서로 관련된 두 데이터를 연결하는 것이다.

감정 일기 프로젝트에서는 사용자와 일기가 서로 관련되어 있다.

```text
users 테이블
┌────┬──────────────────┐
│ id │ email            │
├────┼──────────────────┤
│ 1  │ user@example.com │
└────┴──────────────────┘

diary 테이블
┌───────┬─────────┬─────────┐
│ id    │ content │ user_id │
├───────┼─────────┼─────────┤
│ aaa   │ 일기 1  │ 1       │
│ bbb   │ 일기 2  │ 1       │
└───────┴─────────┴─────────┘
```

`diary.user_id = 1`은 해당 일기를 `users.id = 1`인 사용자가 작성했다는 뜻이다.

## 다대일 관계

Diary에 다음 필드가 추가됐다.

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

`@ManyToOne`은 여러 Diary가 한 User를 가리키는 다대일 관계를 의미한다.

```text
Diary 여러 개 N
→ User 한 명 1

N : 1 관계
```

예를 들어 사용자 1번이 일기를 세 개 작성하면:

```text
Diary A ─┐
Diary B ─┼─→ User 1
Diary C ─┘
```

각 Diary는 작성자 한 명을 가지지만, User 한 명은 여러 Diary의 작성자가 될 수 있다.

## `@ManyToOne`

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
```

### `fetch = FetchType.LAZY`

LAZY는 User 정보가 실제로 필요할 때 조회하겠다는 뜻이다.

Diary를 조회했다고 해서 항상 User의 모든 정보까지 즉시 가져오지 않는다.

```text
Diary 조회
→ Diary 정보 먼저 조회
→ diary.getUser()가 실제로 필요할 때 User 조회
```

일기 목록을 조회할 때 작성자의 비밀번호나 이메일이 필요하지 않다면 불필요한 조회를 줄일 수 있다.

반대 방식인 `EAGER`는 Diary를 조회할 때 연결된 User도 즉시 조회한다.

```text
LAZY
└─ 필요할 때 조회

EAGER
└─ 처음부터 함께 조회
```

### `optional = false`

```java
optional = false
```

Diary 객체에는 반드시 User가 있어야 한다는 뜻이다.

```java
Diary.builder()
        .user(user)
        .date(...)
        .build();
```

작성자가 없는 일기는 허용하지 않는다는 객체 관계의 규칙을 표현한다.

## `@JoinColumn`

```java
@JoinColumn(name = "user_id", nullable = false)
```

Diary와 User를 연결할 DB 컬럼 이름을 `user_id`로 지정한다.

```text
Diary.user
↕
diary.user_id
↕
users.id
```

### `nullable = false`

DB의 `user_id` 컬럼에 `NULL`을 저장할 수 없게 한다.

`optional = false`는 JPA 객체 관계에서 User가 필수라는 의미이고, `nullable = false`는 DB 컬럼에서도 값이 필수라는 의미다.

두 설정을 함께 사용해 작성자가 없는 일기가 저장되지 않도록 한다.

## 외래키

`diary.user_id`는 `users.id`를 가리키는 외래키가 된다.

외래키는 연결된 데이터가 실제로 존재하는지 DB가 확인하도록 도와준다.

```text
diary.user_id = 1
→ users 테이블에 id = 1인 사용자가 있어야 함
```

존재하지 않는 사용자 ID를 일기에 저장하려 하면 DB가 거부한다.

Hibernate가 만드는 테이블 관계를 단순화하면 다음과 같다.

```sql
ALTER TABLE diary
ADD COLUMN user_id BIGINT NOT NULL;

ALTER TABLE diary
ADD CONSTRAINT diary_user_fk
FOREIGN KEY (user_id)
REFERENCES users (id);
```

실제 제약조건 이름은 Hibernate가 자동으로 만들기 때문에 다르게 표시될 수 있다.

## Diary 생성자 변경

기존 Diary 생성자는 날짜, 내용, 감정만 받았다.

```java
public Diary(Long date, String content, Integer emotionId)
```

이번 커밋부터 User도 함께 받는다.

```java
@Builder
public Diary(
        User user,
        Long date,
        String content,
        Integer emotionId
) {
    this.user = user;
    this.date = date;
    this.content = content;
    this.emotionId = emotionId;
}
```

따라서 새 일기를 만들 때 작성자를 반드시 지정해야 한다.

```java
Diary diary = Diary.builder()
        .user(user)
        .date(request.getDate())
        .content(request.getContent())
        .emotionId(request.getEmotionId())
        .build();
```

## Repository의 사용자 조건

기존 목록 조회는 날짜만 조건으로 사용했다.

```java
findByDateBetweenOrderByDateDesc(from, to)
```

이 방식은 모든 사용자의 일기를 함께 조회할 수 있다는 문제가 있다.

이번 커밋에서는 사용자 ID 조건을 추가했다.

```java
findByUser_IdAndDateBetweenOrderByDateDesc(
        Long userId,
        Long from,
        Long to
)
```

메서드 이름을 부분별로 나누면 다음과 같다.

```text
findBy
→ 조건에 맞는 데이터 조회

User_Id
→ Diary.user.id가 userId와 같은지 확인

And
→ 두 조건을 모두 만족

DateBetween
→ date가 from과 to 사이

OrderByDateDesc
→ 최신 날짜부터 정렬
```

대략 다음 SQL과 같은 역할을 한다.

```sql
SELECT *
FROM diary
WHERE user_id = ?
  AND date BETWEEN ? AND ?
ORDER BY date DESC;
```

오래된 날짜부터 조회하는 메서드도 같은 방식으로 변경됐다.

```java
findByUser_IdAndDateBetweenOrderByDateAsc(
        userId,
        from,
        to
)
```

## 중첩 속성 조회

```text
User_Id
```

여기서 `_`는 연결된 객체 안의 필드로 이동한다는 뜻이다.

```text
Diary.user
→ User.id
```

Java 코드로 표현하면 다음 값을 조건으로 사용하는 것이다.

```java
diary.getUser().getId()
```

Spring Data JPA가 이 메서드 이름을 분석해 JOIN 또는 외래키 조건이 포함된 쿼리를 만든다.

## 일기 ID와 사용자 ID를 함께 조회

```java
Optional<Diary> findByIdAndUser_Id(
        String id,
        Long userId
);
```

일기 ID만 조회하지 않고 사용자 ID도 함께 확인한다.

```text
일기 ID가 같음
그리고
일기의 작성자 ID도 같음
→ 조회 성공
```

대략 다음 SQL과 같은 역할을 한다.

```sql
SELECT *
FROM diary
WHERE id = ?
  AND user_id = ?;
```

이 조건이 소유권 검사의 기초가 된다.

## 소유권이란?

소유권은 해당 데이터가 현재 사용자의 것인지 확인하는 것이다.

예를 들어 사용자 1이 작성한 일기를 사용자 2가 수정하면 안 된다.

```text
Diary ID = abc
작성자 ID = 1

사용자 1이 요청
→ id = abc AND user_id = 1
→ 조회 성공

사용자 2가 요청
→ id = abc AND user_id = 2
→ 조회 실패
```

조회 결과가 없으면 `DiaryNotFoundException`을 발생시킨다.

```java
Diary diary = diaryRepository.findByIdAndUser_Id(id, userId)
        .orElseThrow(() -> new DiaryNotFoundException(id));
```

실제로 일기가 존재하더라도 다른 사용자의 일기라면 조회 결과를 없는 것처럼 처리한다.

이렇게 하면 다른 사람의 일기 존재 여부를 불필요하게 알려주지 않을 수 있다.

## DiaryService 변경

DiaryService의 모든 주요 메서드가 `userId`를 받도록 변경됐다.

```java
list(Long userId, Long from, Long to, String sort)
getById(Long userId, String id)
create(Long userId, DiaryRequest request)
update(Long userId, String id, DiaryRequest request)
delete(Long userId, String id)
```

이전에는 일기 ID나 날짜만 사용했지만, 이제는 모든 처리에 사용자 ID를 포함한다.

```text
일기 목록 조회
→ 해당 사용자의 일기만 조회

일기 단건 조회
→ 해당 사용자의 일기인지 확인

일기 생성
→ 해당 사용자를 작성자로 연결

일기 수정
→ 해당 사용자의 일기만 수정

일기 삭제
→ 해당 사용자의 일기만 삭제
```

## UserRepository 주입

일기를 생성할 때 User Entity가 필요하므로 UserRepository를 추가했다.

```java
private final UserRepository userRepository;
```

`@RequiredArgsConstructor`가 생성자를 만들고 Spring이 UserRepository를 주입한다.

## 일기 생성 흐름

```java
User user = userRepository.findById(userId)
        .orElseThrow(() ->
                new IllegalStateException("User not found: " + userId));
```

먼저 사용자 ID로 작성자 User를 조회한다.

사용자가 존재하면 해당 User를 Diary에 넣는다.

```java
Diary diary = Diary.builder()
        .user(user)
        .date(request.getDate())
        .content(request.getContent())
        .emotionId(request.getEmotionId())
        .build();
```

전체 흐름은 다음과 같다.

```text
userId와 DiaryRequest 전달
→ userRepository.findById(userId)
→ 사용자 존재 여부 확인
→ User를 포함한 Diary 생성
→ diaryRepository.save(diary)
→ diary.user_id에 사용자 ID 저장
→ DiaryResponse 반환
```

사용자가 없다면 다음 예외가 발생한다.

```text
IllegalStateException: User not found: 사용자ID
```

이 시점에는 아직 User 전용 공통 예외가 없으므로 `IllegalStateException`을 사용한다.

## 목록 조회 흐름

```text
userId, from, to, sort 전달
→ sort 값 확인
→ 사용자 ID와 날짜 범위로 DB 조회
→ 해당 사용자의 Diary만 반환
→ DiaryResponse 목록으로 변환
→ DiaryListResponse 반환
```

## 단건 조회·수정·삭제 흐름

세 기능은 공통적으로 일기 ID와 사용자 ID를 함께 사용한다.

```text
diaryId와 userId 전달
→ findByIdAndUser_Id(diaryId, userId)
├─ 두 조건 모두 일치 → 처리 계속
└─ 일치하지 않음 → DiaryNotFoundException
```

따라서 다른 사용자의 일기는 조회, 수정, 삭제할 수 없다.

## Controller의 임시 사용자 ID

이번 커밋 시점에는 아직 로그인과 JWT 인증이 없다.

그래서 Controller에 임시 사용자 ID를 사용했다.

```java
private static final Long TEMP_USER_ID = 1L;
```

모든 API 요청에서 사용자 ID 1을 Service에 전달한다.

```java
diaryService.list(TEMP_USER_ID, from, to, sort);
diaryService.getById(TEMP_USER_ID, id);
diaryService.create(TEMP_USER_ID, request);
diaryService.update(TEMP_USER_ID, id, request);
diaryService.delete(TEMP_USER_ID, id);
```

따라서 이 단계의 API를 정상적으로 테스트하려면 DB에 ID가 1인 사용자가 있어야 한다.

```text
TEMP_USER_ID = 1
→ users 테이블에 id = 1인 User 필요
```

`TEMP_USER_ID`는 로그인 기능을 만들기 전까지만 사용하는 임시 코드다.

이후 JWT 인증 필터를 구현하면 다음처럼 실제 로그인 사용자의 ID를 사용하도록 변경된다.

```java
@AuthenticationPrincipal CustomUserDetails principal
```

```java
principal.getId()
```

## 기존 데이터에 주의하기

기존 `diary` 테이블에 작성자가 없는 일기가 이미 저장되어 있을 수 있다.

이번 커밋에서는 `user_id`를 필수 컬럼으로 추가한다.

```text
기존 Diary 데이터
└─ user_id 값이 없음

새 테이블 규칙
└─ user_id는 NULL 불가
```

이 경우 Hibernate의 테이블 변경이 실패할 수 있다.

학습용 데이터라면 기존 일기를 삭제한 뒤 다시 생성하거나, 기존 데이터에 사용자 ID를 먼저 지정해야 한다.

운영 환경에서는 이런 변경을 `ddl-auto: update`에 맡기기보다 DB 마이그레이션으로 안전하게 처리하는 것이 좋다.

## 연관관계 적용 전과 후

### 적용 전

```text
Diary
├─ id
├─ date
├─ content
└─ emotionId

문제
└─ 작성자가 누구인지 알 수 없음
```

### 적용 후

```text
Diary
├─ id
├─ date
├─ content
├─ emotionId
└─ user → User

결과
├─ 작성자 저장 가능
├─ 사용자별 목록 조회 가능
└─ 다른 사용자의 일기 접근 방지 가능
```

## 이번 단계의 한계

이번 커밋에서는 일기 소유권을 확인할 수 있는 DB 조회 구조를 만들었지만, 실제 로그인 사용자를 확인하는 인증 기능은 아직 없다.

현재는 모든 요청에서 `TEMP_USER_ID = 1`을 사용한다.

아직 다음 기능이 필요하다.

- 회원가입 API
- 비밀번호 암호화
- 로그인과 JWT 발급
- JWT에서 실제 사용자 ID 추출
- `@AuthenticationPrincipal` 적용

이후 커밋에서 인증 기능이 추가되면 임시 ID가 제거되고 실제 로그인 사용자 기준으로 동작하게 된다.

## 이번 단계 요약

```text
Diary와 User 연관관계
├─ Diary에 User 필드 추가
├─ @ManyToOne으로 N:1 관계 설정
├─ LAZY 방식으로 User 조회
├─ diary.user_id 외래키 추가
├─ 작성자가 없는 Diary 저장 방지
├─ Repository 쿼리에 userId 조건 추가
├─ 사용자별 일기 목록 조회
├─ 사용자 소유 일기만 조회·수정·삭제
├─ 일기 생성 시 User 연결
└─ 인증 전까지 TEMP_USER_ID 사용
```

이번 커밋을 통해 모든 일기에 작성자가 연결됐고, 사용자별로 자신의 일기만 처리할 수 있는 소유권 구조의 기초가 만들어졌다.
