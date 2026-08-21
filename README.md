# LoL Manager

LoL Manager는 프로 경기의 챔피언 선택과 밴픽 판단, 한 경기의 전투·경제·오브젝트·구조물 진행을 모델링하는 시뮬레이션 프로젝트다. 백엔드는 seed 기반 경기 timeline과 snapshot을 만들고, 프론트엔드는 이를 시간 순서대로 재생한다. Champion, Player, Draft 데이터는 구조화된 identity와 versioned resource를 중심으로 관리한다.

## 주요 기능

- 10초 tick 기반 Match Simulation과 structured event/snapshot timeline
- Champion Catalog, Champion Power, role-aware Matchup, 5인 Composition resource/evaluator
- Player Ratings catalog와 player × champion/role Proficiency 도메인
- professional 5-ban/5-pick Draft AI, flex-role 유지, Hard Fearless series history
- seed 재현, progression, lane/gank/roam, objective, macro, structure/end-game 처리
- React UI의 champion selection, timeline playback, 속도 조절, 경기 상태 시각화

각 기능의 구현 여부와 HTTP runtime 활성화 여부는 동일하지 않다. 현재 wiring은 [프로젝트 상태](docs/project-status.md)를 기준으로 확인한다.

## 기술 스택

| 영역 | 현재 구성 |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5.3, Gradle |
| Test | JUnit Platform, Spring Boot Test, AssertJ |
| Frontend | React 18.3, TypeScript 5.5, Vite 5.4 |
| Data | versioned JSON resources |

## Repository 구조

| 경로 | 책임 |
| --- | --- |
| `backend/` | Spring Boot API, draft engine, match simulator, production resources, tests/diagnostics |
| `frontend/` | champion selection과 match timeline을 표시하는 React 애플리케이션 |
| `docs/` | architecture, data contract, 개발 절차, 현재 상태 |

`build/`, `dist/`, `node_modules/`, 로그와 diagnostic artifact는 source of truth가 아니다.

## 실행 방법

Backend:

```bash
cd backend
./gradlew bootRun
```

Frontend:

```bash
cd frontend
npm ci
npm run dev
```

기본 개발 주소는 backend `http://localhost:8080`, frontend `http://localhost:5173`이다. 자세한 환경 및 Windows 명령은 [Getting Started](docs/development/getting-started.md)를 참고한다.

## 테스트

```bash
cd backend
./gradlew test
```

Frontend에는 별도 test script가 없으며 정적 build 검증은 `npm run build`를 사용한다. focused test와 대규모 diagnostic의 경계는 [Testing](docs/development/testing.md)에 정리되어 있다.

## Documentation

- [Architecture Overview](docs/architecture/overview.md)
- [Match Simulation](docs/architecture/match-simulation.md)
- [Champion System](docs/architecture/champion-system.md)
- [Draft System](docs/architecture/draft-system.md)
- [Player System](docs/architecture/player-system.md)
- [Champion Resource Schema](docs/reference/champion-resource-schema.md)
- [Player Data Schema](docs/reference/player-data-schema.md)
- [Adding a Champion](docs/development/adding-a-champion.md)
- [Project Status](docs/project-status.md)
- [Architecture Decision Records](docs/adr/README.md)
