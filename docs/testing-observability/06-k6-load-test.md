# 06. k6 부하 테스트

## 학습 목표

- Docker Compose로 MariaDB와 k6 실습 환경을 기동할 수 있다.
- 호스트와 컨테이너 사이의 주소·포트 차이를 이해한다.
- k6의 `options`, `setup()`, `default()`, `teardown()` 구조를 설명할 수 있다.
- `check()`와 Threshold의 역할을 구분할 수 있다.
- Ramp-up을 적용한 로그인·일기 CRUD 사용자 시나리오를 실행할 수 있다.
- CLI와 `summary.json`에서 p95·오류율·TPS·Check 결과를 해석할 수 있다.

## 핵심 개념

### 실습 구조

이 단원은 애플리케이션·DB·부하 발생기를 다음과 같이 배치한다.

```text
k6 컨테이너
  └─ http://host.docker.internal:8080
       ↓
호스트의 Spring Boot
  └─ jdbc:mariadb://localhost:3308/springstudy
       ↓
MariaDB 컨테이너(3306)
```

- **Spring Boot**: IntelliJ 디버겅과 빠른 코드 수정을 위해 Mac 호스트에서 실행한다.
- **MariaDB**: 동일한 DB 환경을 재현하기 위해 Docker 컨테이너로 실행한다.
- **k6**: 설치와 버전을 통일하기 위해 Docker 컨테이너로 실행한다.

컨테이너 안의 `localhost`는 Mac이 아니라 **그 컨테이너 자신**을 가리킨다. k6에서 Mac의 Spring Boot에 접속할 때는 `host.docker.internal`을 사용한다.

### 포트 매핑

현재 `docker-compose.db.yml`은 다음 매핑을 사용한다.

```yaml
ports:
  - "3308:3306"
```

- 왼쪽 `3308`: Mac 호스트에서 접속하는 포트
- 오른쪽 `3306`: MariaDB 컨테이너 안의 포트

따라서 Spring Boot의 DB URL은 `localhost:3308`을 사용해야 한다. 컨테이너 간 통신을 할 때는 호스트 포트 대신 서비스 이름과 컨테이너 포트를 사용한다.

### Docker Compose 구성

`infra/docker-compose.db.yml`은 MariaDB와 `emotiondiary-net` Docker 네트워크를 만든다.

주요 설정:

- `mariadb:11.4`: DB 이미지
- `3308:3306`: 호스트 포트 공개
- `emotiondiary-db-data`: 컨테이너를 재생성해도 데이터를 유지하는 Volume
- `./mariadb/init`: DB를 처음 만들 때 실행할 SQL
- `healthcheck`: DB가 실제 접속 가능한 상태인지 확인

`infra/docker-compose.k6.yml`은 k6 컨테이너를 계속 실행한 상태로 유지한다.

```yaml
volumes:
  - ./k6/scripts:/scripts:ro
  - ./k6/results:/results
entrypoint: ["sleep", "infinity"]
```

- `/scripts`: 호스트의 k6 스크립트를 읽기 전용으로 마운트한다.
- `/results`: 컨테이너의 결과 파일을 호스트에 저장한다.
- `sleep infinity`: 컨테이너를 살려두고 `docker compose exec` 명령으로 스크립트를 반복 실행한다.
- `external: true`: DB Compose가 먼저 만든 `emotiondiary-net`을 재사용한다.

### k6 스크립트 구조

k6는 Node.js가 아니라 Go 기반 k6 엔진의 JavaScript 런타임을 사용한다. Node의 `fs`, `net` 대신 `k6/http`, `k6`, `k6/metrics` 모듈을 사용한다.

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export function setup() {
  return { token: 'shared-token' };
}

export default function (data) {
  const res = http.get('http://host.docker.internal:8080/');
  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(1);
}

