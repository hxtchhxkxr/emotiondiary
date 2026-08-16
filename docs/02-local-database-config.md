# 02. 로컬 데이터베이스 설정 분리

- 커밋: `3b40043`
- 커밋 메시지: `chore: 로컬 DB 설정 파일 분리 및 Git 제외`

## 이번 단계에서 한 일

Spring Boot가 MariaDB에 접속할 수 있도록 데이터베이스 설정을 추가했다.

데이터베이스 주소, 사용자 이름, 비밀번호는 사람마다 다르고 외부에 공개하면 안 되는 정보다. 그래서 공통 설정과 개인 설정을 서로 다른 파일로 분리했다.

```text
application.yaml
└─ 모든 개발자가 함께 사용하는 공통 설정

application-local.yaml
└─ 내 컴퓨터에서만 사용하는 DB 접속 정보
```

`application-local.yaml`은 `.gitignore`에 등록하여 GitHub에 올라가지 않도록 했다.

## 왜 설정 파일을 분리할까?

DB 접속 정보를 공통 설정 파일에 직접 작성하면 다음과 같은 문제가 생긴다.

```yaml
spring:
  datasource:
    username: 실제사용자
    password: 실제비밀번호
```

- GitHub에 비밀번호가 공개될 수 있다.
- 팀원마다 DB 사용자 이름과 비밀번호가 다를 수 있다.
- 개발 환경과 운영 환경의 접속 정보가 서로 다르다.
- 비밀번호가 바뀔 때마다 공통 파일을 수정해야 한다.

따라서 공통 설정에는 환경변수 이름만 작성하고 실제 값은 별도로 관리한다.

## `application.yaml`

이 커밋에서 추가한 공통 설정은 다음과 같다.

```yaml
spring:
  application:
    name: emotiondiary

  profiles:
    active: local

  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

## Spring Profile

```yaml
spring:
  profiles:
    active: local
```

Spring Profile은 실행 환경에 따라 다른 설정을 선택하는 기능이다.

`local` 프로필이 활성화되면 Spring Boot는 다음 두 파일을 함께 읽는다.

```text
application.yaml
application-local.yaml
```

두 파일에 같은 설정이 있으면 `application-local.yaml`의 값이 우선 적용된다.

```text
application.yaml 읽기
→ local 프로필 확인
→ application-local.yaml 추가로 읽기
→ 같은 설정은 local 값으로 덮어쓰기
```

## DataSource 설정

DataSource는 애플리케이션이 데이터베이스 연결을 얻을 때 사용하는 객체다.

쉽게 말하면 Spring Boot와 MariaDB를 이어주는 DB 연결 설정이다.

### 드라이버

```yaml
driver-class-name: org.mariadb.jdbc.Driver
```

MariaDB용 JDBC 드라이버를 사용하겠다는 뜻이다.

JDBC는 Java 애플리케이션이 데이터베이스와 통신하기 위한 표준 방식이다.

### DB 주소

```yaml
url: ${SPRING_DATASOURCE_URL}
```

`${SPRING_DATASOURCE_URL}`은 실제 주소가 아니라 환경변수의 값을 가져오라는 표시다.

환경변수에는 보통 다음과 같은 값이 들어간다.

```text
jdbc:mariadb://localhost:3306/springstudy
```

각 부분의 의미는 다음과 같다.

```text
jdbc:mariadb://localhost:3306/springstudy
     └ MariaDB   └ 내 컴퓨터 └ 포트 └ DB 이름
```

MariaDB가 다른 포트에서 실행 중이라면 실제 포트에 맞게 변경해야 한다.

### 사용자 이름과 비밀번호

```yaml
username: ${SPRING_DATASOURCE_USERNAME}
password: ${SPRING_DATASOURCE_PASSWORD}
```

MariaDB에 로그인할 계정 정보를 환경변수에서 가져온다.

## `application-local.yaml`

이 파일은 Git으로 관리하지 않기 때문에 개발자가 직접 만들어야 한다.

위치는 다음과 같다.

```text
src/main/resources/application-local.yaml
```

예시는 다음과 같다.

```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/데이터베이스이름
    username: 로컬사용자이름
    password: 로컬비밀번호
