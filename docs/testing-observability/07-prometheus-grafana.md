# 07. Prometheus와 Grafana

## 학습 목표

- Micrometer·Actuator·Prometheus·Grafana의 역할을 구분할 수 있다.
- Spring Boot의 JVM·HTTP·HikariCP 메트릭을 Prometheus에 저장할 수 있다.
- PromQL로 서비스 상태, TPS, p95, 오류율을 계산할 수 있다.
- Grafana에 Prometheus Data Source와 대시보드 패널을 구성할 수 있다.
- k6 부하 시각의 p95·HikariCP·JVM 지표를 함께 비교할 수 있다.

## 핵심 개념

### 메트릭 수집 흐름

```text
k6 부하 → Spring Boot
                └─ Micrometer가 JVM·HTTP·DB 지표 기록
                   └─ Actuator가 /actuator/prometheus로 노출
                      └─ Prometheus가 15초마다 Pull·저장
                         └─ Grafana가 PromQL로 조회·시각화
```

- **Micrometer**: Spring Boot 안에서 메트릭을 수집하는 공통 API다.
- **Actuator**: 수집된 상태와 메트릭을 HTTP Endpoint로 노출한다.
- **Prometheus**: Endpoint를 주기적으로 읽어 Time Series DB에 저장한다.
- **Grafana**: Prometheus의 데이터를 그래프·표·알림으로 표현한다.

Grafana는 메트릭을 직접 수집하거나 저장하지 않는다. Prometheus 같은 Data Source에 저장된 값을 보여주는 역할을 한다.

### Pull 방식

Prometheus는 Spring Boot가 자신에게 지표를 보내기를 기다리지 않고, 설정된 주기마다 직접 `/actuator/prometheus`를 요청한다. 이를 **Scrape**라고 한다.

애플리케이션이 종료되거나 네트워크가 끊기면 Scrape가 실패하고 `up` 지표가 `0`이 된다. 정상적으로 수집되면 `1`이다.

### 메트릭과 라벨

Prometheus 메트릭은 이름과 여러 라벨의 조합으로 구분된다.

```text
http_server_requests_seconds_count{
  application="emotiondiary",
  method="GET",
  status="200",
  uri="/api/diaries"
}
```

라벨을 사용하면 “GET만”, “5xx만”, “일기 API만”처럼 같은 지표를 필터링·집계할 수 있다.

> 이메일·userId·diaryId처럼 값의 종류가 계속 늘어나는 정보를 라벨로 넣으면 Time Series 수가 폭증한다. 이를 High Cardinality 문제라고 하므로 피한다.

### 주요 메트릭 타입

| 타입 | 특징 | 예시 |
| --- | --- | --- |
| Counter | 누적되며 재시작 전까지 감소하지 않음 | HTTP 요청 수 |
| Gauge | 현재 상태에 따라 증가·감소 | 메모리, 활성 커넥션 |
| Histogram | 측정값을 범위별 Bucket으로 누적 | HTTP 응답 시간 p95 |

Counter의 누적값 자체보다 `rate()`로 초당 증가량을 보는 것이 일반적이다. Histogram은 `histogram_quantile()`로 p95·p99를 계산할 수 있다.

### Spring Boot 설정

#### 의존성

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

- Actuator가 `/actuator/*` Endpoint를 제공한다.
- Prometheus Registry가 Micrometer 지표를 Prometheus 형식으로 변환한다.

#### `application.yaml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: emotiondiary
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5,0.95,0.99
      sla:
        http.server.requests: 10ms,50ms,100ms,200ms,500ms,1s,2s
