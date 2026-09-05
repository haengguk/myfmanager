# 선수 사진 수집·화면 연결 결과

기준: 2026-09-05, 시작 HEAD `87df2df344d41029567dd164ad886c5398477280`. 현재 작업 트리의 미커밋 해외 identity·career 입력과 필요한 frontend를 `/tmp/lolmanager-portraits`에 보존하여 작업했다. 기존 모집단은 6개 리그·56팀·280명으로 일치했다.

**실제 사진 201/280명 확보, 79명 미확보.** 기본 실루엣은 확보 수에 포함하지 않았다. 현재 저장소에 사진 자산과 표시 코드만 반영했다.

| 리그 | 대상 | 확보 | 미확보 |
| --- | ---: | ---: | ---: |
| LCK | 50 | 41 | 9 |
| LPL | 60 | 31 | 29 |
| LEC | 50 | 41 | 9 |
| LCS | 40 | 38 | 2 |
| LCP | 40 | 35 | 5 |
| CBLOL | 40 | 15 | 25 |
| 합계 | 280 | 201 | 79 |

## 출처와 사진 처리

팀 공식 선수단·프로필을 먼저 확인하고, 없으면 LoL Esports 공식 Flickr 및 Riot 지역 리그 공식 계정의 명시된 닉네임·팀·캡션을 대조했다. 남은 항목은 Commons 파일 검색으로 확인했으나 HTTP 429 발생 이후 Commons 조회를 중단했다. 일부 팀 페이지의 접근 오류와 후보 부재는 개별 매핑에 구분하여 남겼다. 지정 출처에서 확인하지 못한 사진을 팬 계정이나 생성 이미지로 대체하지 않았다.

출처 기록: [player-photos.json](../../frontend/src/features/team-player/player-photos.json). 280개 기존 playerId마다 원본 이미지 URL, 출처 페이지, 촬영자·권리자, 사용 조건, 촬영 연도·당시 팀, 확보 상태, 신원 대조 근거, 확인한 출처와 결과, 미확보 이유를 기록했다. 알 수 없는 값은 null이다. 공식 공개 사진도 별도 사용 허가를 확인한 것으로 표시하지 않았다.

촬영 연도 확인: 2026년 45장, 2025년 15장, 2024년 28장, 2023년 13장, 2022년 4장, 2021년 5장, 2018년 1장. **이전 시즌 사진은 66장**, 연도 미확인은 90장이다. 페이지 경로의 연도만으로 촬영 연도를 추측하지 않았다. 당시 팀은 개별 매핑·프로필 크레딧에 표시한다. Uniboy는 2018년 MAD 공식 대회 사진이며 Teddy는 2024년 DRX 사진이다.

표시 자산은 [public/images/players](../../frontend/public/images/players)의 WebP 201장, 합계 4,117,006바이트다. Pillow로 방향 보정·비율 유지·긴 변 최대 512px 변환했다. Teddy는 공식 `DRX_TEDDY(3)` 제목의 중앙 주제 인물이 잘 보이도록 크롭했으며 원본 좌표를 `cropBox`에 기록했다. 큰 원본과 조사 캐시는 임시 작업 폴더에만 보관했다.

## 미확보 playerId와 이유

아래는 같은 이유를 묶은 전체 79명이다. 각 선수의 실제 확인 URL·응답·후보 수는 매핑의 `checkedSources`에 있다. “미확인”은 사진이 존재하지 않는다는 단정이 아니다.