export function teardown(data) {
  // 정리 작업
}
```

| 구성 | 실행 시점 | 용도 |
| --- | --- | --- |
| `options` | 실행 전 설정 | VU·시간·Scenario·Threshold |
| `setup()` | 전체 테스트 시작 전 1회 | 로그인·공용 토큰·시드 데이터 |
| `default()` | VU별로 반복 | 실제 사용자 행동 |
| `teardown()` | 전체 테스트 종료 후 1회 | 시험 데이터 정리 |

`setup()`이 반환한 값은 `default(data)`에 전달되어 모든 VU가 사용할 수 있다.

### `check()`와 Threshold

| 구분 | `check()` | Threshold |
| --- | --- | --- |
| 판단 대상 | 각 응답 | 전체 실행의 집계 지표 |
| 예시 | 상태가 200인가? | p95가 500ms 미만인가? |
| 실패 효과 | 실행은 계속되고 Check 실패로 집계 | 최종 프로세스를 실패로 종료 |
| 주요 목적 | 기능 상태 확인 | SLO·SLA 판정과 CI Gate |

```javascript
check(res, {
  'list 200': (r) => r.status === 200,
  'has items': (r) => Array.isArray(r.json('items')),
});

thresholds: {
  http_req_failed: ['rate<0.01'],
  'http_req_duration{name:diary-list}': ['p(95)<500'],
}
```

Check 실패만으로는 k6 프로세스가 항상 실패 종료되는 것은 아니다. CI에서 성능 기준을 강제하려면 Threshold를 선언해야 한다.

### Tag·Group·Trend

- **Tag**: 요청에 `name` 같은 라벨을 붙여 API별 지표를 나눈다.
- **Group**: 여러 요청을 목록·생성·수정·삭제 같은 시나리오 단계로 묶는다.
- **Trend**: `flow_duration`처럼 원하는 시간 값의 분포를 직접 기록한다.

```javascript
const flowDuration = new Trend('flow_duration');

const res = http.get(url, {
  headers,
  tags: { name: 'diary-list' },
});

flowDuration.add(Date.now() - flowStart);
```

### Executor와 부하 모델

`diary-scenario.js`는 `ramping-vus`를 사용한다. 시간에 따라 VU 수를 올리고 내리는 **Closed Model**이다. 서버가 느려지면 각 VU의 반복 속도도 느려져 초당 요청 수가 줄어든다.

`diary-stress.js`는 `ramping-arrival-rate`를 사용한다. 서버가 느려져도 설정한 속도로 새 반복을 시작하는 **Open Model**이다. 준비한 VU로 속도를 맞추지 못하면 `dropped_iterations`가 증가한다.

| 목적 | 적합한 Executor |
| --- | --- |
| 동시 사용자 행동 재현 | `ramping-vus` |
| 정해진 요청 속도로 한계 탐색 | `ramping-arrival-rate` |

## 프로젝트 적용

### 인프라 파일

| 파일 | 역할 |
| --- | --- |
| `infra/docker-compose.db.yml` | MariaDB·Volume·Docker Network 생성 |
| `infra/docker-compose.k6.yml` | k6 컨테이너 기동과 스크립트·결과 마운트 |
| `infra/mariadb/init/01-schema.sql` | DB 최초 생성 시 스키마·시드 적용 |

### k6 스크립트

| 파일 | 부하 목적 |
| --- | --- |
| `infra/k6/scripts/hello.js` | 1 VU로 로그인 기능과 k6 연결 확인 |
| `infra/k6/scripts/diary-list.js` | `setup()`의 JWT를 재사용해 목록 조회 측정 |
| `infra/k6/scripts/diary-scenario.js` | VU 0 → 10 → 50 → 100 → 0 CRUD 시나리오 |
| `infra/k6/scripts/diary-stress.js` | Arrival Rate를 높여 시스템 포화 탐색 |
| `infra/k6/results/summary.json` | 실행 결과를 구조화한 JSON 파일 |

### CRUD 시나리오

`diary-scenario.js`의 VU 하나는 다음 행동을 반복한다.

```text
목록 조회 → 1초 대기
  → 일기 생성 → 2초 대기
  → 일기 수정 → 1초 대기
  → 일기 삭제