```

- `prometheus`: Prometheus 포맷 Endpoint를 노출한다.
- `metrics`: 사람이 JSON 형식으로 개별 지표를 확인할 수 있다.
- `application` Tag: 여러 서비스의 메트릭을 구분한다.
- `percentiles-histogram`: Prometheus가 p95를 계산할 Histogram Bucket을 노출한다.

p95 PromQL이 빈 결과를 반환한다면 `http_server_requests_seconds_bucket` 지표가 노출되는지 먼저 확인한다.

#### Security

로컬 실습에서 Prometheus가 인증 없이 Scrape할 수 있도록 다음 경로를 허용했다.

```java
.requestMatchers("/actuator/**").permitAll()
```

운영 환경에서 Actuator 전체를 외부에 공개하면 JVM·DB Pool·Traffic Pattern 같은 내부 정보가 노출된다. Management Port 분리, 내부망, Firewall, 별도 인증 중 하나 이상으로 보호해야 한다.

### Actuator Endpoint

| Endpoint | 형식 | 용도 |
| --- | --- | --- |
| `/actuator/health` | JSON | 애플리케이션 생존 상태 |
| `/actuator/metrics` | JSON | 사용 가능한 메트릭 이름 목록 |
| `/actuator/metrics/http.server.requests` | JSON | HTTP 지표 상세 확인 |
| `/actuator/prometheus` | Prometheus Text | Prometheus Scrape 전용 |

Micrometer의 `http.server.requests`는 Prometheus 형식에서 `http_server_requests_seconds`처럼 Dot가 Underscore로 변환된다.

### Prometheus 설정

`infra/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: "emotiondiary"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["host.docker.internal:8080"]
        labels:
          service: "emotiondiary"
          env: "local"

  - job_name: "prometheus"
    static_configs:
      - targets: ["localhost:9090"]
```

Prometheus는 Docker 컨테이너에, Spring Boot는 Mac 호스트에 있으므로 Target은 `host.docker.internal:8080`이다.

### PromQL 기본

#### Target 상태

```promql
up{job="emotiondiary"}
```

- `1`: Scrape 성공
- `0`: 애플리케이션 종료, 접속 실패, 401·404 등으로 Scrape 실패

#### URI별 TPS

```promql
sum by(uri) (
  rate(http_server_requests_seconds_count{application="emotiondiary"}[1m])
)
```

`rate(...[1m])`은 최근 1분의 초당 Counter 증가량을 계산한다. `sum by(uri)`는 Method·Status 등을 합쳐 URI별 하나의 값으로 만든다.

#### URI별 p95

```promql
histogram_quantile(
  0.95,
  sum by(le, uri) (
    rate(http_server_requests_seconds_bucket{application="emotiondiary"}[1m])
  )
)
```

Histogram Bucket은 초 단위이므로 Grafana Unit도 `time → seconds (s)`로 설정한다. `le` 라벨은 Bucket 상한선이며 `histogram_quantile()` 계산에 반드시 필요하다.

#### 5xx 오류율

```promql
sum(rate(http_server_requests_seconds_count{
  application="emotiondiary",status=~"5.."
}[1m]))
/
sum(rate(http_server_requests_seconds_count{
  application="emotiondiary"
}[1m]))
```

결과 `0.01`은 1%를 의미한다. 대시보드 Unit을 Percent(0.0~1.0)로 지정하면 읽기 쉽다. 4xx를 서버 장애로 포함할지는 서비스 정책에 따라 별도로 정한다.

#### HikariCP

```promql
hikaricp_connections_active{application="emotiondiary"}
hikaricp_connections_max{application="emotiondiary"}
hikaricp_connections_pending{application="emotiondiary"}
```

`active`가 `max`에 오래 붙고 `pending`이 0보다 커지면서 p95도 함께 상승한다면 DB Connection Pool 포화를 의심할 수 있다. 그래프의 동시 변화는 좋은 단서지만 원인을 확정하려면 DB Query와 Log도 확인해야 한다.

## 프로젝트 적용

### 모니터링 인프라

| 파일 | 역할 |
| --- | --- |
| `infra/docker-compose.monitoring.yml` | Prometheus·Grafana Container·Volume·Network 구성 |
| `infra/prometheus/prometheus.yml` | Scrape 주기와 Target 설정 |
| `infra/grafana/provisioning/datasources/prometheus.yml` | Grafana Data Source 자동 등록 |
| `infra/grafana/provisioning/dashboards/dashboards.yml` | Dashboard JSON 자동 로딩 경로 설정 |
| `infra/grafana/dashboards/jvm-micrometer.json` | JVM·GC·Thread 등을 보는 Dashboard |

`docker-compose.monitoring.yml`은 다음 Port를 공개한다.

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

Prometheus에는 `--web.enable-remote-write-receiver`가 설정되어 있어 k6 Metric도 받을 수 있다.

### Grafana Provisioning

Grafana Container에서 Prometheus는 `localhost:9090`이 아니라 Docker Compose Service 이름으로 호출한다.

```yaml
datasources:
  - name: Prometheus
    type: prometheus
    url: http://prometheus:9090
    isDefault: true
