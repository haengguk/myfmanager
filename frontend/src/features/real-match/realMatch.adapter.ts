import type {
  MatchEventType, MatchSnapshotViewModel, PlaybackViewModel, Position, PositionComparisonViewModel, TeamSide,
} from './realMatch.types';

export const positionLabels = { TOP: 'TOP', JUNGLE: 'JUNGLE', MID: 'MID', ADC: 'ADC', SUPPORT: 'SUPPORT' } as const;

export const eventPresentation: Record<MatchEventType, { label: string; tone: string }> = {
  GAME_START: { label: '경기 시작', tone: 'phase' },
  KILL: { label: '킬', tone: 'kill' },
  ASSIST: { label: '어시스트', tone: 'kill' },
  JUNGLE_GANK: { label: '정글 갱킹', tone: 'accent' },
  COUNTER_GANK: { label: '역갱', tone: 'accent' },
  LANE_COMBAT: { label: '라인 교전', tone: 'kill' },
  ROAM: { label: '로밍', tone: 'accent' },
  SHUTDOWN: { label: '제압', tone: 'kill' },
  DRAGON: { label: '드래곤', tone: 'objective' },
  BARON: { label: '바론', tone: 'objective' },
  ELDER: { label: '장로', tone: 'objective' },
  TOWER: { label: '구조물', tone: 'phase' },
  TEAMFIGHT: { label: '한타', tone: 'teamfight' },
  TEAMFIGHT_RESULT: { label: '한타 결과', tone: 'teamfight' },
  ACE: { label: '에이스', tone: 'teamfight' },
  MATCH_PHASE_CHANGE: { label: '단계 전환', tone: 'phase' },
  MACRO_ACTION: { label: '운영', tone: 'accent' },
  LATE_GAME_ACTION: { label: '후반 운영', tone: 'accent' },
  LEVEL_UP: { label: '레벨 업', tone: 'phase' },
  ITEM_STAGE_REACHED: { label: '아이템', tone: 'phase' },
  GAME_END: { label: '경기 종료', tone: 'success' },
};

export function formatMatchTime(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds));
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, '0')}`;
}

export function selectSnapshot(viewModel: PlaybackViewModel, currentSeconds: number): MatchSnapshotViewModel {
  for (let index = viewModel.snapshots.length - 1; index >= 0; index -= 1) {
    if (viewModel.snapshots[index].atSeconds <= currentSeconds) return viewModel.snapshots[index];
  }
  return viewModel.snapshots[0];
}

function playerAt(snapshot: MatchSnapshotViewModel, side: TeamSide, position: Position) {
  const player = snapshot.teams[side].champions.find((candidate) => candidate.position === position);
  if (!player) throw new Error(`${side} ${position} snapshot 선수를 찾을 수 없습니다.`);
  return {
    playerId: player.playerId,
    playerName: player.playerName,
    championId: player.championId,
    position: player.position,
    kills: player.kills,
    deaths: player.deaths,
    assists: player.assists,
    cs: player.cs,
    gold: player.gold,
    totalExperience: player.totalExperience,
    level: player.level,
    shutdownBountyGold: player.shutdownBountyGold,
  };
}

export function comparisonAt(viewModel: PlaybackViewModel, currentSeconds: number): readonly PositionComparisonViewModel[] {
  const snapshot = selectSnapshot(viewModel, currentSeconds);
  return (['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'] as const).map((position) => ({
    position,
    blue: playerAt(snapshot, 'BLUE', position),
    red: playerAt(snapshot, 'RED', position),
  }));
}

export function sideName(side: TeamSide): string { return side === 'BLUE' ? '블루' : '레드'; }