```

`sleep()`은 사용자가 화면을 읽고 다음 행동을 하는 Think Time을 흘내 낸다. 그러므로 `flow_duration` 약 4초 중 대부분은 서버 응답 시간이 아니라 의도한 대기 시간이다.

### 시나리오 Threshold

```javascript
thresholds: {
  http_req_failed: ['rate<0.01'],
  'http_req_duration{name:diary-list}': ['p(95)<500'],
  'http_req_duration{name:diary-create}': ['p(95)<800'],
  'http_req_duration{name:diary-update}': ['p(95)<800'],
  'http_req_duration{name:diary-delete}': ['p(95)<500'],
  flow_duration: ['p(95)<5000'],
}
```

읽기 요청과 쓰기 요청은 성격이 다르므로 같은 p95 기준을 무조건 적용하지 않는다. 학습용 기준으로 시작하고 실제 사용자 기대와 Baseline에 맞게 조정한다.

## 실습 내용

### 1. DB와 k6 기동

DB Compose가 Docker Network를 만들므로 반드시 DB를 먼저 기동한다.

```bash
docker compose -f infra/docker-compose.db.yml up -d
docker compose -f infra/docker-compose.db.yml ps

docker compose -f infra/docker-compose.k6.yml up -d
docker compose -f infra/docker-compose.k6.yml ps
```

DB의 `STATUS`에 `(healthy)`가 보인 뒤 애플리케이션을 시작한다.

### 2. Spring Boot 기동과 헬스체크

Spring Boot는 Mac 호스트에서 실시간 디버겅이 가능한 방식으로 실행한다.

```bash
./gradlew bootRun
```

다른 터미널에서 확인한다.

```bash
curl http://localhost:8080/actuator/health
```

`status` 값이 `UP`이어야 한다.

### 3. k6 전용 계정 준비

k6 스크립트는 다음 계정으로 로그인한다.

```json
{
  "email": "k6@test.com",
  "password": "k6pass1234",
  "nickname": "k6유저"
}
```

계정이 없다면 Postman에서 `POST /api/auth/signup`으로 한 번만 생성한다. 매 반복마다 회원가입하면 두 번째 실행부터 중복 이메일 오류가 생기므로 `setup()`에서 로그인만 한다.

### 4. Hello k6

```bash
docker compose -f infra/docker-compose.k6.yml exec k6 \
  k6 run /scripts/hello.js
```

1 VU가 5초 동안 로그인을 반복하며 `status 200`, `has accessToken`을 확인한다. 이 단계에서 연결·계정·스크립트 문제를 먼저 제거한다.

### 5. JWT 재사용 목록 조회

```bash
docker compose -f infra/docker-compose.k6.yml exec k6 \
  k6 run /scripts/diary-list.js
```

`setup()`에서 로그인을 한 번만 수행하고 받은 JWT를 10 VU가 공유한다. 이로써 로그인 BCrypt 비용이 목록 조회 지표에 섞이는 것을 막는다.

### 6. CRUD Ramp-up 시나리오

```bash
docker compose -f infra/docker-compose.k6.yml exec k6 \
  k6 run --summary-export=/results/summary.json \
  /scripts/diary-scenario.js
```

부하는 다음과 같이 변한다.

```text
30초: 0 → 10 VU
1분: 10 → 50 VU
2분: 50 → 100 VU
30초: 100 → 0 VU
```

### 7. Stress 시나리오

```bash
docker compose -f infra/docker-compose.k6.yml exec k6 \
  k6 run /scripts/diary-stress.js
