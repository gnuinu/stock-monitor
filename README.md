# 📈 주식 모니터 (Stock Monitor)

기술적 지표 시각화 + 매매 시그널 + **병맛 밈 차트 분석**을 제공하는 주식 모니터링 앱입니다.

- **백엔드**: Java 21 · Spring Boot 3 (Gradle)
- **프론트엔드**: React 18 · TypeScript · Vite · lightweight-charts

## 주요 기능

### 📊 지표 시각화
- 일봉 캔들차트 + 이동평균선(MA5/20/60/120) + 볼린저밴드(20, 2σ) + 거래량
- RSI(14), MACD(12, 26, 9), 스토캐스틱 보조지표 패널 (시간축 동기화)
- 관심 종목 워치리스트(30일 스파크라인, 실시간 갱신), 시장 요약(상승/하락, 등락률 상위)
- **검색·필터·즐겨찾기**: 종목명/코드 검색, 시장(KOSPI/KOSDAQ/NASDAQ) 필터, ⭐ 즐겨찾기(브라우저 로컬 저장)

### 💼 모의투자 (Paper Trading)
- 현재가 기준으로 매수/매도, 보유 종목·평균단가·평가손익(P&L)·수익률을 실시간 추적
- 초기 예수금 1억 원, 미국 종목은 고정 환율(1 USD = 1,350원)로 단일 원화 계좌 환산
- 종목 상세 페이지의 매매 위젯 또는 모의투자 페이지에서 확인, 계좌 초기화 지원
- 인메모리 데모(프로세스 재시작 시 초기화), 실제 체결·수수료·세금은 반영하지 않음

### 🎯 매매 시그널
골든/데드 크로스, 정배열/역배열, RSI·스토캐스틱 과매수/과매도, MACD 교차,
볼린저 밴드 이탈·스퀴즈, 거래량 급증, 이격도 과열/침체 등 규칙 기반 시그널을 감지하고
-100(매도) ~ +100(매수) 종합 점수 게이지로 보여줍니다.
> 참고 자료일 뿐 투자 권유가 아닙니다.

### 🤪 병맛 차트 분석소 (재미용)
최근 60거래일 추이를 인터넷 밈 패턴과 대조해 유사도를 채점합니다.

| 패턴 | 설명 |
|---|---|
| 👦 기영이 차트 | 평평한 횡보 후 이마 라인 수직 낙하 |
| 🤸 앞구르기 차트 | 상승 → 정점 → 둥글게 말리는 하락 |
| 🤸‍♂️ 뒤구르기 차트 | 하락 → 바닥 → 둥근 반등 |
| 🚀 가즈아 로켓 차트 | 교과서적 우상향 |
| 🌊 폭포수 차트 | 낙차 큰 연속 하락 |
| 🫀 심정지 차트 | 변동성 실종 횡보 |
| ✌️ 브이(V) 차트 | 급락 후 급반등 |
| 🍽️ 설거지 차트 | 거래량 동반 급등 후 수직 낙하 |
| 🪜 지옥의 계단 차트 | 급락과 횡보의 반복 하강 |
| 🕳️ 지하실 차트 | 신저가 행진 |
| 🎢 롤러코스터 차트 | 큰 폭의 등락 반복 (방향성 없음) |
| 😇 천국의 계단 차트 | 급등과 횡보를 반복하는 상승 |
| 🦀 게걸음 차트 | 방향 없이 옆으로만 횡보 |
| 🧘 무념무상 차트 | 아무 패턴도 아님 (해탈) |

## 실행 방법

### 1) 백엔드 (포트 8080)
```bash
cd backend
gradle bootRun          # 기본: 시뮬레이터 (키 불필요). 실시세 연결은 아래 "데이터 소스" 참고
```

### 2) 프론트엔드 (포트 5173)
```bash
cd frontend
npm install
npm run dev
```

브라우저에서 http://localhost:5173 접속. Vite dev 서버가 `/api` 요청을 8080 백엔드로 프록시합니다.

## 데이터 소스

데이터 제공자는 `stockmonitor.market.provider` 설정으로 선택합니다. REST/프론트 계층은
제공자와 무관하게 동일하게 동작합니다 (`MarketDataProvider` 추상화).

| provider | 설명 | 키/계좌 필요 |
|---|---|---|
| `simulated` (기본값) | 종목별 시드 기반 일봉 히스토리 생성 + 실시간 틱 시뮬레이션. 네트워크 없이 항상 동작 | 없음 |
| `yahoo` | **Yahoo Finance** 실시세 (국내 KRX + 미국). 공개 chart 엔드포인트 사용 | 없음 (키·계좌 불필요) |

### Yahoo Finance 실시세 연결

API 키도 계좌도 필요 없습니다. provider만 `yahoo`로 지정하면 됩니다:

```bash
cd backend
export MARKET_PROVIDER=yahoo
export MARKET_REFRESH_MS=10000   # 과도한 호출 방지 (권장 10초 이상)
gradle bootRun
```

동작 방식:
- 기동 시 종목별 일봉 히스토리를 Yahoo `chart` API(`/v8/finance/chart/{symbol}`)로 조회하고, 이후 최신 캔들을 주기적으로 갱신합니다.
- 국내 종목은 자동으로 `.KS`(KOSPI)·`.KQ`(KOSDAQ) 접미사를 붙여 조회합니다 (예: `005930.KS`). 미국 종목은 티커 그대로 사용합니다.
- **폴백 안전장치**: 네트워크 장애나 특정 종목 조회 실패 시 해당 종목만 자동으로 시뮬레이터 데이터로 대체되어 앱이 절대 멈추지 않습니다. 대시보드의 "시장 분위기" 배지에 현재 데이터 소스(`Yahoo Finance 실시간` / `일부 실시간` / `시뮬레이션 데이터`)가 표시됩니다.

> Yahoo Finance 공개 엔드포인트는 비공식 API이며, 과도한 호출 시 일시적으로 제한될 수 있습니다. `YAHOO_RANGE`(기본 `1y`), `YAHOO_HISTORY_REFRESH_MS` 등으로 조정할 수 있습니다.

관련 코드: `backend/src/main/java/com/stockmonitor/market/` (provider 추상화),
`.../market/yahoo/` (Yahoo 클라이언트·매핑). 새 제공자는 `MarketDataProvider`를 구현해 추가하면 됩니다.

## API 요약

| 엔드포인트 | 설명 |
|---|---|
| `GET /api/stocks` | 전체 종목 시세 + 스파크라인 |
| `GET /api/stocks/{symbol}` | 개별 종목 시세 |
| `GET /api/stocks/{symbol}/candles?days=N` | 일봉 OHLCV |
| `GET /api/stocks/{symbol}/indicators?days=N` | 기술적 지표 시리즈 |
| `GET /api/stocks/{symbol}/signals` | 매매 시그널 + 종합 점수 |
| `GET /api/stocks/{symbol}/meme` | 병맛 밈 차트 분석 |
| `GET /api/market/summary` | 시장 요약 + `dataSource`(simulated/yahoo)·`fallbackSymbols` |
| `GET /api/portfolio` | 모의투자 계좌(현금·보유종목·손익) |
| `POST /api/portfolio/orders` | 매수/매도 `{symbol, side: BUY\|SELL, quantity}` |
| `POST /api/portfolio/reset` | 계좌 초기화 |

## 테스트

```bash
cd backend && gradle test     # 서비스 스모크 테스트
cd frontend && npm run build  # 타입체크 + 프로덕션 빌드
```
