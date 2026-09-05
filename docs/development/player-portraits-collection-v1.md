# 선수 사진 수집·화면 연결 결과

2026-09-05 최종: **실제 사진 280/280명 확보·등록, 미확보 0명.** 기본 실루엣은 확보 수에 포함하지 않는다.

초기에는 팀 공식 사이트·공식 Flickr·Commons에서 201명만 확보했다. 이후 사용자의 지적으로 나무위키까지 조사 범위를 넓혀 77명을 추가했고, 마지막 2명은 촬영자와 공식 사진 크레딧이 명시된 인터뷰 사진으로 확보했다. 이전의 79명 미확보 판정은 조사 범위와 문서명 검색의 한계였으며, 사진 자체가 없다는 근거가 아니었다.

| 리그 | 대상 | 확보 | 미확보 | 이번 추가 |
| --- | ---: | ---: | ---: | ---: |
| LCK | 50 | 50 | 0 | 9 |
| LPL | 60 | 60 | 0 | 29 |
| LEC | 50 | 50 | 0 | 9 |
| LCS | 40 | 40 | 0 | 2 |
| LCP | 40 | 40 | 0 | 5 |
| CBLOL | 40 | 40 | 0 | 25 |
| 합계 | 280 | 280 | 0 | 79 |

## 사진과 출처

[사진 매핑](../../frontend/src/features/team-player/player-photos.json)은 기존 playerId 280개를 그대로 사용한다. 로컬 경로·원본 URL·출처 페이지·촬영자·권리자·사용 조건·촬영 연도·당시 팀·상태·신원 대조 근거·확인한 출처 이력을 기록했다. 사진 파일 280개는 [public/images/players](../../frontend/public/images/players)에 있으며 합계 **5,381,182바이트**다. 기존 201장의 파일은 그대로 유지했다.

나무위키 사진 77장은 해당 선수의 인포박스 안에 있는 사진을 선택했다. 닉네임만으로 첫 이미지를 채택하지 않고 실명·소속 이력을 대조했고, 확인 가능한 생일도 비교했다. 동명이인 성우·축구 선수·다른 종목 선수 문서는 제외했다. `photoSourceType`, `sourceIdentity`, `identityEvidence`, `sourcePhotoLabel`, 확인 가능한 `sourceFileNames`를 추가했다. 나무위키의 전체 문서 라이선스를 개별 사진 사용 허가로 간주하지 않았으며, 확인하지 못한 촬영자·권리자·사진 사용 조건은 미확인으로 표시했다.

