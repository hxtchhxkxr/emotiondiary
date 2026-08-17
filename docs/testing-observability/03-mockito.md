# 03. Mockito

## 학습 목표

- Service가 의존하는 Repository를 가짜 객체로 대체할 수 있다.
- Mock·Stub·Spy의 차이를 설명할 수 있다.
- Stubbing으로 Mock의 반환값과 예외를 지정할 수 있다.
- `verify()`로 협력 객체의 호출 여부와 횟수를 검증할 수 있다.
- `ArgumentCaptor`로 Mock에 전달된 객체의 내용을 검증할 수 있다.
- Mockito 단위 테스트와 Spring 통합 테스트의 경계를 구분할 수 있다.

## 핵심 개념

### Mockito가 필요한 이유

`DiaryService.create()`는 사용자를 조회하고 새 일기를 저장하기 위해 `UserRepository`와 `DiaryRepository`를 사용한다.

이를 실제 DB로 테스트하면 Service 로직과 DB 연동을 함께 검증하게 된다. 하지만 지금 확인하고 싶은 것이 **Service의 분기·변환·호출 로직**이라면 DB는 필요 없는 변수다.

Mockito는 이런 협력 객체를 통제 가능한 가짜 객체로 바꿔준다.

```text
진짜 테스트: DiaryService → Repository → DB
Mockito 테스트: DiaryService → Mock Repository → 미리 정한 답
```

이렇게 하면 DB와 Spring Context를 실행하지 않아 빠르고, 항상 같은 조건을 재현할 수 있다.

### Test Double: 진짜를 대신하는 객체

| 종류 | 간단한 의미 | 예시 |
| --- | --- | --- |
| Dummy | 자리만 채우고 사용하지 않음 | 호출되지 않는 매개변수 |
| Stub | 미리 정한 값을 반환함 | 조회 결과로 가짜 일기 목록 반환 |
| Spy | 기본적으로 실제 코드를 실행하고 호출을 기록함 | 일부 메서드만 가짜로 바꾸기 |
| Mock | 반환값과 호출 방식을 통제함 | `save()`가 1회 호출되었는지 검증 |
| Fake | 간단하지만 실제로 동작하는 구현체 | 메모리에 저장하는 Repository |

가장 중요한 구분은 다음과 같다.

- **Stub**: 어떤 값을 반환할지 준비한다.
- **Mock**: 어떤 메서드가 어떻게 호출됐는지 검증한다.

Mockito의 `@Mock` 객체는 둘 다 할 수 있다.

### Mockito 테스트의 기본 구성

```java
@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    @Mock
    DiaryRepository diaryRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    DiaryService diaryService;
}
```

- `@ExtendWith(MockitoExtension.class)`: JUnit이 Mockito 애너테이션을 처리하게 한다.
- `@Mock`: 진짜 구현을 실행하지 않는 가짜 객체를 만든다.
- `@InjectMocks`: 테스트 대상을 만들고 `@Mock` 객체를 주입한다.

`@InjectMocks`는 생성자 → Setter → 필드 순으로 주입을 시도한다. emotiondiary의 `DiaryService`는 생성자 주입을 사용하므로 Mockito가 Repository Mock을 자동으로 넣을 수 있다.

> `@InjectMocks`는 Spring의 의존성 주입이 아니다. Spring Context, `@Autowired`, `@Value`, 트랜잭션, AOP는 동작하지 않는다.

### `@Mock`과 `@Spy`

`@Mock`은 실제 메서드를 실행하지 않고 기본값을 반환한다.

- 참조 타입: `null`
- 숫자 기본형: `0`
- `boolean`: `false`
- 컬렉션: 보통 빈 컬렉션

`@Spy`는 실제 객체를 감싸므로 기본적으로 실제 메서드가 실행된다. 일부 메서드만 Stubbing할 때 사용하지만, 실제 코드에 부수 효과가 있다면 주의해야 한다.

### Stubbing: Mock의 답 준비하기

Stubbing은 “이 메서드가 이 인자로 호출되면 이 값을 반환해”라고 지정하는 것이다.

```java
when(userRepository.findById(1L))
        .thenReturn(Optional.of(user));

when(diaryRepository.save(any(Diary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
```

- `thenReturn(value)`: 고정된 값을 반환한다.
- `thenAnswer(...)`: 호출 인자를 이용해 동적인 결과를 만든다.
- `thenThrow(exception)`: 예외를 발생시킨다.

`save()`에 받은 엔티티를 그대로 반환하는 `thenAnswer()`는 Repository 단위 테스트에서 자주 사용하는 패턴이다.

```java
when(diaryRepository.save(any(Diary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
```

반환값이 없는 `void` 메서드는 `doNothing()`이나 `doThrow()`를 사용한다.

```java
doNothing().when(diaryRepository).delete(any());
doThrow(new RuntimeException("DB down"))
        .when(diaryRepository).delete(any());
```

### ArgumentMatcher: 호출 인자 맞추기