```

Grafana의 `localhost`는 Grafana Container 자신이다. 같은 Docker Network에서는 `prometheus:9090`을 사용한다.

Dashboard Provisioning은 Container의 `/var/lib/grafana/dashboards` 아래 JSON을 자동 등록한다. Provisioned Dashboard를 UI에서 바꿔도 파일이 다시 적용되면 덮어쓰여질 수 있으므로, 학습용 Custom Dashboard는 별도로 만든다.

### 핵심 Dashboard

#### JVM Dashboard

`jvm-micrometer.json`으로 다음을 관찰한다.

- Heap 사용량
- Live·Daemon Thread
- GC 횟수와 Pause
- Process CPU

#### `emotiondiary-observability`

새 Dashboard를 만들어 엔드포인트 성능과 DB Pool을 함께 본다.

| Panel | Query | Unit·Legend |
| --- | --- | --- |
| HTTP TPS by URI | `sum by(uri) (rate(http_server_requests_seconds_count{application="emotiondiary"}[1m]))` | `{{uri}}` |
| HTTP p95 by URI | `histogram_quantile(0.95, sum by(le, uri) (rate(http_server_requests_seconds_bucket{application="emotiondiary"}[1m])))` | seconds, `{{uri}}` |
| HikariCP Active / Max | `hikaricp_connections_active{application="emotiondiary"}` / `hikaricp_connections_max{application="emotiondiary"}` | `{{__name__}}` |
| HTTP 5xx Error Rate | 5xx Rate / 전체 Rate | Percent(0.0~1.0) |

Grafana Panel 편집 화면에서 Data Source를 `Prometheus`로 선택하고 Query Editor를 **Code** Mode로 바꾼 뒤 PromQL을 입력한다. 적용 후 Dashboard를 `emotiondiary-observability`로 저장한다.

## 실습 내용

### 1. Spring Boot Metric 확인

애플리케이션을 재시작한 뒤 로컬에서 Endpoint를 확인한다.

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/http.server.requests
curl http://localhost:8080/actuator/prometheus
```

macOS·Linux에서 HTTP Metric만 앞 몇 줄 확인하려면:

```bash
curl -s http://localhost:8080/actuator/prometheus \
  | grep 'http_server_requests' \
  | head -n 5
```

### 2. Monitoring Stack 기동

`emotiondiary-net`은 DB Compose가 만들므로 DB를 먼저 기동한다.

```bash
docker compose -f infra/docker-compose.db.yml up -d
docker compose -f infra/docker-compose.monitoring.yml up -d
docker compose -f infra/docker-compose.monitoring.yml ps
```

### 3. Prometheus Target 확인

1. Browser에서 `http://localhost:9090` 접속
2. **Status → Targets** 이동
3. `emotiondiary`, `prometheus` Job이 모두 `UP`인지 확인
4. Query 화면에 `up{job="emotiondiary"}` 입력

Spring Boot를 종료하면 최대 15초 후 `up=0`, 다시 시작하면 `up=1`로 변하는 것을 볼 수 있다.

### 4. Grafana Dashboard 구성

1. `http://localhost:3000` 접속
2. 학습용 계정 `admin` / `admin`으로 Login
3. **Dashboards → New → New dashboard**
4. **Add visualization** 후 Prometheus 선택
5. Query Editor의 **Code** 선택
6. TPS·p95·HikariCP·Error Rate Panel 추가
7. Dashboard 저장

Grafana의 `admin/admin`은 로컬 학습용이다. 공유·운영 환경에서는 반드시 비밀번호를 변경하고 접근을 제한한다.

### 5. k6 부하와 Metric 관찰

일반 CRUD Scenario:

```bash
docker compose -f infra/docker-compose.k6.yml exec k6 \
  k6 run /scripts/diary-scenario.js
```

보다 강한 Stress Scenario:

```bash
docker compose -f infra/docker-compose.k6.yml exec k6 \
  k6 run /scripts/diary-stress.js
```

k6 Metric도 Prometheus에 함께 저장하려면:

```bash
docker compose -f infra/docker-compose.k6.yml exec k6 \
  k6 run \
  -o experimental-prometheus-rw=http://prometheus:9090/api/v1/write \
  /scripts/diary-scenario.js
```

