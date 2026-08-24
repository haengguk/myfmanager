import type {
  DraftResultViewModel,
  MatchSnapshotViewModel,
  PlaybackEventType,
  PlaybackViewModel,
  PositionComparisonViewModel,
  TeamSide,
} from './realMatch.types';

export function applyDraftResult(viewModel: PlaybackViewModel, result: DraftResultViewModel): PlaybackViewModel {
  const championByPlayerId = new Map(
    [...result.rosters.BLUE, ...result.rosters.RED]
      .filter((slot) => slot.championId !== null)
      .map((slot) => [slot.playerId, slot.championId as string]),
  );
  const championFor = (playerId: string, fallback: string) => championByPlayerId.get(playerId) ?? fallback;

  return {
    ...viewModel,
    snapshots: viewModel.snapshots.map((snapshot) => ({
      ...snapshot,
      teams: {
        BLUE: { ...snapshot.teams.BLUE, champions: snapshot.teams.BLUE.champions.map((player) => ({ ...player, championId: championFor(player.playerId, player.championId) })) },
        RED: { ...snapshot.teams.RED, champions: snapshot.teams.RED.champions.map((player) => ({ ...player, championId: championFor(player.playerId, player.championId) })) },
      },
    })),
    comparisonAtInitialTime: viewModel.comparisonAtInitialTime.map((row) => ({
      ...row,
      blue: { ...row.blue, championId: championFor(row.blue.playerId, row.blue.championId) },
      red: { ...row.red, championId: championFor(row.red.playerId, row.red.championId) },
    })),
  };
}

export const positionLabels = {
  TOP: 'TOP', JUNGLE: 'JUNGLE', MID: 'MID', ADC: 'ADC', SUPPORT: 'SUPPORT',
} as const;

export const eventPresentation: Record<PlaybackEventType, { label: string; tone: string }> = {
  MATCH_START: { label: '경기 시작', tone: 'phase' },
  ROAM: { label: '로밍', tone: 'accent' },
  GANK_ATTEMPT: { label: '갱킹', tone: 'accent' },
  GOLD_LEAD: { label: '골드 격차', tone: 'accent' },
  KILL: { label: '킬', tone: 'kill' },
  ASSIST: { label: '어시스트', tone: 'kill' },
  PHASE_CHANGE: { label: '단계 전환', tone: 'phase' },
  OBJECTIVE: { label: '오브젝트', tone: 'objective' },
  TOWER: { label: '포탑', tone: 'phase' },
  TEAMFIGHT: { label: '한타', tone: 'teamfight' },
  INHIBITOR: { label: '억제기', tone: 'phase' },
  MATCH_END: { label: '종료', tone: 'success' },
};

export function formatMatchTime(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds));
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, '0')}`;
}

export function selectSnapshot(viewModel: PlaybackViewModel, currentSeconds: number): MatchSnapshotViewModel {
  return [...viewModel.snapshots].reverse().find((snapshot) => snapshot.atSeconds <= currentSeconds) ?? viewModel.snapshots[0];
}

export function comparisonAt(viewModel: PlaybackViewModel, currentSeconds: number): readonly PositionComparisonViewModel[] {
  const factor = currentSeconds <= 0 ? 0 : Math.min(1.5, currentSeconds / viewModel.comparisonReferenceSeconds);
  return viewModel.comparisonAtInitialTime.map((row) => ({
    ...row,
    blue: scalePlayer(row.blue, factor),
    red: scalePlayer(row.red, factor),
  }));
}

function scalePlayer(player: PositionComparisonViewModel['blue'], factor: number): PositionComparisonViewModel['blue'] {
  return {
    ...player,
    kills: Math.round(player.kills * factor), deaths: Math.round(player.deaths * factor), assists: Math.round(player.assists * factor),
    cs: Math.round(player.cs * factor), gold: player.gold * factor,
    level: factor === 0 ? 1 : Math.max(1, Math.round(player.level * Math.min(1, factor))),
  };
}

export function sideName(side: TeamSide): string {
  return side === 'BLUE' ? '블루' : '레드';
}