| 리그 | playerId | 이유 |
| --- | --- | --- |
| LCK | `player-fenrir`, `player-siwoo`, `player-smash`, `player-career`, `player-dudu`, `player-sharvel`, `player-clozer`, `player-casting`, `player-namgung` | 확인한 팀 페이지·공식 앨범에서 이 선수로 명시된 사용 가능한 단독 사진을 확인하지 못했다. Commons는 HTTP 429 요청 제한으로 추가 확인 불가. |
| LPL | `player-zdz`, `player-bulldog`, `player-fengyue`, `player-cube` | 확인한 팀 페이지·공식 앨범에서 이 선수로 명시된 사용 가능한 단독 사진을 확인하지 못했다. Commons 검색에서도 적합한 선수 사진을 확인하지 못했다. |
| LPL | `player-leave`, `player-parukia`, `player-assum`, `player-heng`, `player-tangyuan`, `player-shaoye`, `player-sheer`, `player-nia`, `player-1xn`, `player-ycx`, `player-hoya`, `player-guwon`, `player-care`, `player-photic`, `player-keshi`, `player-junhao`, `player-heru`, `player-feather`, `player-xiaohao`, `player-monki`, `player-karis`, `player-about`, `player-erha` | 확인한 팀 페이지·공식 앨범에서 이 선수로 명시된 사용 가능한 단독 사진을 확인하지 못했다. Commons는 HTTP 429 요청 제한으로 추가 확인 불가. |
| LPL | `player-zhuo` | 검색의 Zhuo는 knight의 실명 일부로, 등록 선수 Zhuo의 사진이 아니다. Commons는 HTTP 429 요청 제한으로 추가 확인 불가. |
| LPL | `player-ahn` | 검색의 Ahn은 TFT 사진으로 해당 LoL 선수임을 확인할 수 없다. Commons는 HTTP 429 요청 제한으로 추가 확인 불가. |
| LEC | `player-fiesta`, `player-slowq`, `player-rooster`, `player-paduck` | 확인한 팀 페이지·공식 앨범에서 이 선수로 명시된 사용 가능한 단독 사진을 확인하지 못했다. Commons 검색에서도 적합한 선수 사진을 확인하지 못했다. |
| LEC | `player-stend`, `player-tracyn`, `player-daglas`, `player-hype`, `player-way` | 확인한 팀 페이지·공식 앨범에서 이 선수로 명시된 사용 가능한 단독 사진을 확인하지 못했다. Commons는 HTTP 429 요청 제한으로 추가 확인 불가. |
| LCS | `player-kryra` | 확인한 팀 페이지·공식 앨범에서 이 선수로 명시된 사용 가능한 단독 사진을 확인하지 못했다. Commons는 HTTP 429 요청 제한으로 추가 확인 불가. |
| LCS | `player-denathor` | 확인한 팀 페이지·공식 앨범에서 이 선수로 명시된 사용 가능한 단독 사진을 확인하지 못했다. Commons 검색에서도 적합한 선수 사진을 확인하지 못했다. |
| LCP | `player-kratos`, `player-harky`, `player-siuloong`, `player-1jiang` | 확인한 팀 페이지·공식 앨범에서 이 선수로 명시된 사용 가능한 단독 사진을 확인하지 못했다. Commons는 HTTP 429 요청 제한으로 추가 확인 불가. |
| LCP | `player-chika` | 검색의 Chikapi는 TFT 선수이며 Chika의 신원 근거가 아니다. Commons는 HTTP 429 요청 제한으로 추가 확인 불가. |
| CBLOL | `player-zest`, `player-curse`, `player-feisty`, `player-duduhh`, `player-sinatra`, `player-kaze`, `player-rabelo`, `player-uzent`, `player-manel`, `player-peach`, `player-bao`, `player-momochi`, `player-devost`, `player-strensh` | 확인한 팀 페이지·공식 앨범에서 이 선수로 명시된 사용 가능한 단독 사진을 확인하지 못했다. Commons 검색에서도 적합한 선수 사진을 확인하지 못했다. |
| CBLOL | `player-zekas` | 검색의 Zeka(HLE)는 등록 선수 ZekaS와 다른 선수다. Commons는 HTTP 429 요청 제한으로 추가 확인 불가. |
| CBLOL | `player-jeskla`, `player-scamber`, `player-hena`, `player-zynts`, `player-stepz`, `player-fuuu`, `player-booki`, `player-enga`, `player-shiku` | 확인한 팀 페이지·공식 앨범에서 이 선수로 명시된 사용 가능한 단독 사진을 확인하지 못했다. Commons는 HTTP 429 요청 제한으로 추가 확인 불가. |
| CBLOL | `player-ceos` | 검색의 Ceo(Rainbow7)는 등록 선수 Ceos와 다른 닉네임·신원이다. Commons 검색에서도 적합한 선수 사진을 확인하지 못했다. |