Grafana 시간 범위를 **Last 5 minutes**, Refresh를 **5s**로 맞추고 다음을 함께 본다.

- VU가 늘 때 TPS도 늘어나는가?
- 어느 시점부터 TPS는 멈추고 p95만 증가하는가?
- HikariCP `active`가 `max` 근처에 머무는가?
- `pending`이 0보다 커지는가?
- p95가 튀는 시점에 GC Pause·Heap·Thread도 함께 변하는가?

그래프의 동시 변화는 원인 후보를 줄이는 단서다. “같이 올랐다”만으로 원인을 확정하지 말고 로그·Query·Profile을 추가로 확인한다.

## 실행 및 검증

### 실행 순서

```bash
# 1. Docker Network·DB
docker compose -f infra/docker-compose.db.yml up -d

# 2. k6
docker compose -f infra/docker-compose.k6.yml up -d

# 3. Prometheus·Grafana
docker compose -f infra/docker-compose.monitoring.yml up -d

# 4. Spring Boot는 IDE 또는 별도 Terminal에서 실행
./gradlew bootRun
```

### 상태 확인

```bash
docker compose -f infra/docker-compose.monitoring.yml ps
curl http://localhost:8080/actuator/health
curl http://localhost:9090/-/healthy
```

확인 목록:

- MariaDB가 `healthy`다.
- k6·Prometheus·Grafana가 `Up`이다.
- `/actuator/prometheus`가 200을 반환한다.
- Prometheus Targets의 `emotiondiary` Job이 `UP`이다.
- Grafana Data Source가 Prometheus로 자동 등록됐다.
- 부하 중 TPS·p95·HikariCP Graph가 갱신된다.

### 주요 Metric Reference

| 영역 | Metric | 의미 |
| --- | --- | --- |
| JVM | `jvm_memory_used_bytes{area="heap"}` | Heap 사용량 |
| JVM | `jvm_gc_pause_seconds_*` | GC 중단 시간 |
| JVM | `jvm_threads_live_threads` | 현재 Live Thread 수 |
| Process | `process_cpu_usage` | JVM Process CPU 사용률 |
| HTTP | `http_server_requests_seconds_count` | HTTP 요청 누적 수 |
| HTTP | `http_server_requests_seconds_bucket` | p95·p99 계산용 Histogram |
| HikariCP | `hikaricp_connections_active` | 사용 중인 Connection |
| HikariCP | `hikaricp_connections_idle` | 대기 중인 유휴 Connection |
| HikariCP | `hikaricp_connections_pending` | Connection을 기다리는 Thread |
| HikariCP | `hikaricp_connections_acquire_seconds` | Connection 획득 시간 |

Metric 이름은 Spring Boot·Micrometer·Prometheus Registry 버전에 따라 세부적으로 달라질 수 있다. `/actuator/prometheus`에서 실제 노출된 이름을 기준으로 Query를 작성한다.

### 종료

지표를 계속 보관하고 싶으면 Container만 종료하고 Volume은 남겨둔다.

```bash
docker compose -f infra/docker-compose.monitoring.yml down
docker compose -f infra/docker-compose.k6.yml down
docker compose -f infra/docker-compose.db.yml down
```

`down -v`를 사용하면 Prometheus Time Series·Grafana 설정·MariaDB 데이터가 삭제될 수 있으므로 일반 종료에서는 사용하지 않는다.

## 문제와 해결

### `/actuator/prometheus`가 404다

- Actuator와 Prometheus Registry 의존성을 확인한다.
- `management.endpoints.web.exposure.include`에 `prometheus`가 있는지 확인한다.
- 설정 변경 후 Spring Boot를 재시작한다.

### `/actuator/prometheus`가 401 또는 403이다

로컬 실습 설정에서 `/actuator/**`가 `permitAll()` 대상인지 확인한다. 운영에서는 무조건 공개하지 말고 Prometheus에 필요한 내부 접근 경로를 구성한다.

### macOS에서 `Select-String`, `Select-Object` 명령이 없다

이 명령은 PowerShell 전용이다. zsh에서는 `grep`과 `head`를 사용한다.

```bash
curl -s http://localhost:8080/actuator/prometheus \
  | grep 'http_server_requests' \
  | head -n 5
```

