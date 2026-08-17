# 08. ELK와 traceId

## 학습 목표

- 메트릭과 로그의 차이를 설명할 수 있다.
- Elasticsearch·Logstash·Kibana의 역할을 구분할 수 있다.
- Spring Boot 로그를 JSON으로 만들어 ELK에 저장할 수 있다.
- 모든 HTTP 요청에 `traceId`를 부여할 수 있다.
- Kibana에서 오류 로그와 특정 요청의 흐름을 검색할 수 있다.
- Grafana에서 이상 시각을 찾고 Kibana에서 원인을 조사할 수 있다.

## 핵심 개념

### 메트릭과 로그의 차이

7단원에서 사용한 메트릭과 이번 단원의 로그는 서로 다른 질문에 답한다.

| 데이터 | 잘 답하는 질문 | 예시 |
| --- | --- | --- |
| 메트릭 | 언제, 얼마나 많이 발생했는가? | 14시 30분에 p95가 2초까지 상승했다. |
| 로그 | 무엇이, 왜 발생했는가? | 같은 시각에 일기 조회 예외가 발생했다. |

Grafana 그래프만 보면 문제가 발생한 시점은 알 수 있지만 정확한 이유를 찾기 어렵다. Kibana 로그만 보면 개별 사건은 자세히 보이지만 서비스 전체의 변화는 한눈에 보기 어렵다.

따라서 장애를 조사할 때는 다음 흐름을 사용한다.

```text
Grafana에서 이상 시각 확인
        ↓
Kibana의 시간 범위를 같은 시각으로 설정
        ↓
ERROR·WARN 로그 검색
        ↓
traceId로 한 요청의 로그만 다시 검색
```

### ELK란 무엇인가

ELK는 Elasticsearch, Logstash, Kibana를 묶어 부르는 이름이다.

- **Elasticsearch**: JSON 로그를 저장하고 빠르게 검색한다.
- **Logstash**: 여러 곳에서 로그를 받아 가공한 뒤 Elasticsearch로 보낸다.
- **Kibana**: Elasticsearch에 저장된 로그를 검색하고 화면에 보여준다.

emotiondiary의 전체 로그 흐름은 다음과 같다.

```text
HTTP 요청
  ↓
TraceIdFilter가 traceId 생성
  ↓
Controller·Service 등이 로그 출력
  ↓
Logback이 콘솔 출력 + JSON 변환
  ↓ TCP 5044
Logstash가 수신·가공
  ↓
Elasticsearch가 일자별 Index에 저장
  ↓
Kibana Discover에서 검색
```

### 구조화 로그

일반 문자열 로그는 사람이 읽기는 쉽지만 필드별 검색이 어렵다.

```text
16:10:23 INFO login called: email=test@example.com
```

구조화 로그는 같은 내용을 JSON의 독립된 필드로 표현한다.

```json
{
  "@timestamp": "2026-08-17T07:10:23.123Z",
  "level": "INFO",
  "logger_name": "com.example.emotiondiary.controller.AuthController",
  "message": "login called: email=test@example.com",
  "thread_name": "http-nio-8080-exec-1",
  "traceId": "a3f42e91",
  "application": "emotiondiary",
  "env": "local"
}
```

이제 Kibana에서 `level:ERROR`, `traceId:a3f42e91`처럼 원하는 필드만 검색할 수 있다.

### traceId와 MDC

여러 사용자의 요청이 동시에 처리되면 콘솔 로그가 서로 섞인다. 이때 각 HTTP 요청에 고유한 `traceId`를 붙이면 같은 요청에서 나온 로그를 하나로 묶을 수 있다.

```text
[a3f42e91] 로그인 요청 시작
[b82c10fa] 일기 조회 시작
[a3f42e91] 비밀번호 확인
[a3f42e91] 로그인 성공
```

`a3f42e91`만 검색하면 로그인 요청의 흐름만 남는다.

MDC(Mapped Diagnostic Context)는 현재 요청을 처리하는 Thread에 `traceId` 같은 값을 임시로 보관하는 공간이다. Logback은 MDC의 값을 읽어 모든 로그에 자동으로 넣는다.

> Spring의 요청 처리 Thread는 재사용된다. 요청이 끝난 뒤 MDC 값을 지우지 않으면 다음 요청에 이전 traceId가 붙을 수 있으므로 `finally`에서 반드시 제거해야 한다.

### 로그 레벨

