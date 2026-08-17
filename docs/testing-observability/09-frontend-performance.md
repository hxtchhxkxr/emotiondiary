# 09. 프론트엔드 성능 최적화

## 학습 목표

- 서버 응답시간과 사용자가 느끼는 화면 속도가 다른 이유를 설명할 수 있다.
- LCP·INP·CLS와 Lighthouse의 주요 지표를 이해할 수 있다.
- Chrome Lighthouse로 최적화 전후를 같은 조건에서 측정할 수 있다.
- 이미지, CSS, JavaScript의 대표적인 성능 문제를 개선할 수 있다.
- gzip과 Cache-Control이 정적 리소스 전송에 주는 효과를 확인할 수 있다.
- 측정 결과를 근거로 가장 영향이 큰 문제부터 개선할 수 있다.

## 핵심 개념

### 서버가 빨라도 화면은 느릴 수 있다

사용자가 느끼는 속도는 Spring Boot의 API 처리시간만으로 결정되지 않는다.

```text
사용자 체감 속도
  = DNS·TLS·네트워크 시간
  + 서버 처리시간
  + HTML·CSS·JS·이미지 다운로드
  + JavaScript 실행
  + 브라우저 화면 그리기
```

Grafana에서 서버 p95가 50ms여도 2MB 이미지와 큰 JavaScript가 화면을 막으면 사용자는 몇 초를 기다릴 수 있다. 따라서 서버와 브라우저를 각각 측정해야 한다.

### 브라우저가 화면을 그리는 흐름

브라우저는 대략 다음 순서로 페이지를 처리한다.

```text
HTML 다운로드·분석
        ↓
CSS 다운로드·분석
        ↓
JavaScript 다운로드·실행
        ↓
Layout: 요소의 크기와 위치 계산
        ↓
Paint: 글자·색상·이미지 그리기
        ↓
Composite: 화면에 최종 합성
```

CSS나 동기 JavaScript가 많으면 HTML 분석과 화면 그리기가 중간에 멈출 수 있다. 이를 **렌더링 블로킹**이라고 한다.

### Core Web Vitals

Core Web Vitals는 사용자가 느끼는 로딩 속도, 반응성, 화면 안정성을 나타낸다.

| 지표 | 쉬운 의미 | 좋은 상태 | 주로 확인할 문제 |
| --- | --- | --- | --- |
| LCP | 가장 큰 콘텐츠가 보일 때까지 걸린 시간 | 2.5초 이하 | 큰 이미지, 느린 응답, 늦은 리소스 발견 |
| INP | 클릭·입력 후 화면이 반응하는 시간 | 200ms 이하 | 무거운 JavaScript, Long Task |
| CLS | 로딩 중 화면이 밀린 정도 | 0.1 이하 | 이미지 크기 미지정, 뒤늦게 나타나는 요소 |

기억하기 쉽게 연결하면 다음과 같다.

- **LCP가 나쁘다** → 이미지와 Network 요청 순서를 먼저 본다.
- **INP가 나쁘다** → JavaScript 실행시간과 Main Thread를 본다.
- **CLS가 나쁘다** → 이미지·광고·동적 요소의 자리 확보 여부를 본다.

> CLS는 시간이 아니라 점수이므로 단위가 없다.

### Lighthouse의 보조 지표

| 지표 | 의미 |
| --- | --- |
| TTFB | 요청 후 첫 Byte가 도착할 때까지의 시간 |
| FCP | 첫 Text 또는 Image가 나타난 시간 |
| TBT | 50ms를 넘는 Main Thread 작업의 초과시간 합계 |
| Speed Index | 화면이 시각적으로 채워지는 속도 |

Lighthouse의 TBT는 실험 환경에서 JavaScript Blocking을 확인하는 지표다. 실제 사용자 상호작용 품질은 INP와 함께 판단한다.

### Lab 데이터와 Field 데이터

- **Lab 데이터**: Lighthouse처럼 정해진 Device와 Network 조건에서 측정한 결과다. 반복 비교와 개발 중 진단에 좋다.
- **Field 데이터**: 실제 사용자의 Device·Network에서 수집한 결과다. 배포 후 실제 경험을 보여준다.

로컬 Lighthouse 점수가 좋아졌다고 실제 사용자 모두가 빨라지는 것은 아니다. 배포 후에는 PageSpeed Insights나 RUM 데이터로 다시 확인해야 한다.

### 성능 최적화의 기본 순서