## 화면·변경 범위

- LCK 선수 정보 목록의 썸네일과 기존 프로필 사진 자리에 로컬 사진을 연결했다. 선택·검색·탭·키보드 로직은 유지했다. 해외 선수 230명도 같은 playerId 조회로 바로 재사용 가능하며 해외 리그 선택 화면은 추가하지 않았다.
- 공통 `PlayerPortrait`는 대체 텍스트, 썸네일 lazy loading, 비율 유지, 미확보·로딩 실패 실루엣을 지원한다. playerId 또는 이미지 경로 변경 시 오류 상태를 초기화한다. 프로필에는 출처 링크·촬영 정보·확인한 사용 조건을 표시한다.
- 추가: `player-photos.json`, `playerPhotos.ts`, `components/PlayerPortrait.tsx`, WebP 201장, 아래 작은 스크립트 2개, 이 문서 1개.
- 수정: `TeamPlayerInformationPage.tsx`, `components/PlayerProfile.tsx`, `src/styles/team-player.css`.
- backend·runtime JSON·Career/Series·App·전역 스타일·package/lock·prompts·공통 보고서는 수정하지 않았다. 다른 작업의 서버·브라우저는 조작하지 않았다. 커밋·push·배포는 하지 않았다.

## 수행한 검증

- `python3 scripts/verify-player-photos.py`: 7/7 통과. 기존 280 ID 일치, 표시 신원·상태, 출처·미확보 이유, 파일·경로·고아 자산, WebP 디코딩·크기·용량·HTML 오응답, 파일/픽셀 중복 의심, 실제 사진 집계 확인. 동일 파일·픽셀 중복 의심은 0건. 자동 검사는 얼굴 신원 판정이 아니며 페이지·캡션 대조가 신원 근거다.
- `npm run build`: TypeScript 및 Vite 빌드 통과. 최종 이미지·매핑을 대상으로 실행했다.
- 별도 포트 5197·Playwright 세션 `portraits-280`: 실제 `TeamPlayerInformationPage`에 기존 API 타입의 통제된 LCK 응답을 연결해 Faker 목록→프로필→Oner 전환, 사진 디코딩, 썸네일 lazy 속성, End 키 탭 이동을 확인했다. 실제 이미지 HTTP 404를 유도해 실루엣 전환을 확인했고, 공통 컴포넌트에서 Faker 실패→Oner→Faker로 돌아와 두 사진이 복구됨을 확인했다. 미확보 ID 실루엣도 확인했다. 테스트용 HTML·응답·브라우저 파일은 임시 폴더에만 남겼으며 backend 연동 E2E라고 주장하지 않는다.
- `git diff --check`: 통과. 시작본과 반영 직전 원본을 비교한 결과 수정 대상 3개 파일에 동시 변경이 없었다. 새로운 파일의 경로 충돌도 없었다. 검증한 작업 공간과 반영한 파일은 바이트 단위로 일치한다.
- 팀 데이터 처리·선택 로직 변경이 없어 `team-player:verify`는 실행하지 않았다. frontend 사진 변경만 있어 backend Gradle test는 실행하지 않았다. 단순 반영 후 동일 테스트를 반복하지 않았다.

## 재개

Python과 Pillow가 있는 환경에서 frontend 기준 아래 스크립트를 직접 실행한다. 앱 의존성은 추가하지 않았다.

```sh
python3 scripts/collect-player-photos.py --cache /tmp/player-photo-originals
python3 scripts/verify-player-photos.py
```

수집기는 검토된 매핑의 `candidate`·`download_failed` 또는 자산이 사라진 `acquired`만 처리한다. 새 후보는 먼저 출처와 신원을 확인하여 메타데이터를 갱신한다. 이미 확보한 파일과 URL별 원본 캐시를 재사용하며 동시 다운로드는 최대 3개다. `missing`은 자동으로 다른 이미지에 연결하지 않는다.

한글 커밋 제안: `feat: 선수 사진 201명 등록 및 선수 정보 화면 연결`