| 레벨 | 의미 | 사용 예시 |
| --- | --- | --- |
| `DEBUG` | 개발 중 상세 정보 | 내부 계산값, 실행 흐름 확인 |
| `INFO` | 정상적인 주요 사건 | 로그인 요청, 애플리케이션 기동 |
| `WARN` | 처리했지만 확인이 필요한 문제 | 잘못된 요청, 비즈니스 예외 |
| `ERROR` | 정상 처리하지 못한 오류 | 예상하지 못한 예외, 외부 시스템 장애 |

모든 값을 `ERROR`로 기록하면 정말 중요한 오류를 구분하기 어렵다. 상황의 심각도에 맞게 레벨을 선택한다.

비밀번호, JWT, Refresh Token, 주민번호 같은 민감 정보는 어떤 레벨에서도 로그에 남기지 않는다.

## 프로젝트 적용

### 의존성

`build.gradle`에는 Logback 로그를 JSON으로 바꾸고 Logstash에 전송하는 Encoder가 추가되어 있다.

```groovy
implementation 'net.logstash.logback:logstash-logback-encoder:8.0'
```

Spring Boot 4.x가 사용하는 Logback 버전과 맞추기 위해 Encoder 8.0을 사용한다.

### Logback 설정

`src/main/resources/logback-spring.xml`에는 두 개의 Appender가 있다.

- `CONSOLE`: 개발자가 읽기 쉬운 형식으로 콘솔에 출력한다.
- `LOGSTASH`: 같은 로그를 JSON으로 바꾸어 `localhost:5044`로 전송한다.

콘솔 Pattern의 핵심은 다음 부분이다.

```xml
[%X{traceId:-NONE}]
```

- MDC에 `traceId`가 있으면 그 값을 출력한다.
- HTTP 요청 밖에서 발생한 로그처럼 값이 없으면 `NONE`을 출력한다.

JSON 로그에는 다음 공통 필드도 추가된다.

```xml
<includeMdcKeyName>traceId</includeMdcKeyName>
<customFields>{"application":"emotiondiary","env":"local"}</customFields>
```

따라서 Kibana에서 애플리케이션·환경·traceId별로 필터링할 수 있다.

### TraceIdFilter

`TraceIdFilter`는 모든 HTTP 요청에서 한 번 실행된다.

```java
String incoming = request.getHeader("X-Trace-Id");
String traceId = (incoming != null && !incoming.isBlank())
        ? incoming
        : UUID.randomUUID().toString().substring(0, 8);

try {
    MDC.put("traceId", traceId);
    response.setHeader("X-Trace-Id", traceId);
    chain.doFilter(request, response);
} finally {
    MDC.remove("traceId");
}
```

처리 순서는 다음과 같다.

1. 요청 Header에 `X-Trace-Id`가 있으면 그 값을 사용한다.
2. 없으면 새로운 8자리 값을 만든다.
3. MDC에 저장해 이후 로그에 자동으로 포함시킨다.
4. 응답 Header에도 같은 값을 넣는다.
5. 요청 처리가 끝나면 MDC에서 제거한다.

클라이언트가 응답의 `X-Trace-Id`를 알려주면 운영자는 Kibana에서 해당 요청만 바로 찾을 수 있다. 다른 서비스가 이미 traceId를 보냈다면 그 값을 유지하므로 서비스 사이의 요청도 연결할 수 있다.

### 애플리케이션 로그

`AuthController`에는 Lombok의 `@Slf4j`가 적용되어 있다.

```java
@Slf4j
@RestController
public class AuthController {
    // ...

    public ResponseEntity<TokenResponse> login(LoginRequest request) {
        log.info("login called: email={}", request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }
}
```

`@Slf4j`가 `log` 객체를 자동으로 만든다. 이 Annotation 없이 `log.info(...)`를 사용하면 `cannot find symbol: variable log` Compile Error가 발생한다.

### Logstash 처리

`infra/logstash/pipeline/logstash.conf`의 동작은 단순하다.

1. TCP `5044`에서 JSON Line을 받는다.
2. 로그 Level을 대문자로 통일한다.
3. `ERROR`와 `WARN`에 검색용 Tag를 추가한다.
4. Elasticsearch의 일자별 Index에 저장한다.

```text
emotiondiary-2026.08.17
emotiondiary-2026.08.18
```

날짜별로 나누면 오래된 로그를 삭제하거나 특정 기간만 조회하기 쉽다.

### Container와 Port

| Container | Port | 역할 |
| --- | --- | --- |
| Elasticsearch | `9200` | 로그 저장·검색 API |
| Logstash | `5044` | Spring Boot JSON 로그 수신 |
| Logstash | `9600` | Logstash 상태 API |
| Kibana | `5601` | 로그 검색 화면 |

