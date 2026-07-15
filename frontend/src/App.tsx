import { useEffect, useMemo, useRef, useState } from 'react';
import type { MatchEvent, MatchSimulateResponse, MatchSnapshot, MidGameMacroSnapshot, PlayerSnapshot, TeamMacroSnapshot } from './types/match';

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
    case 'MACRO_ACTION':
      return 'macro';
    default:
      return 'neutral';
  }
}

function macroPlanLabel(plan: string | null): string {
  switch (plan) {
    case 'GROUP_MID': return '미드 그룹';
    case 'SIDE_LANE_TOP': return '탑 사이드';
    case 'SIDE_LANE_BOT': return '바텀 사이드';
    case 'OBJECTIVE_SETUP_DRAGON': return '드래곤 셋업';
    case 'OBJECTIVE_SETUP_BARON': return '바론 셋업';
    case 'RESET_AND_FARM': return '리셋 · 파밍';
    default: return '계획 없음';
  }
}

function macroTargetLabel(event: MatchEvent): string {
  const action = event.midGameMacroAction;
  if (!action) return '계획 평가';
  if (action.targetObjective) return action.targetObjective === 'DRAGON' ? '드래곤 지역' : action.targetObjective === 'BARON' ? '바론 지역' : '장로 지역';
  if (action.targetLane) return action.targetLane === 'BOT' ? '바텀 라인' : `${action.targetLane} 라인`;
  return '전장 전반';
}

function macroActionMessage(event: MatchEvent): string {
  const action = event.midGameMacroAction;
  if (!action) return event.message;
  const side = action.teamSide === 'BLUE' ? '블루' : '레드';
  if (action.actionType === 'OBJECTIVE_SETUP') return `${side} ${macroPlanLabel(action.plan)} — ${macroTargetLabel(event)} 통제 시작`;
  if (action.actionType === 'STRUCTURE_PUSH') {
    return `${side} ${macroPlanLabel(action.plan)} — ${macroTargetLabel(event)} ${action.result === 'STRUCTURE_DESTROYED' ? '구조물 파괴' : '압박 실패'}`;
  }
  return `${side} ${macroPlanLabel(action.plan)} — 리셋 및 파밍`;
}

function macroStatusLabel(team: TeamMacroSnapshot): string {
  if (team.status === 'MATCH_ENDED') return '경기 종료';
  if (team.currentPlan) return macroPlanLabel(team.currentPlan);
  switch (team.status) {
    case 'WAITING_FOR_EVALUATION': return '첫 평가 대기';
    case 'EXPIRED': return '계획 만료';
    case 'CANCELLED': return '계획 취소';
    case 'DISABLED': return '기능 비활성';
    default: return '미평가';
  }
}

function macroTeamTarget(team: TeamMacroSnapshot): string {
  if (team.status === 'MATCH_ENDED') return 'SCHEDULE CLOSED';
  if (team.targetObjective) return team.targetObjective === 'DRAGON' ? 'DRAGON SETUP' : 'BARON SETUP';
  if (team.targetLane) return `${team.targetLane} SIDE`;
  if (team.currentPlan === 'RESET_AND_FARM') return 'RESET_AND_FARM';
  if (team.status === 'EXPIRED') return 'PLAN EXPIRED';
  if (team.status === 'WAITING_FOR_EVALUATION') return 'NOT EVALUATED YET';
  return team.status;
}

function macroScheduleDetail(team: TeamMacroSnapshot): string {
  if (team.status === 'MATCH_ENDED') {
    return team.lastEvaluationSkippedReason === 'GAME_FINISHED' ? '종료로 평가 생략' : '스케줄 종료';
  }
  if (team.currentPlan === 'RESET_AND_FARM') return `RESET → ${formatTime(team.activeUntilSeconds)}`;
  if (team.activeUntilSeconds >= 0) return `ACTIVE → ${formatTime(team.activeUntilSeconds)}`;
  if (team.status === 'EXPIRED') return 'EXPIRED';
  return 'WAITING';
}

