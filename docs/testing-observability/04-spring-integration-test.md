# 04. Spring Boot 통합 테스트

## 학습 목표

- 단위 테스트와 Spring 통합 테스트의 차이를 설명할 수 있다.
- 검증 목적에 따라 `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`를 선택할 수 있다.
- MockMvc로 HTTP 요청·응답, Bean Validation, JSON을 검증할 수 있다.
- H2와 테스트 프로파일로 DB 환경을 분리할 수 있다.
- Transaction 롤백으로 테스트 데이터를 격리할 수 있다.

## 핵심 개념

### 단위 테스트와 통합 테스트

Mockito 단위 테스트는 Service만 실제로 실행하고 Repository를 Mock으로 바꿔 로직을 빠르게 검증한다. 하지만 다음 내용은 확인하지 못한다.

- Spring이 Bean을 올바르게 생성·주입하는가?
- `@Transactional`이 실제로 적용되는가?
- JPA 쿼리가 DB에서 올바른 결과를 반환하는가?
- JSON, Validation, Security 필터가 요청을 올바르게 처리하는가?

통합 테스트는 Spring Context와 필요한 인프라를 로드해 이런 **구성 요소 사이의 연동**을 검증한다.

| 구분 | 단위 테스트 | 통합 테스트 |
| --- | --- | --- |
| 주요 대상 | 메서드·클래스 하나 | 여러 계층·프레임워크·DB |
| 외부 의존성 | 대부분 Mock | 필요한 부분을 실제로 실행 |
| 속도 | 매우 빠름 | 상대적으로 느림 |
| 주요 질문 | 로직이 정확한가? | 실제 구성으로 연결되는가? |

둘은 대체 관계가 아니다. 단위 테스트를 충분히 작성하고, 프레임워크 연동이 중요한 경로를 통합 테스트로 보완한다.

### Spring Boot 테스트 선택 기준

| 애너테이션 | 로드 범위 | 주요 용도 |
| --- | --- | --- |
| `@SpringBootTest` | 전체 Spring Context | 여러 계층·Security·DB를 연결한 흐름 |
| `@WebMvcTest` | Controller, MVC, JSON, Validation | HTTP 매핑·상태 코드·응답 JSON |
| `@DataJpaTest` | Entity, Repository, EntityManager | JPA 매핑·쿼리·정렬·필터 |
| `@JsonTest` | Jackson 관련 구성 | JSON 직렬화·역직렬화 |
| `@RestClientTest` | HTTP Client 관련 구성 | 외부 API Client |

**가장 작은 범위로 시작**한다. Controller의 JSON 응답은 `@WebMvcTest`, Repository 쿼리는 `@DataJpaTest`, 회원가입부터 DB 저장까지 필요하면 `@SpringBootTest`를 선택한다.

### `@SpringBootTest`

`@SpringBootTest`는 메인 애플리케이션을 기준으로 Spring Bean을 전부 스캔한다.

```java
@SpringBootTest
class EmotiondiaryApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

빈 테스트처럼 보이지만 Bean 누락·순환 참조·설정값 문제가 있으면 Context를 만드는 도중 실패한다.

| `webEnvironment` | 동작 |
| --- | --- |
| `MOCK` | 기본값. 서버 소켓 없이 MockMvc로 MVC 요청 실행 |
| `RANDOM_PORT` | 내장 서버를 랜덤 포트로 기동해 실제 HTTP 호출 |
| `DEFINED_PORT` | 설정된 포트로 서버 기동 |
| `NONE` | 웹 환경 없이 Context만 로드 |

### MockMvc

MockMvc는 네트워크 소켓을 열지 않고 Spring MVC에 HTTP 요청을 보내는 테스트 도구다.

```java
mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(login)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"));
```

- `perform()`: 요청 실행
- `contentType()` / `content()`: JSON 요청 본문 설정
- `status()`: HTTP 상태 코드 검증
- `jsonPath()`: JSON 필드 검증
- `andDo(print())`: 요청과 응답 출력

MockMvc는 MVC 흐름을 검증하지만 실제 TCP 통신은 하지 않는다. 실제 내장 서버와 네트워크까지 확인하려면 `RANDOM_PORT`를 사용한다.

### `@WebMvcTest`

`@WebMvcTest(DiaryController.class)`는 Controller와 MVC 관련 구성만 로드한다. Service는 로드하지 않으므로 Spring Boot 4의 `@MockitoBean`으로 가짜 Bean을 제공한다.

```java
@WebMvcTest(DiaryController.class)
class DiaryControllerSliceTest {
    @Autowired MockMvc mvc;
    @MockitoBean DiaryService diaryService;
}
```

`@MockitoBean`은 Spring Context의 Bean을 Mock으로 교체한다. Spring을 모르는 순수 Mockito `@Mock`과 다르다.

emotiondiary의 슬라이스 테스트는 Controller 매핑·Validation·JSON에 집중하기 위해 JWT 필터를 제외하고 `@AutoConfigureMockMvc(addFilters = false)`를 사용한다. Security 전체 흐름은 별도 통합 테스트로 보완해야 한다.

### `@DataJpaTest`

`@DataJpaTest`는 Entity·Repository·EntityManager 등 JPA 관련 구성만 로드한다. 각 테스트는 기본적으로 Transaction 안에서 실행되고 종료 후 롤백된다.

```java
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DiaryRepositoryTest {
}
```

`Replace.NONE`은 `application-test.yaml`에 명시한 H2 DataSource를 그대로 사용하게 한다.

```java
em.persist(diary);
em.flush(); // INSERT를 DB에 반영
em.clear(); // 1차 캐시를 비워 다음 조회가 SELECT를 실행하게 함
```

H2 MySQL 호환 모드는 일반적인 JPA 쿼리를 빠르게 검증할 때 유용하다. 다만 MariaDB 전용 문법·인덱스·실행 계획까지 동일하지는 않으므로 DB 차이가 중요하면 Testcontainers MariaDB를 사용한다.

### 테스트 프로파일

`src/test/resources/application-test.yaml`에 H2와 테스트용 JWT 설정을 둔다.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:emotiondiary_test;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop

jwt:
  member:
    secret: test-secret-test-secret-test-secret-test
    access-exp-min: 30
    refresh-exp-min: 1440
```

