# 01. 개발 환경 구축

- 커밋: `e0a3d0a`
- 커밋 메시지: `chore: 개발 환경 구축`

## 이번 단계에서 한 일

감정 일기 프로젝트를 시작하기 위한 기본 Spring Boot 프로젝트를 만들었다.

아직 일기 작성이나 회원가입 같은 기능은 없다. 앞으로 기능을 추가할 수 있도록 Java, Spring Boot, Gradle과 필요한 라이브러리를 준비한 단계다.

## 사용한 개발 환경

| 항목 | 내용 |
|---|---|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 4.1.0 |
| 빌드 도구 | Gradle |
| 데이터베이스 | MariaDB |
| 테스트 | JUnit |

## 생성된 주요 파일

```text
emotiondiary/
├─ build.gradle
├─ settings.gradle
├─ gradlew
├─ gradlew.bat
├─ gradle/wrapper/
├─ src/main/java/com/example/emotiondiary/
│  └─ EmotiondiaryApplication.java
├─ src/main/resources/
│  └─ application.yaml
└─ src/test/java/com/example/emotiondiary/
   └─ EmotiondiaryApplicationTests.java
```

### `build.gradle`

프로젝트를 빌드하는 방법과 사용할 라이브러리를 적어두는 파일이다.

쉽게 말하면 프로젝트에 필요한 도구 목록이다.

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```

이 설정은 프로젝트가 Java 17을 사용한다는 뜻이다.

### 주요 라이브러리

#### Spring Web MVC

```groovy
implementation 'org.springframework.boot:spring-boot-starter-webmvc'
```

HTTP 요청을 받고 REST API를 만들 때 사용한다.

예를 들면 다음과 같은 주소를 처리할 수 있게 해준다.

```text
GET /api/diaries
POST /api/diaries
```

#### Spring Data JPA

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
```

Java 객체를 데이터베이스 테이블과 연결하고, 데이터를 저장하거나 조회할 때 사용한다.

#### Validation

```groovy
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

클라이언트가 보낸 값이 올바른지 검사할 때 사용한다.

```java
@NotBlank
private String content;
```

#### MariaDB Driver

```groovy
runtimeOnly 'org.mariadb.jdbc:mariadb-java-client'
```

Spring Boot 애플리케이션이 MariaDB와 통신할 수 있게 해준다.

이 단계에서는 드라이버만 추가했고 실제 DB 접속 정보는 다음 단계에서 설정한다.

#### Lombok

```groovy
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
```

Getter, 생성자, Builder처럼 반복되는 코드를 어노테이션으로 자동 생성해준다.

```java
@Getter
@RequiredArgsConstructor
```

#### DevTools

```groovy
developmentOnly 'org.springframework.boot:spring-boot-devtools'
```

개발 중 코드가 변경되면 애플리케이션을 다시 시작하는 데 도움을 준다.

## 애플리케이션 시작 클래스

`EmotiondiaryApplication.java`는 프로젝트의 시작점이다.

```java
@SpringBootApplication
public class EmotiondiaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmotiondiaryApplication.class, args);
    }
}
```

`main()` 메서드를 실행하면 Spring Boot 애플리케이션이 시작된다.

```text
main() 실행
→ SpringApplication.run() 호출
→ Spring 설정 읽기
→ 필요한 객체 생성
→ 내장 웹 서버 시작
→ HTTP 요청을 받을 준비 완료
```

### `@SpringBootApplication`

Spring Boot 애플리케이션의 시작 클래스라는 표시다.

이 어노테이션이 붙은 클래스가 있는 패키지를 기준으로 Controller, Service, Repository 같은 Spring 객체를 자동으로 찾는다.

## 애플리케이션 설정

`application.yaml`에는 애플리케이션 이름을 설정했다.

```yaml
spring:
  application:
    name: emotiondiary
```

서버 로그 등에 프로젝트 이름이 `emotiondiary`로 표시된다.

## Gradle Wrapper

다음 파일들은 Gradle Wrapper와 관련된 파일이다.

```text
gradlew
gradlew.bat
gradle/wrapper/
```

Gradle Wrapper를 사용하면 컴퓨터에 Gradle을 따로 설치하지 않아도 프로젝트에 정해진 Gradle 버전으로 명령을 실행할 수 있다.

macOS와 Linux에서는:

```bash
./gradlew bootRun
```

Windows에서는:

```text
gradlew.bat bootRun
```

macOS에서 실행 권한 오류가 발생하면 다음 명령으로 권한을 추가할 수 있다.

```bash
chmod +x gradlew
```

## 기본 테스트

`EmotiondiaryApplicationTests.java`에는 Spring Boot가 정상적으로 시작되는지 확인하는 기본 테스트가 있다.

```java
@SpringBootTest
class EmotiondiaryApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

`contextLoads()` 안에 코드가 없어도 Spring 애플리케이션 설정을 불러오는 과정에서 문제가 생기면 테스트가 실패한다.

테스트 실행 명령은 다음과 같다.

```bash
./gradlew test
```

데이터베이스 라이브러리가 이미 추가되어 있으므로 실제 실행과 테스트를 완료하려면 다음 단계에서 DB 접속 설정이 필요하다.

## Git 관련 파일

### `.gitignore`

빌드 결과물이나 IntelliJ 설정처럼 Git에 올릴 필요가 없는 파일을 지정한다.

```text
build/
.idea/
*.iml
```

### `.gitattributes`

운영체제가 달라도 Git에서 파일의 줄바꿈 등을 일정하게 관리할 수 있도록 돕는다.

## 이번 단계 요약

```text
개발 환경 구축
├─ Java 17 설정
├─ Spring Boot 프로젝트 생성
├─ Gradle Wrapper 추가
├─ Web MVC 추가
├─ JPA와 MariaDB 드라이버 추가
├─ Validation과 Lombok 추가
├─ 애플리케이션 시작 클래스 생성
└─ 기본 테스트 생성
```

이번 커밋은 기능을 구현한 단계가 아니라, 앞으로 Diary CRUD와 회원 기능을 구현하기 위한 프로젝트의 기초를 만든 단계다.
