# 05. User 도메인 추가

- 커밋: `2392c62`
- 커밋 메시지: `feat: User 도메인 추가`

## 이번 단계에서 한 일

감정 일기 서비스에서 회원 정보를 저장할 수 있도록 User 도메인을 추가했다.

이번 커밋에서는 아직 회원가입이나 로그인 API를 만들지 않았다. 회원 기능을 구현하기 전에 사용자 데이터를 어떤 구조로 저장할지 먼저 정의한 단계다.

```text
User 도메인
├─ User Entity
├─ Role enum
└─ UserRepository
```

## 도메인이란?

도메인은 프로그램이 다루는 중요한 업무 대상을 뜻한다.

감정 일기 프로젝트의 예시는 다음과 같다.

```text
Diary 도메인
└─ 사용자가 작성한 일기

User 도메인
└─ 서비스를 이용하는 회원
```

User 도메인에는 사용자 ID, 이메일, 비밀번호, 닉네임, 권한 같은 정보가 포함된다.

## 추가된 파일

```text
src/main/java/com/example/emotiondiary/
├─ entity/
│  ├─ User.java
│  └─ Role.java
└─ repository/
   └─ UserRepository.java
```

## User Entity

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
}
```

`User`는 사용자 한 명을 표현하는 Java 객체이며, MariaDB의 `users` 테이블과 연결된다.

```text
User 객체 한 개
↕
users 테이블의 행 한 개
```

## `@Entity`

```java
@Entity
```

이 클래스가 JPA에서 관리할 Entity라는 뜻이다.

Spring Boot가 시작되면 Hibernate가 `@Entity`가 붙은 클래스를 찾고 필드 정보를 읽는다.

`ddl-auto: update` 설정이 있으므로 `users` 테이블이 없다면 자동으로 생성을 시도한다.

```text
Spring Boot 실행
→ User의 @Entity 발견
→ 필드와 JPA 어노테이션 분석
→ users 테이블과 비교
→ 테이블이 없으면 CREATE TABLE 실행
```

## `@Table(name = "users")`

```java
@Table(name = "users")
```

User Entity와 연결할 테이블 이름을 `users`로 지정한다.

`user`는 데이터베이스에서 특별한 의미로 사용될 가능성이 있기 때문에, 복수형인 `users`를 테이블 이름으로 사용하면 이름 충돌을 피하기 좋다.

## `@Getter`

```java
@Getter
```

Lombok이 모든 필드의 Getter를 자동으로 만들어준다.

예를 들면 다음 메서드들을 직접 작성하지 않아도 된다.

```java
user.getId();
user.getEmail();
user.getNickname();
user.getRole();
```

Setter는 만들지 않았기 때문에 외부에서 사용자 정보를 마음대로 변경할 수 없다.

## 기본 생성자

```java
@NoArgsConstructor(access = AccessLevel.PROTECTED)
```

JPA가 Entity를 조회해서 객체로 만들 때 사용할 기본 생성자를 Lombok이 생성한다.

`PROTECTED`로 제한했기 때문에 일반 코드에서 의미 없이 빈 User 객체를 만드는 것을 막을 수 있다.

```java
new User(); // 일반적인 외부 코드에서는 사용할 수 없음
```

## User 필드 구조

```text
users
├─ id: bigint, 기본키, 자동 증가
├─ email: varchar(100), 필수, 중복 불가
├─ password: varchar(100), 필수
├─ nickname: varchar(50), 필수
├─ role: USER 또는 ADMIN, 필수
└─ created_at: 생성 시각
```

## 사용자 ID

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

### `@Id`

이 필드가 테이블의 기본키라는 뜻이다.

기본키는 각 사용자를 구분하는 고유한 값이다.

### `@GeneratedValue`

```java
@GeneratedValue(strategy = GenerationType.IDENTITY)
```

사용자 ID를 MariaDB의 자동 증가 기능으로 생성한다.

```text
첫 번째 사용자 → id = 1
두 번째 사용자 → id = 2
세 번째 사용자 → id = 3
```

사용자 객체를 만들 때 ID를 직접 넣지 않아도 DB가 INSERT 시점에 값을 만들어준다.

## 이메일

```java
@Column(nullable = false, unique = true, length = 100)
private String email;
```

각 설정의 의미는 다음과 같다.

```text
nullable = false
└─ 이메일은 반드시 있어야 함