```

`diary-stress.js`는 Think Time을 제거하고 Arrival Rate를 높여 한계를 탐색한다. 로컬 노트북 자원을 강하게 사용할 수 있으므로 애플리케이션·DB·Docker 메모리와 CPU를 보며 실행한다.

## 실행 및 검증

### 실행 전 체크리스트

```bash
docker version
docker compose version
docker compose -f infra/docker-compose.db.yml ps
docker compose -f infra/docker-compose.k6.yml ps
curl http://localhost:8080/actuator/health
```

확인할 상태:

- Docker Client와 Server 버전이 모두 출력된다.
- MariaDB가 `healthy`다.
- `emotiondiary-k6`가 `Up` 상태다.
- Spring Boot 헬스체크가 `UP`이다.
- `k6@test.com` 계정으로 로그인할 수 있다.

### CLI 결과 읽기

| 지표 | 의미 |
| --- | --- |
| `checks_succeeded` | `check()` 조건의 성공 비율 |
| `http_req_duration` | HTTP 요청 응답 시간 분포 |
| `http_req_failed` | k6가 실패로 판단한 HTTP 요청 비율 |
| `http_reqs` | 전체 HTTP 요청 수와 초당 요청 수 |
| `iteration_duration` | `default()` 한 바퀴 시간. `sleep()` 포함 |
| `iterations` | 완료된 사용자 시나리오 반복 수 |
| `vus` / `vus_max` | 현재 VU와 최대 VU |
| `dropped_iterations` | 설정한 Arrival Rate를 맞추지 못해 시작하지 못한 반복 |

우선순위는 다음과 같다.

1. `setup()`과 Check가 성공했는가?
2. `http_req_failed`가 기준 미만인가?
3. API별 p95 Threshold를 통과했는가?
4. VU가 늘어날 때 TPS·p95·오류율이 어떻게 변했는가?
5. Stress 테스트에서 `dropped_iterations`가 발생했는가?

Check가 100%여도 p95 Threshold를 넘으면 전체 실행은 실패할 수 있다. 반대로 응답이 빠르더라도 401·500 응답이라면 성공한 성능 테스트가 아니다.

### JSON 결과

`--summary-export=/results/summary.json`으로 실행하면 호스트의 다음 파일이 갱신된다.

```text
infra/k6/results/summary.json
```

이 파일은 min·avg·max·p90·p95 등을 구조화해 저장하므로 CI Artifact, Before/After 비교, 자동 리포트에 활용할 수 있다.

### 종료

의존성의 반대 순서로 k6를 먼저 종료한다.

```bash
docker compose -f infra/docker-compose.k6.yml down
docker compose -f infra/docker-compose.db.yml down
```

일반 종료에서는 `down -v`를 사용하지 않는다. `-v`를 붙이면 MariaDB Volume과 실습 데이터가 삭제된다.

## 문제와 해결

### `network emotiondiary-net declared as external, but could not be found`

k6 Compose가 참조할 Docker Network가 아직 없다. DB Compose를 먼저 기동한다.

```bash
docker compose -f infra/docker-compose.db.yml up -d
docker compose -f infra/docker-compose.k6.yml up -d
```

### `localhost:3308 Connection refused`

MariaDB 컨테이너가 기동되었는지와 포트 매핑을 확인한다.

```bash
docker compose -f infra/docker-compose.db.yml ps
docker ps
lsof -nP -iTCP:3308 -sTCP:LISTEN
```

`0.0.0.0:3308->3306/tcp`가 보이고 DB 상태가 `healthy`여야 한다. 접속 정보는 `localhost`, `3308`, `testuser`, `test1234`, `springstudy`다.

### k6에서 Spring Boot에 `connection refused`

k6 출력에 다음과 같은 메시지가 보이면 k6 컨테이너가 호스트의 8080에 연결하지 못한 것이다.

```text
dial tcp ...:8080: connect: connection refused
```

1. Spring Boot 프로세스가 계속 실행 중인지 확인한다.
2. `curl http://localhost:8080/actuator/health`를 확인한다.
3. k6 스크립트의 BASE URL이 `localhost`가 아니라 `host.docker.internal:8080`인지 확인한다.
4. 애플리케이션이 부하 중 종료됐다면 JVM·DB 로그에서 원인을 확인한다.

