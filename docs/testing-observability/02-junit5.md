# 02. JUnit 5

## 학습 목표

- JUnit의 구조와 테스트 실행 방식을 이해한다.
- 테스트 생명주기와 주요 애너테이션을 사용할 수 있다.
- JUnit Assertions와 AssertJ로 값·객체·콜렉션·예외를 검증할 수 있다.
- 반복되는 검증을 매개변수화 테스트로 작성할 수 있다.
- Gradle로 전체·클래스·메서드 단위의 테스트를 실행할 수 있다.

## 핵심 개념

### JUnit이란?

JUnit은 Java의 대표적인 테스트 프레임워크이다. IntelliJ, Gradle, CI가 JUnit의 실행 결과를 이해하므로 같은 테스트를 로컬과 배포 파이프라인에서 반복 실행할 수 있다.

JUnit의 구조는 크게 세 부분으로 나뉘다.

| 구성 요소 | 역할 |
| --- | --- |
| JUnit Platform | IDE와 Gradle이 테스트를 발견하고 실행하는 기반 |
| JUnit Jupiter | `@Test`, `@BeforeEach`, Assertions 등 현재 사용하는 API |
| JUnit Vintage | 기존 JUnit 3·4 테스트를 실행하는 호환 엔진 |

emotiondiary는 Spring Boot 4의 기능별 테스트 스타터를 사용한다. 필요한 JUnit Jupiter·AssertJ·Mockito가 이미 포함되어 있어 별도로 JUnit 의존성을 추가할 필요가 없다.

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
testImplementation 'org.springframework.boot:spring-boot-starter-validation-test'
testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

tasks.named('test') {
    useJUnitPlatform()
}
```

### 테스트 생명주기

| 애너테이션 | 실행 시점 |
| --- | --- |
| `@BeforeAll` | 클래스의 모든 테스트 시작 전 1회 |
| `@BeforeEach` | 각 테스트 시작 전 |
| `@Test` | 테스트 본문 |
| `@AfterEach` | 각 테스트 종료 후 |
| `@AfterAll` | 클래스의 모든 테스트 종료 후 1회 |
| `@Disabled("이유")` | 해당 테스트를 임시로 실행하지 않음 |

두 개의 테스트가 있다면 다음 순서로 실행된다.

```text
BeforeAll
  BeforeEach → 첫 번째 테스트 → AfterEach
  BeforeEach → 두 번째 테스트 → AfterEach
AfterAll
```

`@BeforeAll`과 `@AfterAll`은 기본적으로 `static` 메서드여야 한다. `@BeforeEach`에서 각 테스트에 필요한 객체를 새로 만들면 테스트 간 영향을 줄일 수 있다.

### 읽기 쉬운 테스트 구조

- `@DisplayName`: 메서드명 대신 테스트 리포트에 보여줄 이름을 지정한다.
- `@Nested`: 관련 시나리오를 내부 클래스로 묶어 계층으로 표현한다.

```java
@DisplayName("Diary 도메인")
class DiaryDomainTest {

    @Nested
    @DisplayName("일기 작성 시")
    class WriteDiary {

        @Test
        @DisplayName("내용과 감정이 정상 저장된다")
        void write_success() {
            // 검증 코드
        }
    }
}
```

IDE의 테스트 결과에서 `Diary 도메인 > 일기 작성 시 > 내용과 감정이 정상 저장된다`처럼 읽을 수 있다.

### JUnit Assertions

Assertion은 **실제 결과가 예상 결과와 같은지 판단**하는 코드다.

| 메서드 | 검증 내용 |
| --- | --- |
| `assertEquals(expected, actual)` | 두 값이 같음 |
| `assertTrue(condition)` | 조건이 `true` |
| `assertFalse(condition)` | 조건이 `false` |
| `assertNull(value)` | 값이 `null` |
| `assertNotNull(value)` | 값이 `null`이 아님 |
| `assertArrayEquals(expected, actual)` | 배열의 길이와 내용이 같음 |
| `assertThrows(Type.class, action)` | 예상한 예외가 발생함 |
| `assertTimeout(duration, action)` | 지정한 시간 안에 종료됨 |

`assertAll` 안에 여러 검증을 넣으면 하나가 실패해도 나머지를 모두 실행한 뒤 실패 결과를 한번에 보여준다.

```java
assertAll("Diary 필드",
        () -> assertEquals(5, diary.getEmotionId()),
        () -> assertEquals("좋은 하루", diary.getContent()),
        () -> assertNotNull(diary.getDate())
);
```

### AssertJ

AssertJ는 `assertThat(검증 대상)` 뒤에 검증 조건을 연결하는 방식을 사용한다. 컬렉션·객체·예외 검증이 읽기 쉽고 실패 메시지도 자세하다.

```java
assertThat(emotions)
        .hasSize(5)
        .contains("happy", "calm")
        .doesNotContain("bored");