성능 최적화는 느낌이 아니라 숫자를 비교하는 작업이다.

```text
Before 측정
  ↓
가장 큰 병목 한 가지 선택
  ↓
코드 또는 설정 변경
  ↓
같은 조건에서 After 측정
  ↓
효과가 없으면 원인 가설 수정
```

한 번에 모든 것을 바꾸면 어떤 변경이 효과를 냈는지 알기 어렵다. 이미지, JavaScript, CSS, HTTP 설정처럼 항목을 나누어 확인하는 것이 좋다.

## 프로젝트 적용

### 정적 페이지 구조

Spring Boot는 `src/main/resources/static` 아래 파일을 자동으로 제공한다.

```text
src/main/resources/static/
├── index.html
├── css/
│   ├── reset.css
│   ├── layout.css
│   ├── card.css
│   ├── theme.css
│   └── app.min.css
├── js/
│   ├── app.js
│   └── vendor.js
└── images/
    ├── test.png
    └── test.webp
```

브라우저에서 `http://localhost:8080/`을 열면 `index.html`이 제공된다. 페이지의 `app.js`는 데모 계정으로 로그인한 후 최근 일기 목록 API를 호출한다.

> `app.js`에 들어 있는 데모 계정 정보는 로컬 성능 실습용이다. 실제 서비스의 계정이나 비밀번호를 JavaScript에 넣으면 누구나 볼 수 있으므로 사용하면 안 된다.

### Security 설정

프로젝트는 기본적으로 인증된 요청만 허용한다. 정적 페이지를 로그인 없이 내려주려면 다음 경로를 `permitAll()`로 열어야 한다.

```java
.requestMatchers(
        "/",
        "/index.html",
        "/css/**",
        "/js/**",
        "/images/**",
        "/favicon.ico"
).permitAll()
```

이 설정이 없으면 `/` 또는 CSS·JavaScript 요청이 401로 실패해 페이지가 정상적으로 표시되지 않는다.

### Before 상태의 문제

실습의 Before 상태는 성능 차이를 확인하기 위해 일부러 다음 문제를 만든다.

- CSS 파일 4개를 각각 요청한다.
- 약 2.1MB의 PNG 원본을 LCP 이미지로 사용한다.
- 이미지의 `width`와 `height`를 지정하지 않는다.
- `vendor.js`가 큰 객체 처리와 반복문으로 Main Thread를 점유한다.
- JavaScript에 `defer`가 없다.
- gzip과 정적 리소스 Cache를 사용하지 않는다.

이 상태에서 Lighthouse를 먼저 실행해야 개선 전 기준값이 생긴다.

### 현재 프로젝트의 After 상태

현재 프로젝트에는 다음 최적화가 적용되어 있다.

- CSS 4개를 `app.min.css` 하나로 합쳤다.
- `test.png`를 `test.webp`로 변환했다.
- LCP 이미지를 `<link rel="preload">`로 미리 발견하게 했다.
- `<picture>`로 WebP와 PNG Fallback을 제공한다.
- 이미지에 `width`, `height`, `fetchpriority="high"`를 지정했다.
- JavaScript에 `defer`를 지정했다.
- Spring Boot에서 gzip과 30일 정적 리소스 Cache를 설정했다.

`app.min.css`는 현재 네 CSS 파일을 합친 결과다. 파일 이름에 `min`이 있지만 공백 제거까지 완전하게 수행한 Minify 결과는 아니다. 실무에서는 Vite·Webpack·Rollup 같은 Build Tool에 병합과 Minify를 맡기는 것이 안전하다.

### 이미지 최적화

프로젝트 이미지의 실제 크기는 다음처럼 줄었다.

```text
test.png   약 2.1MB
test.webp  약 239KB
```

약 89%가 줄어들어 Network 전송량과 LCP 개선에 가장 큰 영향을 준다.

`index.html`에서는 WebP를 우선 사용하고, 지원하지 않는 Browser를 위해 PNG를 남겨 둔다.

```html
<picture>
    <source srcset="/images/test.webp" type="image/webp">
    <img src="/images/test.png"
         alt="감정 다이어리 배너"
         width="300"
         height="483"
         class="hero-img"
         loading="eager"
         fetchpriority="high">
</picture>
```

- `<source>`: WebP를 지원하면 작은 파일을 사용한다.
- PNG `src`: WebP를 지원하지 않을 때 사용하는 Fallback이다.
- `width`·`height`: 이미지가 오기 전에 자리를 확보해 CLS를 방지한다.
- `loading="eager"`: 첫 화면의 핵심 이미지를 즉시 불러온다.
- `fetchpriority="high"`: Browser에 높은 우선순위를 알려준다.

