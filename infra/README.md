# emotiondiary — infra

단원별로 필요한 인프라 스택을 `docker-compose` 파일로 분리해 누적 기동하는 구조입니다.

## 파일 구성 (예정 포함)

| 파일 | 도입 단원 | 스택 |
|---|---|---|
| `docker-compose.db.yml` | 2단원 (현재) | MariaDB |
| `docker-compose.k6.yml` | 6단원 (예정) | k6 |
| `docker-compose.monitoring.yml` | 7단원 (예정) | Prometheus · Grafana |
| `docker-compose.logging.yml` | 8단원 (예정) | Elasticsearch · Logstash · Kibana |

## 실행 — 2~5단원 (DB 만 필요)

```bash
# emotiondiary/ 에서
docker compose -f infra/docker-compose.db.yml up -d

# 상태 확인 (healthy 가 뜨면 준비 완료)
docker compose -f infra/docker-compose.db.yml ps
```

- 컨테이너 이름: `emotiondiary-db`
- **노출 포트: `3307`** (로컬 MariaDB 3306 과 충돌 피하려 의도적으로 변경)
- 데이터베이스: `springstudy`
- 계정: `testuser / test1234`
- 볼륨: `emotiondiary-db-data` (컨테이너 삭제해도 데이터 유지)
- 네트워크: `emotiondiary-net` (이후 단원의 컨테이너들이 공유)

## Spring Boot 연결 — `application-local.yaml`

```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3307/springstudy   # ← 포트 3307
    username: testuser
    password: test1234
```

로컬 MariaDB(3306) 로 회귀하고 싶다면 url 의 포트만 `3306` 으로 바꾸고 컨테이너는 꺼도 됩니다.

## 초기 데이터

최초 기동 시 `mariadb/init/*.sql` 이 자동 실행되어 스키마와 시드 데이터(`홍길동` 사용자 + 샘플 일기 1건)가 들어갑니다. 재실행 시에는 **기존 볼륨이 이미 초기화되어 있어 init 스크립트가 건너뛰어집니다.**

**초기 데이터를 다시 넣고 싶다면 볼륨까지 삭제 후 재기동:**

```bash
docker compose -f infra/docker-compose.db.yml down -v
docker compose -f infra/docker-compose.db.yml up -d
```

> `down -v` 는 볼륨을 파괴합니다. DB 데이터가 모두 사라지므로 주의.

## 중지 / 제거

```bash
# 컨테이너만 중지 (데이터 유지)
docker compose -f infra/docker-compose.db.yml down

# 컨테이너 + 볼륨 제거 (완전 초기화)
docker compose -f infra/docker-compose.db.yml down -v
```

## 접속 (디버그)

```bash
# 컨테이너 내부 mariadb 클라이언트
docker exec -it emotiondiary-db mariadb -u testuser -p springstudy

# 호스트에서 직접 연결 (HeidiSQL/DBeaver 등)
# Host: localhost   Port: 3307   User: testuser   Pass: test1234   DB: springstudy
```