- Manel: 나무위키 닉네임·실명 검색에서 사진 문서를 확인하지 못하여 [Sheep Esports 인터뷰](https://www.sheepesports.com/en/articles/manel-about-his-cblol-debut-and-fast-growth-at-red-canids-it-s-my-time/en)의 **Bruno Alvares / CBLOL** 크레딧 사진을 사용했다. 본문에서 Emanoel Lopes·Manel·RED를 함께 명시한다.
- KryRa: [나무위키 선수 문서](https://namu.wiki/w/크리스티안%20라하이안) 프로필의 사진 자리가 `width=100%` 문구뿐이어서, [Sheep Esports 인터뷰](https://www.sheepesports.com/fr/articles/dsg-kryra-today-could-be-a-reality-check-for-me-which-will-help-i-will-learn-a-lot-from-this/en)의 **Christian Betancourt / Riot Games** 크레딧 사진을 사용했다. 본문과 캡션에서 Christian Rahaian·KryRa·Disguised를 명시한다.

Commons HTTP 429 이후 조회를 중단한 이력, 실패했던 문서명 검색과 후속 확보 근거는 구분한다. 새 출처를 확인했다고 과거 접근 제한을 우회하거나 삭제하지 않았다. 외부 사진을 게임 실행 중 직접 요청하지 않고 로컬 WebP를 제공한다.

## 연도·당시 팀과 데이터 차이

확인된 촬영 연도는 2026년 47장, 2025년 15장, 2024년 28장, 2023년 13장, 2022년 4장, 2021년 5장, 2018년 1장이다. 기존 이전 시즌 사진 66장은 유지했다. **실제 촬영 연도 미확인 167장**은 `photoYear: null`이다.

나무위키 파일에 표시된 시즌은 촬영 연도와 구분한다. `photoSeasonYear`는 2026 시즌 70장, 2025 시즌 1장(SiuLoong)이며 나머지 6장은 미확인이다. 프로필에도 `2026 시즌`처럼 표시한다. 당시 팀은 파일의 팀 표기가 확인되는 경우 기록하며, 현재 소속만 보고 촬영 당시 팀을 추정하지 않았다. 이전 팀 사진 예: Xiaohao의 EDG, Ycx의 LGD, Stend의 TH, Nia의 IG. 표기가 불명확한 Way·Denathor·Sharvel은 당시 팀을 null로 남겼다.

Curse·Rabelo·STEPZ·Booki·Strensh·Stend·Tracyn·Tangyuan은 나무위키와 기존 career 생일 표기가 다르다. 닉네임·실명·소속 이력으로 같은 선수임을 대조했고 차이는 사진 메타데이터에 기록했다. Casting의 Shin Min-jae / Shin Min-je, zynts의 본문 zyntz / 파일 zynts 등 표기 차이도 함께 기록했다. 이번 작업에서 기존 identity·career·계약·로스터 정보를 고치지 않았다.

Pillow로 방향 보정·비율 유지·최대 512px WebP 변환을 했다. Teddy·Manel·KryRa의 프로필 가독성을 위한 원본 크롭 좌표는 `cropBox`에 기록했다. 얼굴 생성·얼굴 인식은 사용하지 않았다. 원본 다운로드와 조사·브라우저 캐시는 임시 작업 폴더에만 둔다.

## 화면과 변경 파일

LCK 선수 목록 썸네일·프로필에 공통 `PlayerPortrait`를 사용한다. 전체 280명의 매핑은 기존 playerId로 재사용 가능하다. 해외 리그 선택 UI는 추가하지 않았다. 사진 실패·미확보 시 실루엣을 표시하고 선수 또는 경로 변경 시 오류 상태를 초기화한다. 썸네일은 lazy loading이며 프로필에 사진 출처·촬영 연도 또는 시즌·당시 팀·사용 조건을 간결하게 표시한다.

초기 구현 범위는 매핑·공통 사진 컴포넌트, `TeamPlayerInformationPage.tsx`, `components/PlayerProfile.tsx`, 로컬 `team-player.css`, 작은 수집·검증 스크립트 2개와 이 문서다. **이번 후속 변경은 이미지 79개 추가, 매핑, `playerPhotos.ts`, `components/PlayerPortrait.tsx`의 출처명·시즌 표시, 이 문서로 한정했다.**

후속 시작 HEAD는 `1daa0c662c318066d52faf335c52ef284fa3b2f4`. 현재 frontend와 읽기 전용 선수 입력을 `/tmp/lolmanager-portraits-namu`로 복사하여 작업했다. 반영 직전 원본을 비교해 중첩 변경이 없음을 확인하고 본인 파일만 반영했다. 반영 파일은 검증한 작업 공간과 바이트 단위로 일치한다. backend·Career/Series·App·전역 스타일·package/lock·prompts는 수정하지 않았으며 다른 작업의 프로세스도 조작하지 않았다. 커밋·push·배포는 하지 않았다.

## 실제 검증

- `python3 scripts/verify-player-photos.py`: **7/7 통과, 실제 사진 280/280**. 기존 ID 집합·표시 신원·출처·파일 존재·정상 WebP 디코딩·크기·용량·HTML 오응답·파일/픽셀 중복 의심·확보 수를 확인했다. 중복 의심 0건. 자동 파일 검사는 얼굴 신원을 판정하지 않으며 문서·캡션 대조가 신원 근거다.
- `npm run build`: 최종 매핑·크레딧 코드의 TypeScript·Vite 빌드 통과. 파일의 전체 시즌명이 추가 확인된 6건을 반영한 뒤 최종 빌드했다.
- 별도 포트 5198·Playwright 세션 `portraits-namu`: 기존 API 타입의 통제된 LCK 응답으로 실제 선수 정보 컴포넌트에서 **Siwoo 검색→선택, 로컬 사진·나무위키 링크·2026 시즌 표기, lazy 썸네일, 실제 HTTP 404 실패 실루엣, Smash→Siwoo 전환 복구, DK 목록**을 확인했다. 초기 작업의 키보드 탭·미확보 공통 컴포넌트 검증은 유지한다. 테스트용 HTML·응답은 저장소에 반영하지 않았으며 backend 연동 E2E는 아니다.
- `git diff --check`: 통과. 본인 변경 범위만 점검했다. 대상 파일의 동시 변경과 기존 자산 변경은 없었으며, 반영 후 같은 테스트 전체를 반복하지 않았다.
- 팀 데이터 처리·선택 로직을 바꾸지 않아 `team-player:verify`를 실행하지 않았다. frontend 사진 작업이므로 backend Gradle test는 실행하지 않았다.

## 재개 방법

Python·Pillow 환경에서 frontend 기준으로 직접 실행한다. 앱 의존성은 추가하지 않았다.

```sh
python3 scripts/collect-player-photos.py --cache /tmp/player-photo-originals
python3 scripts/verify-player-photos.py
```

수집기는 검토된 `candidate`·`download_failed` 또는 파일이 사라진 `acquired`를 처리한다. 새 사진은 출처와 신원을 먼저 확인해 메타데이터를 갱신한다. 기존 파일과 URL별 원본 캐시를 재사용하며 최대 동시 다운로드는 3개다.

한글 커밋 제안: `feat: 나무위키 등 추가 출처로 선수 사진 280명 등록 완료`