LCP 후보인 Hero Image에는 `loading="lazy"`를 사용하지 않는다. 화면 아래쪽 Thumbnail처럼 처음에 보이지 않는 이미지에만 Lazy Loading을 사용한다.

```html
<img src="/images/thumb.webp"
     width="80"
     height="80"
     loading="lazy"
     alt="">
```

### LCP 이미지 Preload

`index.html`의 `<head>`에는 다음 설정이 있다.

```html
<link rel="preload"
      as="image"
      href="/images/test.webp"
      type="image/webp"
      fetchpriority="high">
```

Browser가 HTML을 읽는 초기에 LCP Image를 발견하고 다운로드를 시작하도록 돕는다. 모든 이미지를 Preload하면 오히려 Network 경쟁이 생기므로 첫 화면의 가장 중요한 이미지에만 사용한다.

### JavaScript의 defer와 async

현재 스크립트에는 `defer`가 적용되어 있다.

```html
<script src="/js/vendor.js" defer></script>
<script src="/js/app.js" defer></script>
```

| 방식 | Download | 실행 시점 | 순서 보장 |
| --- | --- | --- | --- |
| 속성 없음 | HTML 분석을 막을 수 있음 | Download 직후 | O |
| `async` | HTML과 병렬 | Download가 끝나는 즉시 | X |
| `defer` | HTML과 병렬 | HTML 분석 완료 후 | O |

앞 Script에 의존하는 Application Script는 순서가 보장되는 `defer`가 알맞다. 다른 코드와 독립적인 분석 Script는 `async`를 고려할 수 있다.

`defer`는 실행 시점을 늦출 뿐 무거운 코드를 없애지는 않는다. `vendor.js`의 CPU 작업 자체를 제거하거나 필요한 시점에만 불러오는 것이 최종 해결책이다.

### CSS 병합

Before 상태에서는 네 개의 CSS 요청이 필요했다.

```html
<link rel="stylesheet" href="/css/reset.css">
<link rel="stylesheet" href="/css/layout.css">
<link rel="stylesheet" href="/css/card.css">
<link rel="stylesheet" href="/css/theme.css">
```

After 상태에서는 하나만 요청한다.

```html
<link rel="stylesheet" href="/css/app.min.css">
```

HTTP/2 이상에서는 여러 파일 요청 비용이 과거보다 작지만, 초기 화면을 막는 작은 CSS가 지나치게 나뉘어 있다면 병합이 도움이 될 수 있다. 무조건 하나로 합치기보다 실제 Waterfall과 Cache 효율을 함께 본다.

### gzip 압축과 Cache

`application.yaml`에는 다음 설정이 적용되어 있다.

```yaml
server:
  compression:
    enabled: true
    mime-types: text/html,text/css,text/javascript,application/javascript,application/json,image/svg+xml
    min-response-size: 1KB

spring:
  web:
    resources:
      cache:
        cachecontrol:
          max-age: 30d
          cache-public: true
      chain:
        strategy:
          content:
            enabled: true
            paths: /**
```

- gzip은 HTML·CSS·JavaScript 같은 Text Resource의 전송 크기를 줄인다.
- `min-response-size: 1KB`보다 작은 응답은 압축 비용이 더 클 수 있어 압축하지 않는다.
- Cache-Control은 Browser가 정적 파일을 30일 동안 재사용할 수 있게 한다.
- 이 Cache 설정은 `/api/**` 응답이 아니라 Spring의 정적 리소스에 적용된다.

PNG와 WebP는 이미 압축된 형식이라 gzip 효과가 작다. 이미지 자체의 Format과 Size를 줄이는 것이 더 중요하다.

> 오래 Cache하는 파일을 변경했는데 URL이 그대로면 Browser가 이전 파일을 계속 사용할 수 있다. 정적 HTML에 경로를 직접 작성하는 현재 구조에서는 Build Tool로 Hash가 포함된 Filename을 만들거나 Version Query를 관리하는 Cache Busting 전략이 필요하다.

## 실습 내용

### 1. 데모 데이터 준비

DB Container와 Spring Boot를 실행한다.

```bash
docker compose -f infra/docker-compose.db.yml up -d
./gradlew bootRun
```

Postman에서 데모 회원을 한 번 생성한다.