`@ActiveProfiles("test")`를 붙이면 이 파일이 활성화된다. 테스트가 로컬 MariaDB나 운영 설정을 사용하지 않게 분리하는 것이 중요하다.

### 테스트 데이터 롤백

`@Transactional`이 적용된 테스트는 메서드가 끝날 때 변경 사항을 롤백한다. 테스트 순서와 관계없이 같은 초기 상태를 유지하기 쉽다.

`RANDOM_PORT`로 실제 HTTP 서버를 띄우면 요청 처리 스레드와 테스트 스레드의 Transaction이 다르다. 이때는 테스트의 `@Transactional`만으로 서버가 저장한 데이터를 롤백할 수 없어 `@Sql`이나 명시적 정리가 필요하다.

### 커스텀 인증 사용자

`@WithMockUser`와 `.with(user(...))`는 기본 `UserDetails`를 만든다. emotiondiary Controller는 `CustomUserDetails.getId()`를 사용하므로 ID가 든 실제 타입을 주입해야 한다.

```java
CustomUserDetails principal = new CustomUserDetails(
        1L, "u@example.com", "password", "USER");

var auth = new UsernamePasswordAuthenticationToken(
        principal, null, principal.getAuthorities());

mvc.perform(get("/api/diaries")
        .with(authentication(auth))
        .param("from", "0")
        .param("to", "999"));
```

이렇게 하면 `@AuthenticationPrincipal CustomUserDetails principal`에 ID가 포함된 객체가 전달된다.

## 프로젝트 적용

4단원의 테스트는 검증 범위에 따라 다음과 같이 나뉘다.

| 파일 | 테스트 유형 | 검증 내용 |
| --- | --- | --- |
| `EmotiondiaryApplicationTests.java` | `@SpringBootTest` | 전체 Spring Context 기동 |
| `integration/AuthControllerIntegrationTest.java` | 전체 Context + MockMvc | 회원가입 → 로그인, JWT 발급, Validation |
| `controller/DiaryControllerSliceTest.java` | `@WebMvcTest` | 일기 API의 상태 코드와 JSON 응답 |
| `repository/DiaryRepositoryTest.java` | `@DataJpaTest` | 날짜 범위 정렬과 일기 소유권 쿼리 |
| `resources/application-test.yaml` | 테스트 설정 | H2와 테스트용 JWT 설정 |

H2 드라이버는 테스트 실행 시에만 필요하다.

```groovy
testRuntimeOnly 'com.h2database:h2'
```

## 실습 내용

### 1. 회원가입 → 로그인 통합 테스트

전체 Context와 실제 Security 설정, Service, Repository, H2를 사용했다.

```text
Given: 테스트용 이메일·비밀번호·닉네임
When:  회원가입 요청 후 같은 계정으로 로그인 요청
Then:  201 Created 후 accessToken·refreshToken·Bearer 응답
```

잘못된 비밀번호 형식에 `400 Bad Request`와 `VALIDATION_ERROR`가 반환되는지도 확인했다.

> MockMvc를 사용하므로 여러 계층을 연결한 통합 테스트이지만, 실제 네트워크 소켓을 통한 완전한 E2E 테스트는 아니다.

### 2. DiaryController 슬라이스 테스트

실제 `DiaryService` 대신 `@MockitoBean`을 사용해 Controller의 책임만 확인했다.

- `GET /api/diaries`: Service의 결과가 JSON 목록으로 변환되는가?
- `POST /api/diaries`: `emotionId=99` 요청이 Bean Validation에서 거부되는가?
- 응답 상태 코드와 `$.items[0].content`, `$.code`가 정확한가?

### 3. DiaryRepository 슬라이스 테스트

H2에 User와 Diary를 직접 저장한 뒤 Repository 메서드를 실제로 실행했다.