Pipe 뒤의 명령이 없어 먼저 종료되면 `curl: (23) Failure writing output` 같은 메시지가 함께 보일 수 있다.

### Prometheus Target이 DOWN이다

1. Spring Boot가 8080에서 실행 중인지 확인한다.
2. 호스트에서 `/actuator/prometheus`가 200인지 확인한다.
3. `prometheus.yml`의 Target이 `host.docker.internal:8080`인지 확인한다.
4. Prometheus Targets 화면의 **Last Scrape Error**를 확인한다.

### PromQL 결과가 비어 있다

- 먼저 `up{job="emotiondiary"}`로 Scrape 상태를 확인한다.
- 해당 API를 한 번 이상 호출해 Metric을 생성한다.
- Grafana 시간 범위를 Last 5·15 minutes로 맞춘다.
- `application="emotiondiary"` Label이 실제 Metric에 있는지 확인한다.
- Query에 사용한 Metric 이름을 `/actuator/prometheus`에서 검색한다.

`rate(...[1m])`은 최근 1분에 최소 두 개 정도의 Sample이 필요하다. Scrape 직후에는 잠시 빈 결과가 나올 수 있으므로 15~30초 후 다시 확인한다.

### p95 Query만 빈 결과다

`http_server_requests_seconds_bucket` 자체가 있는지 확인한다. 없다면 Histogram 설정을 추가한 후 Spring Boot를 재시작한다.

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

### Grafana Data Source 연결이 실패한다

Grafana Container 내부에서 `localhost:9090`은 Grafana 자신이다. URL을 `http://prometheus:9090`으로 설정하고 두 Container가 `emotiondiary-net`에 속했는지 확인한다.

### Grafana Panel에 `No data`가 보인다

- Data Source가 Prometheus인지 확인한다.
- Query Editor를 Code Mode로 바꾸고 Prometheus에서 먼저 성공한 Query를 붙여넣는다.
- 시간 범위·Refresh·Label Filter를 확인한다.
- k6로 Traffic을 발생시킨다.

### p95가 초 단위로 표시되지 않는다

Panel 오른쪽의 **Standard options → Unit → Time → seconds (s)**를 선택한다. Prometheus HTTP Duration Metric의 기본 단위는 초다.

### HikariCP Metric이 보이지 않는다

Spring Boot가 DataSource를 생성하고 DB에 연결했는지 확인한다. 애플리케이션 기동 직후에는 없을 수 있으므로 DB를 사용하는 API를 호출한 뒤 `/actuator/prometheus`에서 `hikaricp_` 접두사를 검색한다.

### k6 Remote Write가 실패한다

Prometheus Command에 `--web.enable-remote-write-receiver`가 있는지 확인한다. k6와 Prometheus가 같은 Docker Network에 있다면 URL은 `http://prometheus:9090/api/v1/write`를 사용한다.

### Monitoring Stack을 시작할 때 External Network 오류가 난다

DB Compose가 `emotiondiary-net`을 먼저 만들어야 한다.

```bash
docker compose -f infra/docker-compose.db.yml up -d
docker compose -f infra/docker-compose.monitoring.yml up -d
```

## 정리

- Micrometer는 애플리케이션 메트릭을 기록하고 Actuator는 이를 HTTP로 노출한다.
- Prometheus는 `/actuator/prometheus`를 주기적으로 Pull해 Time Series로 저장한다.
- Grafana는 Prometheus를 Data Source로 사용해 Dashboard를 그린다.
- `up=1`은 Scrape 성공, `up=0`은 Target에서 Metric을 읽지 못한 상태다.
- Counter의 초당 증가량은 `rate()`, Histogram의 p95는 `histogram_quantile()`로 계산한다.
- Dashboard에 TPS·p95·오류율·HikariCP Active·Pending을 함께 놓는다.
- HikariCP Active가 Max에 붙고 Pending·p95가 함께 오르면 DB Pool 포화를 의심한다.
- 그래프의 상관관계는 원인 후보이며 확정은 아니다. Log·Query·Profile로 추가 검증한다.
- 운영에서 Actuator·Prometheus·Grafana는 내부망·Firewall·Authentication으로 보호한다.
- 8단원에서는 p95가 튠 시각의 구조화 Log와 traceId를 Kibana에서 추적한다.