```http
POST http://localhost:8080/api/auth/signup
Content-Type: application/json
```

```json
{
  "email": "demo@test.com",
  "password": "pw12345!",
  "nickname": "demo"
}
```

이미 같은 이메일이 있다면 다시 가입할 필요가 없다. 로그인 후 Access Token을 받은 다음 일기 작성 API로 화면에 표시할 데이터를 등록한다.

```http
POST http://localhost:8080/api/diaries
Authorization: Bearer 발급받은_accessToken
Content-Type: application/json
```

```json
{
  "date": 20260817,
  "content": "프론트엔드 성능 실습 일기",
  "emotionId": 5
}
```

`date`는 프로젝트에서 사용하는 `YYYYMMDD` 숫자 형식으로 입력한다. 등록 후 `http://localhost:8080/`에서 로그인 완료 문구와 일기 목록이 보이는지 확인한다.

### 2. Lighthouse Before 측정

최적화 전 Commit 또는 Before 상태에서 측정한다.

1. Chrome에서 `http://localhost:8080/`을 연다.
2. 우클릭 → **검사** 또는 `⌥ Option + ⌘ Command + I`로 DevTools를 연다.
3. 위쪽의 **Lighthouse** Tab을 선택한다.
4. 보이지 않으면 `»`를 누르거나 DevTools 메뉴의 **More tools → Lighthouse**를 선택한다.
5. Mode는 `Navigation`, Device는 `Mobile`을 선택한다.
6. Categories에서 `Performance`, `Best Practices`, `SEO`를 선택한다.
7. **Analyze page load**를 실행한다.

다음 값을 따로 기록한다.

```text
Performance Score:
FCP:
LCP:
TBT:
CLS:
Speed Index:
전송량:
가장 큰 개선 제안:
```

Lighthouse 점수는 실행할 때마다 조금 달라질 수 있으므로 같은 조건에서 3회 측정하고 Median 값을 비교하면 더 안정적이다.

### 3. Network에서 병목 찾기

DevTools의 **Network** Tab에서 다음 순서로 확인한다.

1. Network Tab을 연다.
2. **Disable cache**를 선택한다.
3. 페이지를 새로고침한다.
4. Size 또는 Time 순으로 정렬한다.
5. Waterfall에서 늦게 시작하거나 오래 걸리는 요청을 찾는다.

확인 항목:

- `test.png`가 약 2.1MB인지
- CSS 요청이 여러 개로 나뉘어 있는지
- `vendor.js`가 화면 그리기를 오래 막는지
- 실패한 요청이 빨간색으로 표시되는지

### 4. CSS 병합

macOS·Linux에서 단순히 합치는 실습 명령은 다음과 같다.

```bash
cat src/main/resources/static/css/reset.css \
    src/main/resources/static/css/layout.css \
    src/main/resources/static/css/card.css \
    src/main/resources/static/css/theme.css \
    > src/main/resources/static/css/app.min.css
```

그다음 `index.html`의 CSS Link 네 개를 `/css/app.min.css` 하나로 바꾼다.

이 명령은 병합만 수행한다. 실무의 Minify·Tree Shaking·Source Map 생성은 Frontend Build Tool로 자동화한다.

### 5. PNG를 WebP로 변환

macOS에서 `cwebp`를 설치한다.

```bash
brew install webp
```

이미지를 변환한다.

```bash
cwebp -q 80 src/main/resources/static/images/test.png \
  -o src/main/resources/static/images/test.webp
```

결과 크기를 비교한다.

```bash
ls -lh src/main/resources/static/images/test.*
```

이미지를 CSS로 300px만 표시하더라도 원본이 3729×6000이면 Browser는 큰 원본 전체를 내려받는다. 실제 표시 크기에 맞게 Resize한 뒤 WebP 또는 AVIF로 변환하면 더 줄일 수 있다.

### 6. HTML 최적화

`index.html`에 다음 변경을 적용한다.

1. `<picture>`로 WebP와 PNG Fallback 구성
2. 이미지의 실제 비율에 맞는 `width`·`height` 지정
3. LCP 이미지에 `loading="eager"`, `fetchpriority="high"` 지정
4. `<head>`에서 WebP Preload
5. Script에 `defer` 지정

화면 아래 이미지는 `loading="lazy"`를 사용하지만 LCP Image는 즉시 불러온다.

### 7. gzip과 Cache 확인

Spring Boot를 재시작한 후 Chrome DevTools에서 확인한다.