Stubbing 시 지정한 인자와 실제 호출 인자가 맞아야 준비한 답이 반환된다. UUID·시간·새로 생성된 객체처럼 정확한 값을 예측하기 어려울 때 Matcher를 사용한다.

```java
when(userRepository.findById(anyLong()))
        .thenReturn(Optional.of(user));

when(diaryRepository.findByIdAndUser_Id(eq("id-1"), anyLong()))
        .thenReturn(Optional.of(diary));
```

주요 Matcher:

- `any()`: 어떤 참조 값이든 허용
- `any(Diary.class)`: `Diary` 타입의 값 허용
- `anyLong()`: 어떤 `long` 값이든 허용
- `eq(value)`: 특정 값과 같은지 검사

> 하나의 메서드 호출에서 Matcher를 하나라도 사용하면 나머지 인자도 Matcher로 작성해야 한다. 예: `eq("id-1"), anyLong()`

### `verify()`: 협력 객체의 행위 검증

Stubbing은 Mock이 반환할 값을 준비한다. `verify()`는 테스트 대상이 Mock을 올바르게 사용했는지 확인한다.

```java
verify(diaryRepository).save(any());
verify(diaryRepository, times(1)).delete(diary);
verify(diaryRepository, never()).delete(any());
```

| 검증 | 의미 |
| --- | --- |
| `verify(mock).method()` | 정확히 1회 호출 |
| `times(n)` | 정확히 n회 호출 |
| `never()` | 한 번도 호출되지 않음 |
| `atLeastOnce()` | 1회 이상 호출 |
| `atMost(n)` | n회 이하 호출 |
| `verifyNoInteractions(mock)` | 해당 Mock이 전혀 사용되지 않음 |

모든 내부 호출을 과도하게 검증하면 리팩터링만 해도 테스트가 깨질 수 있다. 저장·삭제·외부 알림 전송 같은 **중요한 협력 행위**만 검증한다.

### `ArgumentCaptor`: 전달된 인자 검증

Service 내부에서 `new` 또는 Builder로 생성한 객체는 테스트 밖에서 미리 동일한 객체를 만들기 어렵다. `ArgumentCaptor`는 Repository에 실제로 넘어간 객체를 꺼내 필드를 검증하게 해준다.

```java
ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
verify(diaryRepository).save(captor.capture());

Diary saved = captor.getValue();
assertThat(saved.getContent()).isEqualTo("좋은 하루");
assertThat(saved.getEmotionId()).isEqualTo(5);
assertThat(saved.getUser()).isSameAs(user);
```

여러 번 호출된 인자는 `captor.getAllValues()`로 확인할 수 있다. `@Captor` 필드로 선언해도 된다.

### 예외 Stubbing

실제 DB 장애나 제약조건 위반을 반복 재현하기는 어렵다. Mockito로는 외부 의존성의 실패를 쉽게 만들 수 있다.

```java
when(diaryRepository.save(any(Diary.class)))
        .thenThrow(new DataIntegrityViolationException("UK"));

assertThatThrownBy(() -> diaryService.create(1L, request))
        .isInstanceOf(DataIntegrityViolationException.class);
```

이 테스트는 Repository의 예외를 Service가 잡지 않고 상위로 전파한다는 현재 설계를 기록한다.

### BDDMockito

BDDMockito는 Mockito의 기능을 Given-When-Then 용어로 표현한다.

```java
given(userRepository.findById(1L))
        .willReturn(Optional.of(user));

// when: diaryService.create(...) 실행

then(diaryRepository).should()
        .save(any(Diary.class));
```

| 기본 Mockito | BDDMockito |
| --- | --- |
| `when(...).thenReturn(...)` | `given(...).willReturn(...)` |
| `verify(mock).method()` | `then(mock).should().method()` |

기능적 차이는 거의 없으므로 두 스타일 중 팀의 약속에 맞는 한 가지를 일관되게 사용하면 된다.

## 프로젝트 적용

Mockito 학습 코드는 다음 파일에 나누어 있다.

| 파일 | 검증 대상 |
| --- | --- |
| `sample/MockVsSpyTest.java` | `@Mock`과 `@Spy`의 기본 동작 차이 |
| `service/DiaryServiceListTest.java` | 목록 조회 결과의 DTO 변환과 ArgumentMatcher |
| `service/DiaryServiceDeleteTest.java` | 삭제 호출·미호출에 대한 행위 검증 |
| `service/DiaryServiceCreateTest.java` | Stubbing, 예외, `ArgumentCaptor`, BDDMockito 종합 실습 |

이 테스트들은 `@SpringBootTest`를 사용하지 않는다. 따라서 Spring Context와 DB를 로드하지 않고 `DiaryService`의 Java 로직만 빠르게 검증한다.

> Spring Boot 4 통합 테스트에서 Context의 Bean을 Mock으로 교체할 때는 `@MockitoBean`을 사용한다. 순수 Mockito의 `@Mock`과 역할이 다르며, 이 내용은 4단원에서 다룬다.

## 실습 내용