function nextMacroEvaluationTime(snapshot: MidGameMacroSnapshot): number | null {
  const next = [snapshot.blueTeam.nextEvaluationAtSeconds, snapshot.redTeam.nextEvaluationAtSeconds]
    .filter((time) => time >= 0)
    .sort((left, right) => left - right)[0];
  return next === undefined ? null : next;
}
function eventMessage(event: MatchEvent): string {
  if (event.midGameMacroAction) return macroActionMessage(event);
  const counter = event.counterGank;
  const roam = event.roam;
  if (roam) {
    const side = roam.roamingSide === 'BLUE' ? '블루' : '레드';
    const role = roam.roamerPosition === 'MID' ? '미드' : '서포터';
    const lane = roam.targetLane === 'BOT' ? '바텀' : roam.targetLane;
    if (roam.outcome === 'NO_KILL') return `${side} ${role} ${lane} 로밍 — 교전 없이 종료`;
    return `${side} ${role} ${lane} 로밍 — ${roam.outcome === 'ROAMING_SIDE_KILL' ? '처치 성공' : '역킬 발생'}${roam.killerPlayerId ? `: ${roam.killerPlayerId} → ${roam.victimPlayerId}` : ''}`;
  }

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
  const macroSnapshot = currentSnapshot?.midGameMacro ?? null;
  const latestMacroEvaluation = macroSnapshot?.evaluationHistory.length
    ? macroSnapshot.evaluationHistory[macroSnapshot.evaluationHistory.length - 1]
    : null;
  const latestMacroEvent = useMemo<MatchEvent | null>(() => {
    return [...visibleEvents].reverse().find((event) => event.type === 'MACRO_ACTION' && event.midGameMacroAction) ?? null;
  }, [visibleEvents]);

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
    const blue = currentSnapshot.playerSnapshots.find((player) => player.teamSide === 'BLUE');
    const red = currentSnapshot.playerSnapshots.find((player) => player.teamSide === 'RED');
    return [blue?.teamName ?? 'Blue Team', red?.teamName ?? 'Red Team'];
  }, [currentSnapshot]);

  const bluePlayers = useMemo<PlayerSnapshot[]>(() => {
    if (!currentSnapshot) {
      return [];
    }

    return sortPlayers(currentSnapshot.playerSnapshots.filter((player) => player.teamSide === 'BLUE'));
  }, [currentSnapshot, teamNames]);

  const redPlayers = useMemo<PlayerSnapshot[]>(() => {
    if (!currentSnapshot) {
      return [];
    }

    return sortPlayers(currentSnapshot.playerSnapshots.filter((player) => player.teamSide === 'RED'));
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

            <section className={`lane-phase-overview ${currentSnapshot.lanePhase.enabled ? '' : 'is-disabled'}`} aria-label="라인 단계와 외곽 포탑">
              <div className="lane-phase-heading">
                <div>
                  <span className="eyebrow">Map tempo</span>
                  <h3>{currentSnapshot.lanePhase.matchPhase === 'MID_GAME' ? '미드게임' : '라인전'}</h3>
                </div>
                <span className="phase-transition-copy">
                  {!currentSnapshot.lanePhase.enabled
                    ? '주도권 비활성'
                    : currentSnapshot.lanePhase.matchPhase === 'MID_GAME'
                      ? `${formatTime(currentSnapshot.lanePhase.midGameStartedAtSeconds)} · ${currentSnapshot.lanePhase.transitionReason === 'ALL_LANES_OPEN' ? '전 라인 개방' : '시간 전환'}`
                      : '외곽 포탑 압박 진행 중'}
                </span>
              </div>
              <div className="lane-phase-lines">
                {currentSnapshot.lanePhase.lanes.map((lane) => (
                  <div className="lane-phase-line" key={lane.lane}>
                    <div className="outer-integrity blue-outer">
                      <strong>{lane.blueOuter.alive ? lane.blueOuter.remainingIntegrity.toFixed(0) : '파괴'}</strong>
                      <span>BLUE 외곽</span>
                      <i style={{ width: `${lane.blueOuter.remainingIntegrity}%` }} />
                    </div>
                    <div className="lane-phase-center">
                      <b>{lane.lane}</b>
                      <span className={lane.phase === 'OPEN' ? 'is-open' : ''}>{lane.phase}</span>
                    </div>
                    <div className="outer-integrity red-outer">
                      <strong>{lane.redOuter.alive ? lane.redOuter.remainingIntegrity.toFixed(0) : '파괴'}</strong>
                      <span>RED 외곽</span>
                      <i style={{ width: `${lane.redOuter.remainingIntegrity}%` }} />
                    </div>
                  </div>
                ))}
              </div>
            </section>

            <section className={`objective-priority ${currentSnapshot.objectivePriority.enabled ? '' : 'is-disabled'}`} aria-label="오브젝트 주도권">
              <div className="priority-heading">
                <div>
                  <span className="eyebrow">Objective control</span>
                  <h3>오브젝트 주도권</h3>
                </div>
                {!currentSnapshot.objectivePriority.enabled && <span className="priority-disabled-label">비활성</span>}
              </div>
              <div className="priority-rows">
                {([
                  {
                    label: '드래곤',
                    blue: currentSnapshot.objectivePriority.blueDragonPriority,
                    red: currentSnapshot.objectivePriority.redDragonPriority,
                    lane: currentSnapshot.objectivePriority.dragonLanePressureScore,
                    recent: currentSnapshot.objectivePriority.dragonRecentControl
                  },
                  {
                    label: '바론',
                    blue: currentSnapshot.objectivePriority.blueBaronPriority,
                    red: currentSnapshot.objectivePriority.redBaronPriority,
                    lane: currentSnapshot.objectivePriority.baronLanePressureScore,
                    recent: currentSnapshot.objectivePriority.baronRecentControl
                  }
                ] as const).map((item) => (
                  <div className="priority-row" key={item.label}>
                    <div className="priority-row-copy">
                      <strong>{item.label}</strong>
                      <span>라인 {item.lane >= 0 ? '+' : ''}{item.lane.toFixed(1)} · 최근 전투 {item.recent >= 0 ? '+' : ''}{item.recent.toFixed(1)}</span>
                    </div>
                    <div className="priority-score" aria-label={`${item.label} 블루 ${item.blue.toFixed(0)} 레드 ${item.red.toFixed(0)}`}>
                      <b className="priority-blue">BLUE {item.blue.toFixed(0)}</b>
                      <div className="priority-track" aria-hidden="true">
                        <span style={{ width: `${item.blue}%` }} />
                      </div>
                      <b className="priority-red">{item.red.toFixed(0)} RED</b>
                    </div>
                  </div>
                ))}
              </div>
            </section>

            {macroSnapshot && (
              <section className={`macro-panel ${macroSnapshot.enabled ? '' : 'is-disabled'}`} aria-label="미드게임 팀 매크로">
                <div className="macro-heading">
                  <div>
                    <span className="eyebrow">Team macro plan</span>
                    <h3>미드게임 운영 보드</h3>
                  </div>
                  <span className="macro-phase">{macroSnapshot.enabled ? macroSnapshot.matchPhase : '비활성'} · {formatTime(macroSnapshot.currentTimeSeconds)}</span>
                </div>

                <div className="macro-teams">
                  {[
                    { key: 'BLUE', label: 'BLUE', team: macroSnapshot.blueTeam },
                    { key: 'RED', label: 'RED', team: macroSnapshot.redTeam }
                  ].map((entry) => (
                    <article className={`macro-team macro-${entry.key.toLowerCase()}`} key={entry.key}>
                      <div className="macro-team-topline">
                        <span>{entry.label}</span>
                        <strong>{macroStatusLabel(entry.team)}</strong>
                      </div>
                      <p className="macro-target">{macroTeamTarget(entry.team)}</p>
                      <div className="macro-assignment">
                        <span>ASSIGNMENT</span>
                        <b>{entry.team.assignedPositions.length ? entry.team.assignedPositions.join(' · ') : '—'}</b>
                      </div>

                      <div className="macro-team-meta">
                        <span>{entry.team.lastActionResult}</span>
                        <span>{macroScheduleDetail(entry.team)}</span>
                      </div>
                    </article>
                  ))}
                </div>

                <div className="macro-controls">
                  <div>
                    <span>DRAGON SETUP CONTROL</span>
                    <strong className={macroSnapshot.dragonMacroSetupControl > 0 ? 'blue-control' : macroSnapshot.dragonMacroSetupControl < 0 ? 'red-control' : ''}>
                      {macroSnapshot.dragonMacroSetupControl > 0 ? '+' : ''}{macroSnapshot.dragonMacroSetupControl.toFixed(0)}
                    </strong>
                  </div>
                  <div>
                    <span>BARON SETUP CONTROL</span>
                    <strong className={macroSnapshot.baronMacroSetupControl > 0 ? 'blue-control' : macroSnapshot.baronMacroSetupControl < 0 ? 'red-control' : ''}>
                      {macroSnapshot.baronMacroSetupControl > 0 ? '+' : ''}{macroSnapshot.baronMacroSetupControl.toFixed(0)}
                    </strong>
                  </div>
                  <div>
                    <span>NEXT EVALUATION</span>
                    <strong>{macroSnapshot.matchEnded ? '경기 종료' : nextMacroEvaluationTime(macroSnapshot) === null ? '—' : formatTime(nextMacroEvaluationTime(macroSnapshot)!)}</strong>
                  </div>
                </div>

                {latestMacroEvaluation && (
                  <div className="macro-evaluation">
                    <span>{formatTime(latestMacroEvaluation.dueAtSeconds)} DUE → {formatTime(latestMacroEvaluation.actualEvaluationAtSeconds)} ACTUAL</span>
                    <strong>
                      BLUE {macroPlanLabel(latestMacroEvaluation.blueDecision?.selectedPlan ?? null)} · RED {macroPlanLabel(latestMacroEvaluation.redDecision?.selectedPlan ?? null)}
                    </strong>
                    <small>
                      {latestMacroEvaluation.evaluationSkippedReason
                        ? `평가 생략: ${latestMacroEvaluation.evaluationSkippedReason}`
                        : `선택 Random ${latestMacroEvaluation.selectionRandomConsumptionCount}회`}
                    </small>
                  </div>
                )}

                {latestMacroEvent?.midGameMacroAction && (
                  <div className="macro-callout">
                    <span>{formatTime(latestMacroEvent.timeSeconds)} · LATEST ACTION</span>
                    <strong>{macroActionMessage(latestMacroEvent)}</strong>
                    <small>{latestMacroEvent.midGameMacroAction.participants.join(' · ') || '참여자 정보 없음'} · FARM BLOCK {latestMacroEvent.midGameMacroAction.farmBlockSeconds}s</small>
                  </div>
                )}
              </section>
            )}

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
                              ) : player.activityType === 'ROAMING' ? (
                                <span className="farm-return-timer">로밍 복귀 {player.activitySecondsRemaining}초</span>
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
                              ) : player.activityType === 'ROAMING' ? (
                                <span className="farm-return-timer">로밍 복귀 {player.activitySecondsRemaining}초</span>
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