```

실제 값은 자신의 MariaDB 설정에 맞게 작성한다.

공통 `application.yaml`의 환경변수 설정을 로컬 파일의 값이 덮어쓰기 때문에, 로컬에서는 별도로 환경변수를 등록하지 않아도 실행할 수 있다.

## `.gitignore`

다음 설정을 추가했다.

```gitignore
# 로컬 DB 접속 정보
application-local.yaml
```

Git은 이름이 `application-local.yaml`인 파일을 추적하지 않는다.

따라서 다음 파일에 실제 비밀번호를 작성해도 일반적인 `git add .` 명령에 포함되지 않는다.

```text
src/main/resources/application-local.yaml
```

무시되고 있는지 확인하려면 다음 명령을 사용할 수 있다.

```bash
git check-ignore -v src/main/resources/application-local.yaml
```

출력에 `.gitignore` 규칙이 나타나면 정상이다.

주의할 점은 이미 Git이 추적 중인 파일은 나중에 `.gitignore`에 추가해도 자동으로 제외되지 않는다는 것이다. 이 프로젝트에서는 처음부터 로컬 설정 파일을 추적하지 않도록 구성했다.

## JPA 설정

### `ddl-auto: update`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

애플리케이션을 시작할 때 Entity 클래스와 DB 테이블 구조를 비교한다.

필요한 테이블이나 컬럼이 없으면 Hibernate가 생성하거나 추가한다.

```text
@Entity 클래스 확인
→ 실제 DB 테이블과 비교
→ 필요한 CREATE 또는 ALTER SQL 실행
```

예를 들어 나중에 다음 Entity를 추가하면:

```java
@Entity
public class Diary {
    @Id
    private String id;
}
```

Hibernate가 이에 맞는 테이블 생성을 시도한다.

`update`는 학습과 로컬 개발에서는 편리하지만, 운영 환경에서는 예상하지 않은 테이블 변경을 피하기 위해 Flyway 같은 마이그레이션 도구를 사용하는 편이 안전하다.

### `show-sql: true`

```yaml
show-sql: true
```

Hibernate가 실행하는 SQL을 콘솔에 보여준다.

```sql
select * from diary;
```

어떤 쿼리가 실행되는지 확인하며 학습하거나 문제를 찾을 때 유용하다.

### `format_sql: true`

```yaml
properties:
  hibernate:
    format_sql: true
```

SQL을 여러 줄로 보기 좋게 정리해서 출력한다.

설정하지 않은 경우:

```sql
select d1_0.id,d1_0.content,d1_0.date from diary d1_0
```

설정한 경우:

```sql
select
    d1_0.id,
    d1_0.content,
    d1_0.date
from
    diary d1_0
```

## 애플리케이션 실행 흐름

```text
Spring Boot 실행
→ application.yaml 읽기
→ local 프로필 활성화 확인
→ application-local.yaml 읽기
→ MariaDB 접속 정보 적용
→ DataSource 생성
→ MariaDB 연결
→ JPA와 Hibernate 시작
→ Entity와 테이블 구조 비교
→ 애플리케이션 실행 완료
```

## 자주 발생하는 오류

### 환경변수가 적용되지 않은 경우

다음과 같은 오류가 발생할 수 있다.

```text
Driver org.mariadb.jdbc.Driver claims to not accept jdbcUrl,
${SPRING_DATASOURCE_URL}
```

확인할 내용:

- `application-local.yaml` 파일이 존재하는가?
- 파일명이 정확히 `application-local.yaml`인가?
- 파일을 저장했는가?
- YAML 들여쓰기가 올바른가?

### `Connection refused`

MariaDB가 실행되지 않았거나 설정한 포트와 실제 포트가 다를 때 발생한다.

확인할 내용:

- MariaDB 서비스가 실행 중인가?
- MariaDB가 사용하는 포트가 맞는가?
- JDBC URL의 DB 이름이 맞는가?

### `Access denied`

DB 사용자 이름이나 비밀번호가 틀렸거나 해당 데이터베이스에 접근할 권한이 없을 때 발생한다.

## 이번 단계 요약

```text
로컬 DB 설정 분리
├─ local 프로필 활성화
├─ MariaDB DataSource 설정
├─ 실제 접속 정보는 application-local.yaml에 작성
├─ application-local.yaml을 Git에서 제외
├─ Hibernate 테이블 자동 갱신 설정
└─ 실행 SQL 출력 설정
```

이번 커밋을 통해 Spring Boot가 로컬 MariaDB에 연결할 준비를 마쳤고, 비밀번호 같은 민감한 정보를 Git에 올리지 않는 구조를 만들었다.