assertThat(diary)
        .extracting("content", "emotionId")
        .containsExactly("hello", 3);

assertThatThrownBy(() -> Integer.parseInt("x"))
        .isInstanceOf(NumberFormatException.class)
        .hasMessageContaining("x");
```

단순한 동등·`true`·`null` 검증은 JUnit Assertions로도 충분하다. 컬렉션이나 여러 조건을 연결하는 검증에는 AssertJ가 편리하다.

### Assumptions: 조건에 따른 실행

Assumption은 테스트의 전제조건이다. 조건이 맞지 않으면 테스트를 실패시키지 않고 **Skipped**로 처리한다.

```java
@Test
@DisplayName("prod 환경이 아닐 때만 실행")
void onlyOnNonProd() {
    assumeFalse("prod".equals(System.getenv("PROFILE")));

    assertThat(1 + 1).isEqualTo(2);
}
```

OS·환경 변수·특정 외부 조건이 필요한 테스트에 사용한다. 일반적인 비즈니스 규칙을 Assumption으로 건너뛰면 안 된다.

### 매개변수화 테스트

`@ParameterizedTest`는 같은 검증 로직을 여러 입력값으로 반복한다. 입력만 다른 테스트 메서드를 여러 개 만드는 중복을 줄일 수 있다.

| 데이터 제공 방식 | 적합한 상황 |
| --- | --- |
| `@ValueSource` | 숫자·문자열 같은 단일 값 |
| `@CsvSource` | 여러 입력값과 기대 결과의 조합 |
| `@MethodSource` | `null`, 객체, 복잡한 조합, 동적 데이터 |

```java
@ParameterizedTest(name = "[{index}] emotionId={0}은 유효 범위이다")
@ValueSource(ints = {1, 2, 3, 4, 5})
void emotionId_validRange(int emotionId) {
    assertThat(emotionId).isBetween(1, 5);
}
```

`@MethodSource`가 참조하는 메서드는 기본적으로 `static`이어야 하며, 테스트 메서드의 매개변수와 같은 순서의 `Arguments`를 반환해야 한다.

### 반복·동적·시간 제한 테스트

- `@RepeatedTest`: 같은 테스트를 지정한 횟수만큼 반복한다.
- `@TestFactory`: 실행 중에 `DynamicTest`를 만들어 가변적인 케이스를 검증한다.
- `@Timeout`: 테스트가 지정한 시간을 넘기면 실패시킨다.

`@RepeatedTest`는 간헐적 실패나 난수 기반 로직을 확인할 때 도움이 된다. 다만 반복해서 통과했다고 로직의 완전한 정확성이 보장되는 것은 아니다.

`@TestFactory`는 외부 파일이나 DB에서 읽은 가변적인 데이터로 테스트를 만들 때 적합하다. 단순한 고정 입력 반복은 `@ParameterizedTest`가 더 읽기 쉽다.

## 프로젝트 적용

2단원 예제는 `src/test/java/com/example/emotiondiary/sample/` 아래에 정리했다.

| 파일 | 학습 내용 |
| --- | --- |
| `LifecycleTest.java` | `@BeforeAll`·`@BeforeEach`·`@AfterEach`·`@AfterAll` 실행 순서 |
| `DiaryDomainTest.java` | `@Nested`, Assertions, AssertJ, Assumptions, 매개변수화·반복·동적·Timeout 테스트 |

예제를 `sample` 패키지에 두어 Service·Controller·Repository의 실전 테스트와 구분했다. 이 단원의 목적은 Spring Context나 DB를 로드하는 것이 아니라 **JUnit 기능 자체를 단위 테스트로 익히는 것**이다.

## 실습 내용

`DiaryDomainTest`에서 다음 기능을 직접 작성하고 실행했다.

1. `@Nested`와 `@DisplayName`으로 작성·수정 시나리오를 계층화했다.
2. `assertAll`로 `Diary`의 날짜·내용·감정 필드를 한번에 검증했다.
3. AssertJ로 감정 목록, 객체 필드, 예외 메시지를 검증했다.
4. Assumption으로 환경에 따라 테스트를 스킵하는 방법을 확인했다.
5. `@ValueSource`·`@CsvSource`·`@MethodSource`로 다양한 입력을 반복 검증했다.
6. `@RepeatedTest`·`@TestFactory`·`@Timeout`의 용도를 확인했다.

## 실행 및 검증

프로젝트 루트에서 Gradle Wrapper로 실행한다.

```bash
# 전체 테스트
./gradlew test