`docker-compose.logging.yml`은 기존 `emotiondiary-net`을 사용하는 설정이다. 이 Network는 DB Compose가 먼저 만든다.

Elasticsearch의 `xpack.security.enabled=false`는 로컬 학습 편의를 위한 설정이다. 운영 환경에서는 인증·TLS·접근 제어 없이 사용하면 안 된다.

## 실습 내용

### 1. ELK Stack 실행

DB가 실행 중이 아니라면 먼저 기동한다.

```bash
docker compose -f infra/docker-compose.db.yml up -d
docker compose -f infra/docker-compose.logging.yml up -d
docker compose -f infra/docker-compose.logging.yml ps
```

다음 세 Container가 `Up`인지 확인한다.

- `emotiondiary-elasticsearch`
- `emotiondiary-logstash`
- `emotiondiary-kibana`

Elasticsearch는 Health Check가 끝날 때까지 시간이 걸릴 수 있고, Kibana와 Logstash는 그 뒤에 시작한다.

### 2. Spring Boot 재시작

의존성과 `logback-spring.xml`을 적용하려면 애플리케이션을 재시작한다.

```bash
./gradlew bootRun
```

콘솔 로그에서 traceId 자리를 확인한다.

```text
16:10:23.123 [http-nio-8080-exec-1] INFO [a3f42e91] ... - login called: email=test@example.com
```

애플리케이션 시작 로그처럼 HTTP 요청과 관계없는 로그에는 `[NONE]`이 표시되는 것이 정상이다.

### 3. Postman에서 로그인 요청

Postman에서 다음 요청을 만든다.

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json
```

Body → **raw → JSON**:

```json
{
  "email": "test@example.com",
  "password": "사용 중인 비밀번호"
}
```

직접 traceId를 지정해 보고 싶으면 **Headers** 탭에 다음 값을 추가한다.

| Key | Value |
| --- | --- |
| `X-Trace-Id` | `abcdefg` |

요청 후 다음 세 곳에 같은 값이 있는지 확인한다.

- Spring Boot 콘솔 로그의 `[abcdefg]`
- Postman Response Headers의 `X-Trace-Id: abcdefg`
- Elasticsearch JSON 문서의 `traceId`

Header를 보내지 않으면 서버가 8자리 traceId를 자동 생성한다.

### 4. Elasticsearch에서 저장 결과 확인

Cluster 상태:

```bash
curl -s http://localhost:9200/_cluster/health?pretty
```

Index 목록:

```bash
curl -s 'http://localhost:9200/_cat/indices?v&pretty'
```

`emotiondiary-2026.08.17` 같은 Index가 나타나고 `docs.count`가 1 이상이면 로그가 저장된 것이다.

원시 로그 한 건 확인:

```bash
curl -s 'http://localhost:9200/emotiondiary-*/_search?pretty&size=1'
```

응답의 `hits.hits[]._source`에서 `level`, `message`, `traceId`, `application`, `env`를 확인한다.

### 5. Kibana Data View 생성

1. Browser에서 `http://localhost:5601` 접속
2. 왼쪽 메뉴의 **Stack Management** 이동
3. **Kibana → Data Views → Create data view** 선택
4. 다음 값 입력

| 항목 | 값 |
| --- | --- |
| Name | `emotiondiary` |
| Index pattern | `emotiondiary-*` |
| Timestamp field | `@timestamp` |

Data View는 Elasticsearch의 실제 Index를 삭제하거나 복사하지 않는다. Kibana가 어떤 Index를 어떤 시간 필드로 조회할지 기억하는 설정이다.

### 6. Discover에서 로그 확인

Kibana의 **Discover**로 이동해 방금 만든 `emotiondiary` Data View를 선택한다.

1. 시간 범위를 **Last 15 minutes**로 설정한다.
2. Auto-refresh를 `5s`로 설정한다.
3. 왼쪽 Fields에서 `level`, `traceId`, `message`를 표에 추가한다.
4. Postman 요청을 여러 번 보낸다.
5. 새로운 로그가 나타나는지 확인한다.

Kibana 버전에 따라 **Analytics** 그룹이 보이지 않을 수 있다. 왼쪽 메뉴 전체를 펼치거나 상단 검색에서 `Discover`를 검색하면 된다.

### 7. KQL로 필요한 로그 검색

KQL(Kibana Query Language)은 Discover 위쪽 검색창에 입력한다.