로컬 헬스체크가 UP이어도 **부하 실행 중** 애플리케이션이 종료될 수 있다. 오류가 발생한 시점의 프로세스 상태를 다시 확인한다.

### `the body is null so we can't transform it to JSON`

연결 실패 또는 빈 응답에서 바로 `res.json('id')`를 호출했을 때 발생한다. 상태 코드와 본문을 확인한 뒤 JSON을 읽도록 방어한다.

```javascript
let diaryId;

if (res.status === 201 && res.body) {
  diaryId = res.json('id');
}

check(res, {
  'create 201': (r) => r.status === 201,
  'has id': () => diaryId !== undefined,
});
```

이 방어 코드는 스크립트 예외로 VU가 중단되는 것을 막아 실제 HTTP 실패 지표를 끝까지 수집하게 해준다.

### `setup()`에서 로그인이 실패한다

- `k6@test.com` 계정이 DB에 있는지 확인한다.
- 이메일과 비밀번호가 스크립트와 같은지 확인한다.
- Postman으로 같은 로그인 요청을 먼저 확인한다.
- DB Volume을 삭제했다면 테스트 계정을 다시 생성한다.

### Threshold를 넘어 k6가 실패로 종료된다

이것은 k6 오류가 아니라 선언한 성능 기준을 시스템이 충족하지 못했다는 결과다. 실패한 Threshold가 오류율인지 p95인지 확인하고, 부하 구간의 애플리케이션·DB 지표와 로그를 함께 봐야 한다.

### `checks` 성공률은 100%인데 실행이 실패했다

Check과 Threshold는 별개다. 모든 응답이 200이어도 p95가 기준을 넘으면 Threshold 실패로 종료된다. CLI의 `THRESHOLDS` 영역을 확인한다.

### `http_req_duration` 평균과 `iteration_duration`이 크게 다르다

`iteration_duration`은 HTTP 응답 시간 뿐 아니라 스크립트 실행·여러 요청·`sleep()`을 모두 포함한다. CRUD 시나리오에는 총 4초의 Think Time이 있으므로 약 4초 이상이 정상이다.

### DB 초기화 SQL이 다시 실행되지 않는다

`docker-entrypoint-initdb.d` SQL은 **Volume이 빈 최초 생성 시에만** 실행된다. 컨테이너만 재시작해서는 다시 실행되지 않는다.

데이터를 완전히 초기화하려면 Volume 삭제가 필요하지만, 모든 DB 데이터가 삭제되므로 백업과 대상을 확인한 뒤 실행해야 한다.

## 정리

- Spring Boot는 호스트, MariaDB와 k6는 Docker 컨테이너에서 실행한다.
- k6 컨테이너에서 호스트 애플리케이션은 `host.docker.internal:8080`으로 호출한다.
- 호스트에서 MariaDB는 `localhost:3308`로 접속한다.
- DB Compose를 먼저 기동해 `emotiondiary-net`을 만든 뒤 k6 Compose를 기동한다.
- `setup()`은 로그인처럼 공유할 준비 작업, `default()`는 VU가 반복할 사용자 행동을 담는다.
- `check()`는 개별 응답의 기능, Threshold는 전체 집계의 성능 기준을 검증한다.
- Tag로 API별 지표를 나누고 Trend로 CRUD 전체 시간을 기록한다.
- `ramping-vus`는 동시 사용자 재현, `ramping-arrival-rate`는 정해진 속도의 요청 유지에 적합하다.
- 결과는 Check 성공률 → 오류율 → API별 p95 → TPS·VU·Dropped Iteration 순으로 읽는다.
- 응답 실패 시 JSON을 바로 파싱하지 말고 상태 코드와 본문을 먼저 확인한다.
- `summary.json`을 남겨 다음 변경의 Before/After Baseline으로 사용한다.
- 7단원에서는 k6 부하 시각의 Spring Boot 메트릭을 Prometheus·Grafana로 관측한다.