1. **Network** Tab을 연다.
2. 페이지를 새로고침한다.
3. `app.js` 또는 크기가 1KB 이상인 Text Resource를 클릭한다.
4. **Headers → Response Headers**를 확인한다.

기대하는 Header:

```text
Content-Encoding: gzip
Cache-Control: max-age=2592000, public
```

Terminal에서도 확인할 수 있다.

```bash
curl -I -H 'Accept-Encoding: gzip' http://localhost:8080/js/app.js
curl -I http://localhost:8080/css/app.min.css
```

두 번째 요청의 Browser Cache 여부를 확인할 때는 DevTools의 **Disable cache** 선택을 해제한다.

### 8. Lighthouse After 측정

After 측정은 Before와 같은 설정으로 실행한다.

| 항목 | Before | After | 주요 원인 |
| --- | --- | --- | --- |
| LCP | 직접 기록 | 직접 기록 | WebP, Preload, Priority |
| TBT | 직접 기록 | 직접 기록 | 무거운 JS 제거·분리, defer |
| CLS | 직접 기록 | 직접 기록 | 이미지 크기 지정 |
| Transfer Size | 직접 기록 | 직접 기록 | WebP, gzip, Minify |
| Performance Score | 직접 기록 | 직접 기록 | 전체 개선 결과 |

예상값을 결과처럼 기록하지 않는다. 자신의 Computer에서 직접 측정한 값과 측정 조건을 함께 남긴다.

```text
Chrome Version:
Lighthouse Version:
Device: Mobile
실행 일시:
Before Commit:
After Commit:
```

## 실행 및 검증

### 실행 순서

```bash
# 1. DB 실행
docker compose -f infra/docker-compose.db.yml up -d

# 2. Spring Boot 실행
./gradlew bootRun

# 3. Browser에서 접속
# http://localhost:8080/
```

### 화면 확인

- `Emotion Diary` 제목이 보인다.
- `로그인 완료` 또는 이해 가능한 오류 문구가 표시된다.
- Hero Image가 깨지지 않는다.
- 최근 일기 목록이 표시된다.
- Console에 JavaScript Error가 없다.
- Network Tab에 401·404·500 요청이 없다.

### 파일 크기 확인

```bash
ls -lh src/main/resources/static/images/test.*
ls -lh src/main/resources/static/css/*
ls -lh src/main/resources/static/js/*
```

현재 프로젝트 기준 `test.webp`는 약 239KB로 `test.png` 약 2.1MB보다 훨씬 작다.

### 응답 Header 확인

```bash
curl -I -H 'Accept-Encoding: gzip' http://localhost:8080/js/app.js
curl -I http://localhost:8080/css/app.min.css
```

검증할 값:

- Text Resource에 `Content-Encoding: gzip`이 있는가?
- 정적 Resource에 `Cache-Control: max-age=2592000, public`이 있는가?
- API 응답에 의도하지 않은 30일 Public Cache가 적용되지 않았는가?

### Performance Panel로 Long Task 확인

Lighthouse에서 TBT가 높다면 DevTools의 **Performance** Tab을 사용한다.

1. Record를 시작한다.
2. 페이지를 새로고침한다.
3. Loading이 끝나면 Record를 중지한다.
4. Main Thread에서 50ms가 넘는 긴 Task를 찾는다.
5. Bottom-Up 또는 Call Tree에서 오래 걸린 Function을 확인한다.

현재 `vendor.js`는 Long Task를 재현하기 위한 CPU 작업을 포함한다. `defer`만 붙이고 끝내지 말고 해당 코드가 실제 화면에 필요하지 않다면 제거한 뒤 다시 측정한다.

## 문제와 해결

### `/`에 접속하면 401 또는 403이 나온다

`SecurityConfig`에서 `/`, `/index.html`, `/css/**`, `/js/**`, `/images/**`가 `permitAll()` 대상인지 확인한다. 변경 후 Spring Boot를 재시작한다.

### 페이지에 `로그인 실패`가 표시된다

- `demo@test.com` 계정이 DB에 있는지 확인한다.
- 계정의 Password가 `app.js`의 실습 Password와 같은지 확인한다.
- DevTools Network에서 `/api/auth/login`의 Status와 Response를 확인한다.
- Spring Boot Console과 Kibana에서 같은 요청의 traceId Log를 확인한다.

### 일기 목록이 보이지 않는다