| KQL | 의미 |
| --- | --- |
| `level:ERROR` | Error 로그만 조회 |
| `level:(ERROR OR WARN)` | Error 또는 Warn 조회 |
| `traceId:abcdefg` | 한 요청의 로그만 조회 |
| `message:*failed*` | Message에 `failed`가 포함된 로그 |
| `logger_name:*AuthController*` | AuthController가 남긴 로그 |
| `application:emotiondiary AND level:ERROR` | emotiondiary의 Error만 조회 |
| `NOT level:INFO` | Info를 제외한 로그 조회 |

KQL의 공백은 일반적으로 `AND`와 같은 의미다.

```text
level:ERROR message:*timeout*
```

이는 Error이면서 Message에 `timeout`이 있는 로그를 찾는다.

### 8. 오류와 traceId 연결

Postman에서 존재하지 않는 일기 ID를 조회하거나 잘못된 요청 Body를 보내 WARN·ERROR 로그를 발생시킨다. 인증이 필요한 API라면 로그인 후 받은 Access Token을 Authorization 탭의 Bearer Token으로 넣는다.

1. Discover에서 `level:(ERROR OR WARN)` 검색
2. 방금 발생한 로그 한 건 펼치기
3. 해당 로그의 `traceId` 복사
4. 검색창을 `traceId:복사한값`으로 변경
5. 그 요청에서 나온 로그를 시간 순서대로 확인

### 9. Grafana와 Kibana 함께 보기

1. k6로 부하를 발생시킨다.
2. Grafana에서 p95 또는 오류율이 튄 시각을 확인한다.
3. Kibana의 시간 범위를 같은 시각으로 맞춘다.
4. `level:(ERROR OR WARN)`으로 원인 후보를 찾는다.
5. `traceId`로 한 요청의 로그를 좁혀 본다.

```bash
docker compose -f infra/docker-compose.k6.yml exec k6 \
  k6 run /scripts/diary-scenario.js
```

Grafana는 **언제 문제가 생겼는지**, Kibana는 **그때 무슨 일이 있었는지** 찾는 데 사용한다.

## 실행 및 검증

### 권장 실행 순서

```bash
# 1. Network와 DB 준비
docker compose -f infra/docker-compose.db.yml up -d

# 2. Prometheus와 Grafana
docker compose -f infra/docker-compose.monitoring.yml up -d

# 3. Elasticsearch, Logstash, Kibana
docker compose -f infra/docker-compose.logging.yml up -d

# 4. k6 Container
docker compose -f infra/docker-compose.k6.yml up -d

# 5. Spring Boot는 IDE 또는 별도 Terminal에서 실행
./gradlew bootRun
```

### 상태 확인 명령

```bash
docker compose -f infra/docker-compose.logging.yml ps
curl -s http://localhost:9200/_cluster/health?pretty
curl -s http://localhost:9600/_node/stats?pretty
curl -s 'http://localhost:9200/_cat/indices?v&pretty'
```

### 최종 확인 목록

- Elasticsearch가 `healthy`다.
- Logstash와 Kibana가 `Up`이다.
- Spring Boot 요청 로그에 `[traceId]`가 표시된다.
- 응답 Header에 `X-Trace-Id`가 있다.
- Elasticsearch에 `emotiondiary-*` Index가 생성된다.
- `_source` 안에 `level`, `message`, `traceId`가 있다.
- Kibana Discover에서 최신 로그가 보인다.
- `traceId:값`으로 한 요청의 로그만 조회된다.
- Grafana의 이상 시각과 Kibana의 오류 발생 시각을 맞춰 볼 수 있다.

### 종료

```bash
docker compose -f infra/docker-compose.logging.yml down
docker compose -f infra/docker-compose.monitoring.yml down
docker compose -f infra/docker-compose.k6.yml down
docker compose -f infra/docker-compose.db.yml down
```

Elasticsearch 로그와 다른 데이터가 필요하면 `down -v`를 사용하지 않는다. `down -v`는 Compose Volume을 삭제해 데이터를 잃을 수 있다.

## 문제와 해결

### `log` 변수를 찾을 수 없다고 나온다