unique = true
└─ 같은 이메일을 두 사용자가 사용할 수 없음

length = 100
└─ 최대 100자
```

이메일에 `unique` 제약조건이 있기 때문에 DB에서도 중복 이메일 저장을 막는다.

```text
test@example.com 최초 저장 → 성공
test@example.com 다시 저장 → DB 중복 오류
```

## 비밀번호

```java
@Column(nullable = false, length = 100)
private String password;
```

사용자 비밀번호를 저장하는 필드다.

코드의 매개변수 이름은 `encodedPassword`로 되어 있다.

```java
public static User create(
        String email,
        String encodedPassword,
        String nickname
)
```

이는 평문 비밀번호가 아니라 BCrypt로 암호화된 결과를 저장하려는 설계다.

```text
평문 비밀번호
test1234

BCrypt 해시 예시
$2a$10$...
```

하지만 이번 커밋에서는 아직 Spring Security와 BCrypt를 연결하지 않았다. 실제 비밀번호 암호화는 이후 커밋에서 적용한다.

## 닉네임

```java
@Column(nullable = false, length = 50)
private String nickname;
```

화면에서 사용자에게 보여줄 이름이다.

- 반드시 값이 있어야 한다.
- DB에는 최대 50자까지 저장한다.

## 사용자 권한

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private Role role;
```

사용자가 일반 회원인지 관리자인지 구분한다.

Role은 별도의 enum으로 정의했다.

```java
public enum Role {
    USER,
    ADMIN
}
```

### enum이란?

enum은 사용할 수 있는 값을 미리 정해놓는 Java 타입이다.

Role에는 두 값만 들어갈 수 있다.

```text
Role.USER
Role.ADMIN
```

다음과 같은 잘못된 값은 사용할 수 없다.

```text
MANAGER
GUEST
ROOT
```

문자열을 직접 사용하는 것보다 오타를 줄이고 사용할 수 있는 값을 분명하게 보여준다.

## `@Enumerated(EnumType.STRING)`

```java
@Enumerated(EnumType.STRING)
```

Role을 DB에 문자열로 저장한다.

```text
Role.USER  → "USER"
Role.ADMIN → "ADMIN"
```

만약 `EnumType.ORDINAL`을 사용하면 enum의 순서 번호인 0, 1로 저장된다. enum 순서가 바뀌면 기존 데이터의 의미가 달라질 수 있으므로 문자열 저장 방식이 더 이해하기 쉽고 안전하다.

## 생성 시각

```java
@Column(name = "created_at", updatable = false)
private LocalDateTime createdAt;
```

사용자가 처음 생성된 시각을 저장한다.

`updatable = false`이므로 이후 사용자를 수정할 때 이 컬럼은 UPDATE 대상에서 제외된다.

```java
@PrePersist
protected void onCreate() {
    this.createdAt = LocalDateTime.now();
}
```

`@PrePersist`는 User가 DB에 처음 저장되기 직전에 실행된다.

```text
userRepository.save(user)
→ @PrePersist 메서드 실행
→ createdAt에 현재 시각 저장
→ INSERT SQL 실행
```

## private 생성자

```java
private User(
        String email,
        String password,
        String nickname,
        Role role
) {
    this.email = email;
    this.password = password;
    this.nickname = nickname;
    this.role = role;
}
```

생성자가 `private`이므로 클래스 외부에서 직접 호출할 수 없다.

```java
new User(...); // 외부에서 호출 불가
```

대신 아래의 정적 팩토리 메서드를 통해 User를 만든다.

## 정적 팩토리 메서드

정적 팩토리 메서드는 객체를 생성해 반환하는 이름 있는 메서드다.

### 일반 사용자 생성

```java
public static User create(
        String email,
        String encodedPassword,
        String nickname
) {
    return new User(
            email,
            encodedPassword,
            nickname,
            Role.USER
    );
}
```

`User.create()`로 생성하면 권한이 자동으로 `USER`가 된다.

```java
User user = User.create(
        "test@example.com",
        "암호화된 비밀번호",
        "홍길동"
);
```

호출하는 쪽에서 `Role.USER`를 매번 전달하지 않아도 된다.

### 관리자 생성

```java
public static User createAdmin(
        String email,
        String encodedPassword,
        String nickname
) {
    return new User(
            email,
            encodedPassword,
            nickname,
            Role.ADMIN
    );
}
```