# DiaryDomainTest 클래스만
./gradlew test --tests 'com.example.emotiondiary.sample.DiaryDomainTest'

# 일반 테스트 메서드 하나만
./gradlew test --tests 'com.example.emotiondiary.sample.DiaryDomainTest.uuid_unique'

# @Nested 내부의 메서드 하나만
./gradlew test --tests 'com.example.emotiondiary.sample.DiaryDomainTest$WriteDiary.write_success'
```

실행 후 생성되는 HTML 리포트에서 통과·실패·스킵 수, 실행 시간, 실패 스택 트레이스를 확인할 수 있다.

```text
build/reports/tests/test/index.html
```

`@DisplayName`은 `System.out.println()`의 콘솔 문자열을 바꾸는 기능이 아니다. IntelliJ의 테스트 트리나 Gradle HTML 리포트에서 확인한다.

## 문제와 해결

### `./gradlew: permission denied`

Gradle Wrapper에 실행 권한이 없는 상태이다.

```bash
chmod +x gradlew
./gradlew test
```

`gradlew`의 실행 권한 변경도 Git에서 파일 변경으로 추적될 수 있다.

### `No tests found for given includes`

`--tests`에 입력한 패키지·클래스·메서드 이름이 실제 코드와 같은지 확인한다. 현재 클래스의 정식 이름은 다음과 같다.

```text
com.example.emotiondiary.sample.DiaryDomainTest
```

`@Nested` 클래스는 외부와 내부 클래스 사이에 `$`를 사용한다. zsh에서 `$`가 해석되지 않도록 명령 전체를 작은따옴표로 감싼다.

### IntelliJ에서 `NoSuchMethodError`가 발생함

IntelliJ의 JUnit 런너와 프로젝트의 JUnit Platform 버전이 맞지 않을 때 발생할 수 있다.

1. IntelliJ를 현재 JUnit Platform을 지원하는 버전으로 업데이트한다.
2. Gradle 설정의 **Run tests using**을 `Gradle`로 지정한다.
3. IDE 문제인지 확인하려면 `./gradlew test`로 먼저 실행한다.

### `@DisplayName`이 콘솔에 보이지 않음

정상일 수 있다. `@DisplayName`은 테스트 리포트용 이름이며 테스트 본문의 출력이 아니다. IntelliJ 테스트 결과 트리나 Gradle HTML 리포트를 확인한다.

### Assumption 조건이 맞지 않음

테스트가 실패하지 않고 **Skipped**로 표시되면 정상이다. 전제조건이 충족되지 않아 검증 본문을 실행하지 않은 것이다.

### `@BeforeAll` 메서드 오류

기본 테스트 인스턴스 전략에서는 `@BeforeAll`과 `@AfterAll` 메서드에 `static`이 필요하다. `static`을 사용하지 않으려면 클래스에 다음 설정을 추가한다.

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
```

## 정리

- JUnit Platform은 테스트를 실행하고, Jupiter는 테스트 작성 API를 제공한다.
- `@BeforeEach`에서 테스트 데이터를 새로 준비하면 각 테스트를 독립적으로 유지하기 쉽다.
- `@Nested`와 `@DisplayName`은 테스트를 비즈니스 시나리오처럼 읽게 해준다.
- 단순 값은 JUnit Assertions, 콜렉션·객체·예외은 AssertJ로 검증하면 편리하다.
- 입력만 다른 반복 검증은 `@ParameterizedTest`로 중복을 줄인다.
- 동적 데이터엔 `@TestFactory`, 반복 실행엔 `@RepeatedTest`, 시간 제한엔 `@Timeout`을 사용한다.
- IDE와 JUnit의 호환 문제가 의심되면 Gradle Wrapper 실행 결과를 기준으로 확인한다.