### 1. 목록 조회

Repository의 최신순 조회 결과를 미리 준비하고, Service가 이를 `DiaryListResponse`로 올바르게 변환하는지 검증했다.

```text
Given: Repository가 셋째·둘째·첫째 일기를 반환하도록 Stubbing
When:  diaryService.list(..., "desc") 실행
Then:  전체 개수가 3이고 내용이 최신순으로 변환됨
```

### 2. 일기 삭제

일기가 있을 때와 없을 때의 행위를 나누어 검증했다.

```text
일기가 있음 → delete(diary)가 1회 호출됨
일기가 없음 → DiaryNotFoundException, delete()는 0회 호출됨
```

### 3. 일기 생성

1. `UserRepository.findById()`의 사용자 조회 결과를 준비했다.
2. `DiaryRepository.save()`가 받은 엔티티를 그대로 반환하도록 Stubbing했다.
3. Service가 반환한 DTO의 내용과 감정 ID를 검증했다.
4. `ArgumentCaptor`로 Repository에 전달된 `Diary`의 날짜·내용·감정·사용자를 검증했다.
5. 사용자가 없거나 저장 중 예외가 발생하는 실패 흐름도 검증했다.

## 실행 및 검증

프로젝트 루트에서 실행한다.

```bash
# Mockito 학습 테스트 전체
./gradlew test --tests 'com.example.emotiondiary.sample.MockVsSpyTest' \
  --tests 'com.example.emotiondiary.service.DiaryService*Test'

# 특정 클래스
./gradlew test --tests 'com.example.emotiondiary.service.DiaryServiceCreateTest'

# 특정 메서드
./gradlew test --tests 'com.example.emotiondiary.service.DiaryServiceCreateTest.create_success'
```

테스트가 통과하면 다음을 확인한 것이다.

- Service의 진짜 로직이 실행됐다.
- Repository는 DB 대신 미리 준비한 결과를 반환했다.
- 저장·삭제와 같은 필수 협력 행위가 예상대로 호출됐다.
- 사용자 미존재·Repository 실패 같은 예외 흐름도 재현됐다.

## 문제와 해결

### `@Mock` 필드가 `null`이다

테스트 클래스에 Mockito 확장을 추가했는지 확인한다.

```java
@ExtendWith(MockitoExtension.class)
```

### Stubbing했는데 `null`이 반환된다

실제 호출 인자와 Stubbing 인자가 서로 다를 가능성이 크다. 호출 코드를 확인하고 `eq()`, `anyLong()`, `any()` 등으로 의도한 범위를 매칭한다.

### Matcher 사용 중 `InvalidUseOfMatchersException`

하나의 호출에 일반 값과 Matcher를 섞어 사용했는지 확인한다.

```java
// 잘못된 예
when(repository.find(eq(1L), "desc"));

// 올바른 예
when(repository.find(eq(1L), eq("desc")));
```

### `UnnecessaryStubbingException`

준비한 Stubbing이 실제 테스트에서 사용되지 않았다는 뜻이다. 가장 좋은 해결은 필요 없는 Stubbing을 삭제하는 것이다. Lenient 설정으로 경고를 숨기기 전에 테스트 준비 코드가 과한지 확인한다.

### `@InjectMocks`가 의존성을 주입하지 못한다

생성자의 매개변수 타입과 `@Mock` 필드 타입이 맞는지 확인한다. 같은 타입의 의존성이 여러 개면 Mockito가 어느 Mock을 넣을지 판단하기 어려울 수 있다. 이런 경우 테스트에서 생성자를 직접 호출하는 방법도 명확하다.

### Spy Stubbing 중 실제 메서드가 실행된다

`when(spy.method()).thenReturn(value)`는 Stubbing 과정에서 실제 메서드를 호출할 수 있다. 실제 호출을 피하려면 다음 형식을 사용한다.

```java
doReturn(value).when(spy).method();
```

### Mock을 너무 많이 준비해야 한다

하나의 테스트에 협력 객체와 Stubbing이 너무 많다면 Service가 너무 많은 역할을 담고 있을 수 있다. 역할을 나누거나, 여러 구성 요소의 연동 자체가 중요하다면 통합 테스트로 올리는 것을 고려한다.

## 정리

- Mockito는 DB·외부 API 같은 협력 객체를 격리해 Service 로직만 빠르게 검증하게 해준다.
- `@Mock`으로 가짜 협력 객체를 만들고 `@InjectMocks`로 테스트 대상에 주입한다.
- Stubbing은 Mock의 **답**을 준비하고, `verify()`는 Mock에 대한 **행위**를 검증한다.
- Matcher를 하나라도 사용하면 해당 호출의 모든 인자를 Matcher로 작성한다.
- `ArgumentCaptor`는 Service 내부에서 생성되어 Repository로 전달된 객체를 검증할 때 유용하다.
- Repository 쿼리·트랜잭션·Spring Bean 연동은 Mockito 단위 테스트가 아니라 통합 테스트에서 검증한다.