`User.createAdmin()`으로 생성하면 권한이 자동으로 `ADMIN`이 된다.

```text
User.create()      → 일반 사용자
User.createAdmin() → 관리자
```

메서드 이름만 봐도 어떤 사용자를 만드는지 쉽게 알 수 있다.

## User 생성 흐름

일반 사용자가 만들어지는 전체 흐름은 다음과 같다.

```text
이메일, 암호화된 비밀번호, 닉네임 준비
→ User.create() 호출
→ role을 USER로 설정
→ User 객체 생성
→ userRepository.save() 호출
→ @PrePersist로 createdAt 설정
→ users 테이블에 INSERT
→ DB가 id 자동 생성
```

## UserRepository

```java
public interface UserRepository
        extends JpaRepository<User, Long> {
}
```

`JpaRepository<User, Long>`의 의미는 다음과 같다.

```text
User → 관리할 Entity
Long → User의 ID 타입
```

JpaRepository를 상속했기 때문에 기본적인 DB 메서드를 바로 사용할 수 있다.

```java
userRepository.save(user);
userRepository.findById(id);
userRepository.findAll();
userRepository.delete(user);
```

## 이메일로 사용자 조회

```java
Optional<User> findByEmail(String email);
```

Spring Data JPA가 메서드 이름을 분석해 이메일 조회 쿼리를 자동으로 만든다.

대략 다음 SQL과 같은 역할을 한다.

```sql
SELECT *
FROM users
WHERE email = ?;
```

반환 타입은 `Optional<User>`다.

```text
해당 이메일 사용자 있음
→ Optional 안에 User가 있음

해당 이메일 사용자 없음
→ Optional.empty()
```

나중에 로그인할 때 이메일로 회원을 찾는 데 사용한다.

```java
User user = userRepository.findByEmail(email)
        .orElseThrow(...);
```

## 이메일 존재 여부 확인

```java
boolean existsByEmail(String email);
```

해당 이메일을 사용하는 회원이 있는지만 확인한다.

```text
이메일 존재함  → true
이메일 존재하지 않음 → false
```

회원가입 전에 이메일 중복을 검사할 때 사용한다.

```java
if (userRepository.existsByEmail(email)) {
    // 이미 가입된 이메일 처리
}
```

사용자 전체 정보가 필요하지 않고 존재 여부만 필요할 때 알맞은 메서드다.

## `findByEmail()`과 `existsByEmail()`의 차이

| 메서드 | 반환값 | 사용 목적 |
|---|---|---|
| `findByEmail()` | `Optional<User>` | 사용자 정보가 필요할 때 |
| `existsByEmail()` | `boolean` | 존재 여부만 확인할 때 |

예를 들면:

```text
로그인
→ 이메일로 사용자 정보가 필요
→ findByEmail()

회원가입 이메일 중복 검사
→ 존재 여부만 필요
→ existsByEmail()
```

## DB에 만들어지는 테이블

JPA와 Hibernate는 User Entity를 읽고 대략 다음과 같은 테이블을 만든다.

```sql
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE (email)
);
```

실제 SQL 문법과 컬럼 타입은 Hibernate와 MariaDB 버전에 따라 조금 다르게 출력될 수 있다.

## 이번 단계의 한계

이번 커밋은 User 데이터 구조만 만든 단계다.

아직 다음 기능은 구현되지 않았다.

- 회원가입 요청 DTO
- 회원가입 Controller와 Service
- 이메일 형식과 비밀번호 검증
- 이메일 중복 오류 응답
- BCrypt 비밀번호 암호화
- 로그인 API
- JWT 인증
- User와 Diary의 연관관계

이후 커밋에서 이 기능들이 차례대로 추가된다.

## 이번 단계 요약

```text
User 도메인
├─ User Entity
│  ├─ 자동 증가 Long ID
│  ├─ 중복 불가능한 이메일
│  ├─ 비밀번호
│  ├─ 닉네임
│  ├─ USER 또는 ADMIN 권한
│  └─ 생성 시각 자동 기록
├─ Role enum
│  ├─ USER
│  └─ ADMIN
└─ UserRepository
   ├─ 기본 CRUD
   ├─ findByEmail()
   └─ existsByEmail()
```

이번 커밋을 통해 회원 정보를 DB에 저장하고 이메일로 조회할 수 있는 User 도메인의 기초가 만들어졌다.