- Login Response에서 Access Token이 정상적으로 내려오는지 확인한다.
- `/api/diaries` 요청에 `Authorization: Bearer ...`가 있는지 확인한다.
- `from`, `to`, `sort` Query Parameter를 확인한다.
- 해당 기간에 작성된 일기가 실제로 있는지 확인한다.

### Thumbnail이 깨지거나 404가 발생한다

현재 `index.html`이 `/images/thumb.webp`를 참조한다면 실제 파일이 존재하는지 확인한다. 파일이 없다면 Thumbnail 예제를 제거하거나 올바른 이미지를 추가한다. 404 요청도 불필요한 Network 비용이므로 성능 측정 전에 정리한다.

### `Content-Encoding: gzip`이 보이지 않는다

- 요청 Header에 `Accept-Encoding: gzip`이 있는지 확인한다.
- 응답 크기가 `min-response-size: 1KB` 이상인지 확인한다.
- MIME Type이 `server.compression.mime-types`에 포함됐는지 확인한다.
- 설정 변경 후 Spring Boot를 재시작했는지 확인한다.

`app.min.css`는 약 626B라 1KB 기준보다 작아 gzip이 적용되지 않는 것이 정상일 수 있다. 크기가 더 큰 `app.js`로 확인한다.

### Cache 변경 내용이 바로 보이지 않는다

30일 Cache 때문에 이전 파일이 재사용될 수 있다.

- DevTools Network의 **Disable cache**를 선택하고 새로고침한다.
- 필요하면 Hard Reload를 실행한다.
- 실무에서는 Filename에 Content Hash를 넣어 URL 자체를 바꾼다.

### Lighthouse Tab이 보이지 않는다

DevTools 상단의 `»`를 누르거나 **⋮ → More tools → Lighthouse**를 선택한다.

### Lighthouse 점수가 실행할 때마다 달라진다

- 같은 Device와 Mode를 사용한다.
- 다른 무거운 Application을 종료한다.
- Chrome Extension의 영향을 줄이기 위해 시크릿 창에서 실행한다.
- 한 번의 최고 점수 대신 3회 측정의 Median을 비교한다.

### LCP가 여전히 높다

- Lighthouse에서 실제 LCP Element가 무엇인지 확인한다.
- Network Waterfall에서 Image 요청 시작이 늦는지 확인한다.
- WebP가 실제로 선택됐는지 확인한다.
- 표시 크기에 비해 원본 해상도가 지나치게 크지 않은지 확인한다.
- TTFB가 높다면 Browser Resource가 아니라 Server·Network 문제도 조사한다.

### TBT가 줄지 않는다

- Performance Panel에서 Long Task의 실제 Function을 찾는다.
- `vendor.js`의 불필요한 CPU 작업을 제거한다.
- 큰 기능은 필요할 때 `import()`하는 Code Splitting을 고려한다.
- `defer`는 실행을 없애지 않는다는 점을 기억한다.
- Chrome Extension 영향을 배제하기 위해 시크릿 창에서도 측정한다.

### CLS가 0인데도 이미지 크기를 지정해야 하는가

현재 Network나 Layout 조건에서 우연히 이동이 없었을 수 있다. `width`와 `height`를 지정하면 Browser가 Download 전에 공간을 확보하므로 다른 Device에서도 Layout Shift를 예방할 수 있다.

## 정리

- 사용자가 느끼는 속도에는 서버 처리뿐 아니라 Network, Resource Download, JavaScript 실행, Rendering이 포함된다.
- LCP는 Loading, INP는 Interaction, CLS는 화면 안정성을 나타낸다.
- Lighthouse Before와 After는 같은 Device·조건에서 측정한다.
- 가장 큰 Resource나 가장 긴 Task처럼 영향이 큰 병목부터 개선한다.
- Hero Image는 WebP·적절한 Size·Preload·높은 Fetch Priority를 사용한다.
- 화면 밖 이미지는 Lazy Loading하고 모든 이미지의 크기를 미리 지정한다.
- CSS 병합과 Minify는 Build Tool로 자동화하는 것이 좋다.
- `defer`는 HTML 분석 Blocking을 줄이지만 불필요한 JavaScript 자체를 없애지는 않는다.
- gzip은 Text Resource의 전송량을 줄이고 Cache-Control은 재방문 요청을 줄인다.
- 정적 Resource를 오래 Cache한다면 Filename Hash 같은 Cache Busting 전략이 필요하다.
- 서버 p95는 Grafana에서, 사용자 화면 성능은 Lighthouse와 실제 사용자 데이터에서 확인한다.
