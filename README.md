# 📈 주식 모니터 (Stock Monitor)

기술적 지표 시각화 + 매매 시그널 + **병맛 밈 차트 분석**을 제공하는 주식 모니터링 앱입니다.

- **백엔드**: Java 21 · Spring Boot 3 (Gradle)
- **프론트엔드**: React 18 · TypeScript · Vite · lightweight-charts

## 주요 기능

### 📊 지표 시각화
- 일봉 캔들차트 + 이동평균선(MA5/20/60/120) + 볼린저밴드(20, 2σ) + 거래량
- RSI(14), MACD(12, 26, 9), 스토캐스틱 보조지표 패널 (시간축 동기화)
- 관심 종목 워치리스트(30일 스파크라인, 실시간 갱신), 시장 요약(상승/하락, 등락률 상위)

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
| 🧘 무념무상 차트 | 아무 패턴도 아님 (해탈) |

## 실행 방법

### 1) 백엔드 (포트 8080)
```bash
cd backend
gradle bootRun          # 또는 ./gradlew bootRun (wrapper 생성 시)
```

### 2) 프론트엔드 (포트 5173)
```bash
cd frontend
npm install
npm run dev
```

브라우저에서 http://localhost:5173 접속. Vite dev 서버가 `/api` 요청을 8080 백엔드로 프록시합니다.

## 데이터 소스

기본 내장 **시장 시뮬레이터**가 종목별 시드 기반의 일봉 히스토리(420거래일)를 생성하고,
3초마다 마지막 캔들을 갱신해 실시간 시세를 흉내냅니다(장 마감/휴장 개념 없이 항상 동작).
실제 시세 연동이 필요하면 `backend/src/main/java/com/stockmonitor/market/MarketDataService.java`를
실데이터 제공자(한국투자증권 OpenAPI, Yahoo Finance, Alpha Vantage 등)로 교체하면 됩니다 —
API 계층은 그대로 재사용됩니다.

## API 요약

| 엔드포인트 | 설명 |
|---|---|
| `GET /api/stocks` | 전체 종목 시세 + 스파크라인 |
| `GET /api/stocks/{symbol}` | 개별 종목 시세 |
| `GET /api/stocks/{symbol}/candles?days=N` | 일봉 OHLCV |
| `GET /api/stocks/{symbol}/indicators?days=N` | 기술적 지표 시리즈 |
| `GET /api/stocks/{symbol}/signals` | 매매 시그널 + 종합 점수 |
| `GET /api/stocks/{symbol}/meme` | 병맛 밈 차트 분석 |
| `GET /api/market/summary` | 시장 요약 |

## 테스트

```bash
cd backend && gradle test     # 서비스 스모크 테스트
cd frontend && npm run build  # 타입체크 + 프로덕션 빌드
```
