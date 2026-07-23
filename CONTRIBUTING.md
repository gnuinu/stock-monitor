# 기여 가이드 (Contributing)

## 커밋 메시지 규칙

이 저장소는 [Conventional Commits](https://www.conventionalcommits.org/) 스타일을 따릅니다.
제목은 **한국어 또는 영어** 모두 허용합니다.

### 형식

```
<type>(<scope>): <제목>

<본문 — 선택>

<푸터 — 선택>
```

- `<scope>` 는 생략 가능하지만, 모노레포이므로 **되도록 붙이는 것을 권장**합니다.

### type (필수)

| type | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서만 변경 (README, 주석 등) |
| `style` | 포맷·세미콜론 등 동작에 영향 없는 변경 |
| `refactor` | 기능 변화 없는 코드 구조 개선 |
| `perf` | 성능 개선 |
| `test` | 테스트 추가·수정 |
| `build` | 빌드·의존성·Gradle/npm 설정 변경 |
| `chore` | 기타 잡무 (.gitignore, 설정 파일 등) |

### scope (선택, 권장)

어느 영역을 바꿨는지 표시합니다. 이 프로젝트에서 자주 쓰는 스코프:

`backend`, `frontend`, `market`, `portfolio`, `meme`, `signal`, `indicator`, `infra`

### 제목 규칙

- **50자 이내**, 간결하게
- 명령형·현재형으로 (`추가`, `수정` — `추가했음` ✗)
- 문장 끝에 마침표를 붙이지 않음

### 본문 (선택)

- **무엇을 · 왜** 바꿨는지 적습니다. *어떻게*(코드 세부)는 diff가 말해줍니다.
- 제목과 빈 줄로 분리하고, 한 줄은 72자 이내를 권장합니다.

### 예시

```
feat(portfolio): 모의투자 매수/매도 및 손익 추적 추가
fix(market): Yahoo 일봉의 null 캔들을 건너뛰도록 수정
docs: README 데이터 소스 섹션 갱신
refactor(market): MarketDataProvider 추상화 도입
test(portfolio): 초과 매도·예수금 부족 케이스 검증 추가
build(backend): Gradle Wrapper 추가
chore: .gitignore 에 로컬 설정 파일 추가
```

## 커밋·푸시 흐름

- **작업 전 최신 main 을 받으세요**: `git pull origin main` (협업자와의 충돌 예방)
- 영역이 다른 변경은 **나눠서 커밋**하면 히스토리가 깔끔합니다.
  ```bash
  git add backend/  && git commit -m "feat(market): ..."
  git add frontend/ && git commit -m "feat(frontend): ..."
  ```
- 한 커밋은 **하나의 논리적 변경**만 담습니다. 무관한 변경을 섞지 않습니다.
- 빌드·테스트가 통과하는 상태로 커밋합니다 (`gradle test`, `npm run build`).

## 커밋 템플릿 (선택)

저장소에 포함된 템플릿을 등록하면 커밋 시 형식이 자동으로 채워집니다.

```bash
git config commit.template .gitmessage.txt
```
