# Getting Started

## Prerequisites

- JDK 21. `backend/build.gradle`의 Gradle toolchain이 Java 21을 요구한다.
- Node.js와 npm. repository에 Node version pin(`.nvmrc`, `engines`)은 없다.
- Git.

Gradle은 repository wrapper를 사용하므로 system Gradle 설치는 필요하지 않다. 별도 database, migration, 필수 환경 변수는 현재 코드에서 확인되지 않는다.

## Backend 실행

Linux/macOS/WSL:

```bash
cd backend
./gradlew bootRun
```

Windows PowerShell/CMD:

```bat
cd backend
gradlew.bat bootRun
```

Spring Boot 기본 port는 `8080`이다. 현재 API는 다음 두 endpoint를 제공한다.

- `GET http://localhost:8080/api/champions`
- `POST http://localhost:8080/api/matches/simulate`

`MatchController`와 `ChampionController`의 CORS origin은 `http://localhost:5173`으로 고정되어 있다.

## Frontend 실행

새 checkout에서는 lockfile 기준으로 dependency를 설치한다.

```bash
cd frontend
npm ci
npm run dev
```

`frontend/vite.config.ts`가 dev server port를 `5173`으로 지정한다. `frontend/src/App.tsx`는 backend를 `http://localhost:8080`으로 직접 호출하므로 두 process를 함께 실행한다.

## 기본 확인 흐름

1. backend를 실행한다.
2. frontend를 실행한다.
3. browser에서 `http://localhost:5173`을 연다.
4. champion selection과 seed를 확인하고 simulation을 시작한다.
5. 같은 seed와 selection을 다시 보내 timeline이 재현되는지 확인할 수 있다.

Seed 입력을 비우면 frontend가 `Date.now()`로 seed를 만들어 request에 포함한다. API를 직접 호출하면서 seed까지 생략하면 backend controller가 `System.currentTimeMillis()`를 사용한다. 재현에는 response의 seed를 보존해야 한다.

## Test / Build

Backend 전체 unit/integration test:

```bash
cd backend
./gradlew test
```

Frontend production build:

```bash
cd frontend
npm run build
```

Frontend `package.json`에는 현재 test script가 없다. test 종류와 diagnostic 실행 경계는 [Testing](testing.md)에 있다.

## Resource 위치

| 경로 | 내용 |
| --- | --- |
| `backend/src/main/resources/champions/champion-resource-manifest.json` | active champion resource 선택 |
| `backend/src/main/resources/champions/` | Catalog, Power, Matchup, Composition, Jungle Clear |
| `backend/src/main/resources/draft/` | Draft Meta |
| `backend/src/main/resources/players/` | Player Ratings |
| `backend/src/main/resources/application.properties` | Spring application name; 추가 runtime 설정 없음 |

Champion resource를 바꿀 때는 개별 JSON만 보지 말고 manifest와 completeness contract를 함께 확인한다. 신규 champion 절차는 [Adding a Champion](adding-a-champion.md)을 따른다.
