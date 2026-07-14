import { useEffect, useMemo, useRef, useState } from 'react';
import type { MatchEvent, MatchSimulateResponse, MatchSnapshot, PlayerSnapshot } from './types/match';

const API_URL = 'http://localhost:8080/api/matches/simulate';
const SPEED_OPTIONS = [1, 5, 10, 30] as const;
const POSITION_ORDER: Record<string, number> = {
  TOP: 0,
  JUNGLE: 1,
  MID: 2,
  ADC: 3,
  SUPPORT: 4
};

function formatTime(seconds: number): string {
  const safeSeconds = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(safeSeconds / 60);
  const remainSeconds = safeSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(remainSeconds).padStart(2, '0')}`;
}

function eventTypeClass(type: string): string {
  switch (type) {
    case 'KILL':
    case 'JUNGLE_GANK':
    case 'COUNTER_GANK':
      return 'kill';
    case 'SHUTDOWN':
      return 'shutdown';
    case 'DRAGON':
    case 'BARON':
    case 'TOWER':
      return 'objective';
    case 'ACE':
      return 'ace';
    case 'GAME_END':
      return 'game-end';
    case 'TEAMFIGHT':
    case 'TEAMFIGHT_RESULT':
      return 'teamfight';
    default:
      return 'neutral';
  }
}

function eventMessage(event: MatchEvent): string {
  const counter = event.counterGank;
  if (counter) {
    const attacking = counter.attackingSide === 'BLUE' ? '블루' : '레드';
    const defending = counter.defendingSide === 'BLUE' ? '블루' : '레드';
    const lane = counter.targetLane === 'BOT' ? '바텀' : counter.targetLane;
    if (counter.outcome === 'NO_KILL') {
      return attacking + '의 ' + lane + ' 갱킹에 ' + defending + ' 정글이 대응했지만 킬 없이 끝났습니다.';
    }
    const result = counter.outcome === 'ATTACKING_SIDE_KILL' ? '공격 팀 승리' : '방어 팀 승리';
    return lane + ' 카운터 갱킹 ' + result + ': ' + counter.killerPlayerId + ' → ' + counter.victimPlayerId;
  }
  const gank = event.jungleGank;
  if (!gank) return event.message;
  const side = gank.gankingSide === 'BLUE' ? '블루' : '레드';
  const lane = gank.targetLane === 'BOT' ? '바텀' : gank.targetLane;
  if (gank.outcome === 'NO_KILL') return side + ' 정글의 ' + lane + ' 갱킹이 킬 없이 끝났습니다.';
  if (gank.outcome === 'GANK_SUCCESS') {
    return side + ' 정글의 ' + lane + ' 갱킹 성공: ' + gank.killerPlayerId + ' → ' + gank.victimPlayerId;
  }
  return side + ' 정글의 ' + lane + ' 갱킹에서 역킬: ' + gank.killerPlayerId + ' → ' + gank.victimPlayerId;
}

function sortPlayers(players: PlayerSnapshot[]): PlayerSnapshot[] {
  return [...players].sort((left, right) => {
    const leftOrder = POSITION_ORDER[left.position] ?? 99;
    const rightOrder = POSITION_ORDER[right.position] ?? 99;

    if (leftOrder !== rightOrder) {
      return leftOrder - rightOrder;
    }

    return left.playerName.localeCompare(right.playerName);
  });
}

function App() {
  const [matchResult, setMatchResult] = useState<MatchSimulateResponse | null>(null);
  const [seedInput, setSeedInput] = useState('');
  const [currentSeed, setCurrentSeed] = useState<number | null>(null);
  const [gameTime, setGameTime] = useState(0);
  const [speed, setSpeed] = useState<number>(1);
  const [playing, setPlaying] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const logRef = useRef<HTMLDivElement | null>(null);

  const timeline = matchResult?.timeline ?? null;

  useEffect(() => {
    if (!timeline || !playing) {
      return;
    }

    const intervalId = window.setInterval(() => {
      setGameTime((previousTime) => Math.min(previousTime + speed * 0.25, timeline.durationSeconds));
    }, 250);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [timeline, playing, speed]);

  useEffect(() => {
    if (!timeline) {
      return;
    }

    if (gameTime >= timeline.durationSeconds && playing) {
      setGameTime(timeline.durationSeconds);
      setPlaying(false);
    }
  }, [gameTime, playing, timeline]);

  const currentSnapshot = useMemo<MatchSnapshot | null>(() => {
    if (!timeline || timeline.snapshots.length === 0) {
      return null;
    }

    const visibleSnapshots = timeline.snapshots.filter((snapshot) => snapshot.timeSeconds <= gameTime);
    return visibleSnapshots[visibleSnapshots.length - 1] ?? timeline.snapshots[0];
  }, [gameTime, timeline]);

  const visibleEvents = useMemo<MatchEvent[]>(() => {
    if (!timeline) {
      return [];
    }

    return timeline.events.filter((event) => event.timeSeconds <= gameTime);
  }, [gameTime, timeline]);

  useEffect(() => {
    if (!logRef.current) {
      return;
    }

    logRef.current.scrollTo({
      top: logRef.current.scrollHeight,
      behavior: 'smooth'
    });
  }, [visibleEvents.length]);

  const teamNames = useMemo<[string, string]>(() => {
    if (!currentSnapshot) {
      return ['Blue Team', 'Red Team'];
    }

    const orderedNames: string[] = [];
    for (const player of currentSnapshot.playerSnapshots) {
      if (!orderedNames.includes(player.teamName)) {
        orderedNames.push(player.teamName);
      }
    }

    return [orderedNames[0] ?? 'Blue Team', orderedNames[1] ?? 'Red Team'];
  }, [currentSnapshot]);

  const bluePlayers = useMemo<PlayerSnapshot[]>(() => {
    if (!currentSnapshot) {
      return [];
    }

    return sortPlayers(currentSnapshot.playerSnapshots.filter((player) => player.teamName === teamNames[0]));
  }, [currentSnapshot, teamNames]);

  const redPlayers = useMemo<PlayerSnapshot[]>(() => {
    if (!currentSnapshot) {
      return [];
    }

    return sortPlayers(currentSnapshot.playerSnapshots.filter((player) => player.teamName === teamNames[1]));
  }, [currentSnapshot, teamNames]);

  const hasGameEnded = visibleEvents.some((event) => event.type === 'GAME_END') || (timeline ? gameTime >= timeline.durationSeconds : false);

  const startMatch = async () => {
    const trimmedSeed = seedInput.trim();
    const seed = trimmedSeed === '' ? Date.now() : Number(trimmedSeed);

    if (!Number.isSafeInteger(seed) || seed < 0) {
      setError('Seed는 0 이상의 숫자만 입력해주세요.');
      setPlaying(false);
      return;
    }

    setLoading(true);
    setError(null);
    setMatchResult(null);
    setCurrentSeed(seed);
    setGameTime(0);
    setSpeed(1);
    setPlaying(false);

    try {
      const response = await fetch(API_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ seed })
      });

      if (!response.ok) {
        throw new Error(`API 요청 실패: ${response.status} ${response.statusText}`);
      }

      const result = (await response.json()) as MatchSimulateResponse;
      setMatchResult(result);
      setCurrentSeed(result.seed);
      setGameTime(0);
      setSpeed(1);
      setPlaying(true);
    } catch (caughtError) {
      const message =
        caughtError instanceof Error ? caughtError.message : '알 수 없는 오류가 발생했습니다.';
      setError(message);
      setPlaying(false);
      setCurrentSeed(null);
    } finally {
      setLoading(false);
    }
  };

  const togglePlaying = () => {
    if (!timeline) {
      return;
    }

    if (gameTime >= timeline.durationSeconds) {
      setGameTime(0);
    }

    setPlaying((previous) => !previous);
  };

  const jumpToResult = () => {
    if (!timeline) {
      return;
    }

    setGameTime(timeline.durationSeconds);
    setPlaying(false);
  };

  return (
    <main className="broadcast-shell">
      <section className="control-panel">
        <div className="control-copy">
          <p className="eyebrow">LoL FM Match Console</p>
          <h1>실시간 텍스트 중계 테스트</h1>
          <p className="control-text">백엔드 타임라인을 받아 0:00부터 경기 흐름을 재생합니다.</p>
        </div>

        <div className="control-actions">
          <button className="primary-button" onClick={startMatch} disabled={loading}>
            {loading ? '경기 생성 중...' : '경기 시작'}
          </button>

          <button className="secondary-button" onClick={togglePlaying} disabled={!timeline || loading}>
            {playing ? '일시정지' : '재개'}
          </button>

          <button className="secondary-button result-button" onClick={jumpToResult} disabled={!timeline || loading}>
            즉시 경기 결과
          </button>

          <div className="speed-group" aria-label="재생 속도">
            {SPEED_OPTIONS.map((option) => (
              <button
                key={option}
                className={`speed-button ${speed === option ? 'active' : ''}`}
                onClick={() => setSpeed(option)}
                disabled={!timeline}
              >
                x{option}
              </button>
            ))}
          </div>
        </div>

        <div className="seed-panel">
          <label className="seed-field" htmlFor="seed-input">
            <span>재현용 Seed</span>
            <input
              id="seed-input"
              className="seed-input"
              type="text"
              inputMode="numeric"
              placeholder="비워두면 자동 생성"
              value={seedInput}
              onChange={(event) => setSeedInput(event.target.value.replace(/[^0-9]/g, '').slice(0, 16))}
              disabled={loading}
            />
          </label>
          <p className="seed-hint">입력창을 비워두면 경기 시작마다 새 seed를 사용하고, 숫자를 넣으면 같은 경기를 다시 재현합니다.</p>
        </div>

        <div className="status-strip">
          <div className="status-item">
            <span className="label">현재 시간</span>
            <strong>{formatTime(gameTime)}</strong>
          </div>
          <div className="status-item">
            <span className="label">종료 시간</span>
            <strong>{formatTime(timeline?.durationSeconds ?? 0)}</strong>
          </div>
          <div className="status-item">
            <span className="label">상태</span>
            <strong className={hasGameEnded ? 'accent-green' : 'accent-blue'}>
              {hasGameEnded ? '경기 종료' : playing ? '재생 중' : timeline ? '일시정지' : '대기 중'}
            </strong>
          </div>
          <div className="status-item">
            <span className="label">승자</span>
            <strong>{timeline?.winner ?? '-'}</strong>
          </div>
          <div className="status-item seed-status-item">
            <span className="label">Seed</span>
            <strong>{currentSeed ?? '-'}</strong>
          </div>
        </div>
      </section>

      {error && <div className="feedback-panel error-panel">에러: {error}</div>}
      {loading && <div className="feedback-panel loading-panel">백엔드에서 경기 타임라인을 생성하고 있습니다.</div>}

      {!timeline && !loading && !error && (
        <section className="empty-panel">
          <p>경기 시작 버튼을 누르면 0:00부터 경기 로그와 스코어가 재생됩니다.</p>
        </section>
      )}

      {timeline && currentSnapshot && (
        <div className="broadcast-grid">
          <section className="score-panel">
            <div className="section-header">
              <p className="eyebrow">현재 경기 상태</p>
              <h2>실시간 스코어보드</h2>
            </div>

            <div className="team-overview">
              <article className="team-summary blue-team">
                <span className="team-name">{teamNames[0]}</span>
                <strong>{currentSnapshot.blueKills} Kills</strong>
                <p>{currentSnapshot.blueGold.toLocaleString()} Gold</p>
                <div className="team-observability">
                  <span>DRAGON {currentSnapshot.blueDragons}</span>
                  <span className={currentSnapshot.blueHasDragonSoul ? 'dragon-soul-active' : ''}>
                    {currentSnapshot.blueHasDragonSoul ? 'DRAGON SOUL' : 'SOUL -'}
                  </span>
                  <span>TOWER {currentSnapshot.blueTowersDestroyed}</span>
                  <span>INHIB {currentSnapshot.blueInhibitorsRemaining}</span>
                  <span>NEXUS T {currentSnapshot.blueNexusTurretsRemaining}</span>
                  <span className={currentSnapshot.blueNexusAlive ? '' : 'nexus-destroyed'}>
                    {currentSnapshot.blueNexusAlive ? 'NEXUS OK' : 'NEXUS DESTROYED'}
                  </span>
                  <span className={currentSnapshot.blueHasBaronBuff ? 'baron-active' : ''}>{currentSnapshot.blueHasBaronBuff ? 'BARON BUFF' : 'BARON -'}</span>
                  <span className={currentSnapshot.blueHasElderBuff ? 'elder-active' : ''}>{currentSnapshot.blueHasElderBuff ? `ELDER BUFF ${Math.ceil(currentSnapshot.blueElderBuffRemainingSeconds)}s` : (currentSnapshot.elderAlive ? 'ELDER ALIVE' : 'ELDER -')}</span>
                  <span>ALIVE {currentSnapshot.blueAlivePlayers}/5</span>
                </div>
              </article>
              <article className="team-summary red-team">
                <span className="team-name">{teamNames[1]}</span>
                <strong>{currentSnapshot.redKills} Kills</strong>
                <p>{currentSnapshot.redGold.toLocaleString()} Gold</p>
                <div className="team-observability">
                  <span>DRAGON {currentSnapshot.redDragons}</span>
                  <span className={currentSnapshot.redHasDragonSoul ? 'dragon-soul-active' : ''}>
                    {currentSnapshot.redHasDragonSoul ? 'DRAGON SOUL' : 'SOUL -'}
                  </span>
                  <span>TOWER {currentSnapshot.redTowersDestroyed}</span>
                  <span>INHIB {currentSnapshot.redInhibitorsRemaining}</span>
                  <span>NEXUS T {currentSnapshot.redNexusTurretsRemaining}</span>
                  <span className={currentSnapshot.redNexusAlive ? '' : 'nexus-destroyed'}>
                    {currentSnapshot.redNexusAlive ? 'NEXUS OK' : 'NEXUS DESTROYED'}
                  </span>
                  <span className={currentSnapshot.redHasBaronBuff ? 'baron-active' : ''}>{currentSnapshot.redHasBaronBuff ? 'BARON BUFF' : 'BARON -'}</span>
                  <span className={currentSnapshot.redHasElderBuff ? 'elder-active' : ''}>{currentSnapshot.redHasElderBuff ? `ELDER BUFF ${Math.ceil(currentSnapshot.redElderBuffRemainingSeconds)}s` : (currentSnapshot.elderAlive ? 'ELDER ALIVE' : 'ELDER -')}</span>
                  <span>ALIVE {currentSnapshot.redAlivePlayers}/5</span>
                </div>
              </article>
            </div>

            <div className="roster-grid">
              <div className="roster-block">
                <div className="roster-header">
                  <h3>{teamNames[0]}</h3>
                  <span>블루 진영</span>
                </div>
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Pos</th>
                        <th>Player</th>
                        <th>KDA</th>
                        <th>CS</th>
                        <th>Gold</th>
                      </tr>
                    </thead>
                    <tbody>
                      {bluePlayers.map((player) => (
                        <tr key={`${player.teamName}-${player.playerName}`} className={player.alive ? '' : 'player-dead'}>
                          <td>{player.position}</td>
                          <td>
                            <div className="player-cell">
                              <span>{player.playerName}</span>
                              {!player.alive ? (
                                <span className="respawn-timer">부활 {player.respawnRemainingSeconds}초</span>
                              ) : !player.canFarm && player.position !== 'SUPPORT' ? (
                                <span className="farm-return-timer">복귀 {player.farmReturnSecondsRemaining}초</span>
                              ) : player.hasShutdownBounty ? (
                                <span className="shutdown-bounty">BOUNTY +{player.shutdownBountyGold}</span>
                              ) : null}
                            </div>
                          </td>
                          <td>{player.kills}/{player.deaths}/{player.assists}</td>
                          <td>{player.cs}</td>
                          <td>{player.gold.toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="roster-block">
                <div className="roster-header">
                  <h3>{teamNames[1]}</h3>
                  <span>레드 진영</span>
                </div>
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Pos</th>
                        <th>Player</th>
                        <th>KDA</th>
                        <th>CS</th>
                        <th>Gold</th>
                      </tr>
                    </thead>
                    <tbody>
                      {redPlayers.map((player) => (
                        <tr key={`${player.teamName}-${player.playerName}`} className={player.alive ? '' : 'player-dead'}>
                          <td>{player.position}</td>
                          <td>
                            <div className="player-cell">
                              <span>{player.playerName}</span>
                              {!player.alive ? (
                                <span className="respawn-timer">부활 {player.respawnRemainingSeconds}초</span>
                              ) : !player.canFarm && player.position !== 'SUPPORT' ? (
                                <span className="farm-return-timer">복귀 {player.farmReturnSecondsRemaining}초</span>
                              ) : player.hasShutdownBounty ? (
                                <span className="shutdown-bounty">BOUNTY +{player.shutdownBountyGold}</span>
                              ) : null}
                            </div>
                          </td>
                          <td>{player.kills}/{player.deaths}/{player.assists}</td>
                          <td>{player.cs}</td>
                          <td>{player.gold.toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </section>

          <aside className="log-panel">
            <div className="section-header">
              <p className="eyebrow">이벤트 로그</p>
              <h2>실시간 중계</h2>
            </div>

            <div className="log-frame" ref={logRef}>
              {visibleEvents.length === 0 ? (
                <p className="muted">경기 시작을 기다리는 중입니다.</p>
              ) : (
                <div className="event-list">
                  {visibleEvents.map((event, index) => (
                    <article key={`${event.timeSeconds}-${event.type}-${index}`} className="event-row">
                      <div className="event-meta">
                        <span className="event-time">{formatTime(event.timeSeconds)}</span>
                        <span className={`event-badge ${eventTypeClass(event.type)}`}>{event.type}</span>
                      </div>
                      <p className="event-message">{eventMessage(event)}</p>
                    </article>
                  ))}
                </div>
              )}
            </div>
          </aside>
        </div>
      )}
    </main>
  );
}

export default App;