- 지정 날짜 범위 안의 일기만 조회되는가?
- 결과가 최신 날짜순으로 정렬되는가?
- `findByIdAndUser_Id()`가 작성자 본인의 일기만 반환하는가?

## 실행 및 검증

프로젝트 루트에서 테스트 범위별로 실행한다.

```bash
# 4단원 테스트 전체
./gradlew test \
  --tests 'com.example.emotiondiary.EmotiondiaryApplicationTests' \
  --tests 'com.example.emotiondiary.integration.AuthControllerIntegrationTest' \
  --tests 'com.example.emotiondiary.controller.DiaryControllerSliceTest' \
  --tests 'com.example.emotiondiary.repository.DiaryRepositoryTest'

# 회원가입·로그인 통합 테스트
./gradlew test --tests 'com.example.emotiondiary.integration.AuthControllerIntegrationTest'

# Controller 슬라이스
./gradlew test --tests 'com.example.emotiondiary.controller.DiaryControllerSliceTest'

# Repository 슬라이스
./gradlew test --tests 'com.example.emotiondiary.repository.DiaryRepositoryTest'
```

실행 후 다음을 확인한다.

- 회원가입과 로그인이 H2를 사용해 정상 처리되는가?
- JWT 응답 필드와 Validation 오류 JSON이 예상과 같은가?
- Controller 테스트에서 실제 Service가 호출되지 않는가?
- Repository의 정렬·기간·소유권 조건이 정확한가?
- 테스트 종료 후 데이터가 롤백되는가?

## 문제와 해결

### 테스트가 로컬 MariaDB에 연결하려고 한다

`@ActiveProfiles("test")`가 있는지, `src/test/resources/application-test.yaml` 경로가 정확한지 확인한다. H2 의존성도 필요하다.

### `@WebMvcTest`에서 Service Bean을 찾지 못한다

`@WebMvcTest`는 Service를 자동으로 로드하지 않는다. Spring Boot 4에서는 `@MockitoBean`으로 Context에 등록한다.

```java
@MockitoBean
DiaryService diaryService;
```

### `principal.getId()`에서 `NullPointerException`이 발생한다

`.with(user(...))`나 `@WithMockUser`가 만든 객체는 `CustomUserDetails`가 아니다. `UsernamePasswordAuthenticationToken`과 `.with(authentication(...))`을 사용해 ID가 든 `CustomUserDetails`를 주입한다.

### 예상과 다르게 401 또는 403이 반환된다

무엇을 검증하는지 먼저 구분한다.

- Controller 매핑과 Validation만 검증: JWT 필터 제외 또는 `addFilters = false`
- 실제 인증·인가까지 검증: 전체 Context에 Security 필터를 적용하고 유효한 인증 정보 사용

필터를 끄면 Security는 검증되지 않으므로 별도 통합 테스트로 보완한다.

### `@DataJpaTest`에서 테이블 또는 SQL 오류가 발생한다

- Entity 매핑과 H2 호환 문법을 확인한다.
- `@ActiveProfiles("test")`와 `Replace.NONE`을 확인한다.
- 실제 SELECT를 확인하려면 `em.flush()`와 `em.clear()`를 호출한다.
- MariaDB 전용 기능이 원인이면 Testcontainers MariaDB를 고려한다.

### 테스트 데이터가 다른 테스트에 영향을 준다

`@DataJpaTest`는 기본적으로 롤백한다. `@SpringBootTest`에서는 `@Transactional`을 확인한다. 비동기 코드나 `RANDOM_PORT`처럼 다른 스레드의 데이터는 `@Sql` 또는 명시적 정리가 필요하다.

### 통합 테스트가 너무 느리다

전체 Context가 필요하지 않은 테스트까지 `@SpringBootTest`로 작성했는지 확인한다. HTTP 계약은 `@WebMvcTest`, JPA 쿼리는 `@DataJpaTest`, 순수 로직은 Mockito 단위 테스트로 내린다.

## 정리

- 통합 테스트는 Spring Bean·Security·JSON·JPA·DB가 실제로 연결되는지 검증한다.
- 검증 범위에 맞는 가장 작은 테스트를 선택한다.
- 전체 흐름은 `@SpringBootTest`, HTTP 계약은 `@WebMvcTest`, JPA 쿼리는 `@DataJpaTest`가 기본 선택이다.
- `@MockitoBean`은 Spring Context의 Bean을 Mock으로 교체하며 `@Mock`과 다르다.
- `@ActiveProfiles("test")`와 H2로 로컬·운영 DB에서 테스트를 분리한다.
- `@Transactional` 롤백은 데이터 격리에 유용하지만 다른 스레드의 변경까지 되돌리지는 못한다.
- MockMvc는 네트워크 없이 MVC 흐름을 검증하며, 실제 HTTP가 필요하면 `RANDOM_PORT`를 사용한다.
- 다음 단원에서는 기능 정확성을 넘어 TPS·p95·오류율로 성능을 측정한다.