로그를 사용하는 Class에 Lombok Annotation과 Import가 필요하다.

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthController {
}
```

### 콘솔에 항상 `[NONE]`이 나온다

- HTTP 요청을 실제로 보냈는지 확인한다.
- `TraceIdFilter`가 `@Component`로 등록됐는지 확인한다.
- `MDC.put()`이 `chain.doFilter()`보다 먼저 실행되는지 확인한다.
- Logback Pattern이 `%X{traceId:-NONE}`인지 확인한다.

애플리케이션 기동 로그와 Scheduler 로그처럼 HTTP 요청 밖에서 발생한 로그가 `[NONE]`인 것은 정상이다.

### Spring Boot는 실행되지만 Elasticsearch Index가 없다

아래 순서로 전송 경로를 확인한다.

1. `docker compose -f infra/docker-compose.logging.yml ps`에서 Logstash 상태 확인
2. `lsof -nP -iTCP:5044 -sTCP:LISTEN`으로 Port 확인
3. `docker logs emotiondiary-logstash`에서 Parsing·연결 오류 확인
4. `logback-spring.xml`의 Destination이 `localhost:5044`인지 확인
5. Spring Boot 재시작 후 API 요청으로 새 로그 생성

Spring Boot가 Mac 호스트에서 실행되므로 Logstash의 공개 Port인 `localhost:5044`를 사용한다. Spring Boot도 Docker 안에서 실행한다면 같은 Network의 Service 이름인 `logstash:5044`를 사용해야 한다.

### Elasticsearch의 `docs.count`가 0이다

Spring Boot에서 Logstash까지 로그가 전달되지 않은 상태일 수 있다. Logstash 로그와 Spring Boot 시작 로그에서 TCP 연결 오류를 확인하고 API 요청을 한 번 더 보낸다.

### Postman에서 Elasticsearch 요청만 400이다

- Method를 `GET`으로 설정한다.
- URL에는 Markdown의 `[` `]` `(` `)`를 넣지 않는다.
- 다음 주소만 그대로 입력한다.

```text
http://localhost:9200/_cat/indices?v&pretty
```

불필요한 Body나 잘못된 Header가 있다면 비우고 다시 요청한다.

### Kibana에서 Discover가 보이지 않는다

Kibana 버전에 따라 메뉴 위치가 다를 수 있다. 왼쪽 햄버거 메뉴를 펼친 뒤 `Discover`를 찾거나 상단 검색창에서 검색한다. 먼저 Data View가 생성되어 있는지도 확인한다.

### Kibana에 로그가 보이지 않는다

- Data View가 `emotiondiary-*`인지 확인한다.
- Timestamp Field가 `@timestamp`인지 확인한다.
- 시간 범위를 Last 15 minutes 또는 Last 24 hours로 넓힌다.
- Elasticsearch Index의 `docs.count`가 1 이상인지 확인한다.
- Discover에서 올바른 Data View를 선택했는지 확인한다.

### `X-Trace-Id`는 어디에 입력하는가

Postman 요청의 **Headers** 탭에 Key `X-Trace-Id`, Value `abcdefg`를 입력한다. Body에 넣는 값이 아니다.

### Container가 메모리 부족으로 종료된다

ELK는 메모리 사용량이 크다. 필요 없는 Container를 종료하고 Docker Desktop의 Memory 설정을 확인한다. 8GB 환경이라면 k6·Grafana 등을 동시에 실행하지 않고 단계별로 확인하는 편이 안전하다.

### External Network 오류가 발생한다

`docker-compose.logging.yml`은 `emotiondiary-net`이 이미 존재한다고 가정한다. DB Compose를 먼저 실행한다.

```bash
docker compose -f infra/docker-compose.db.yml up -d
docker compose -f infra/docker-compose.logging.yml up -d
```

## 정리

- 메트릭은 **언제·얼마나**, 로그는 **무엇이·왜**를 설명한다.
- Logback은 애플리케이션 로그를 콘솔에 출력하고 JSON으로 Logstash에 보낸다.
- Logstash는 로그를 가공하고 Elasticsearch는 이를 검색 가능한 문서로 저장한다.
- Kibana는 Elasticsearch 로그를 Data View와 KQL로 탐색한다.
- traceId는 한 HTTP 요청에서 발생한 여러 로그를 하나로 연결한다.
- MDC에 넣은 traceId는 요청이 끝날 때 반드시 제거한다.
- 클라이언트가 `X-Trace-Id`를 보내면 그대로 사용하고, 없으면 서버가 생성한다.
- Grafana에서 이상 시각을 찾은 뒤 Kibana에서 같은 시간의 오류와 traceId를 조사한다.
- 비밀번호·Token 같은 민감 정보는 로그로 남기지 않는다.
- 로컬에서 비활성화한 Elasticsearch 보안 설정을 운영 환경에 그대로 사용하면 안 된다.
