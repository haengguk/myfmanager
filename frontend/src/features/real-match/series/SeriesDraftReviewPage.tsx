import { PlayerDraftRoomPage } from '../player-draft/PlayerDraftRoomPage';
import type { PlayerDraftScreenState } from '../player-draft/playerDraft.types';
import { SeriesContextBar } from './SeriesContextBar';
import type { SeriesScreenState } from './series.types';

export function SeriesDraftReviewPage({ state, onBack, onOpenGame }: {
  state: SeriesScreenState; onBack: () => void; onOpenGame: (gameNumber: number) => void;
}) {
  if (!state.reviewDraft) throw new Error('완료된 Series Draft review가 필요합니다.');
  const binding = state.reviewDraft.binding;
  const screen: PlayerDraftScreenState = {
    session: state.reviewDraft.session, options: state.options, championsById: state.championsById,
    selection: {
      blueTeamId: binding.blueTeamCode, redTeamId: binding.redTeamCode, seed: binding.matchSeed,
      gameNumber: binding.gameNumber, seriesType: `${state.series.format} · ${state.series.winsRequired}선승`,
      draftMode: 'PLAYER_CONTROLLED', controlledSide: binding.controlledSide,
    },
  };
  return <PlayerDraftRoomPage state={screen} contextBar={<SeriesContextBar series={state.series} catalog={state.championsById} onOpenGame={onOpenGame} />}
    utilityMeta={`${state.series.format} · Game ${binding.gameNumber} · 완료된 Draft`} backLabel="경기 화면으로 돌아가기"
    canSubmit={false} canSimulate={false} canCancelDraft={false}
    onSessionChange={() => undefined} onSimulationComplete={() => undefined} onCancelled={onBack} onReviewBack={onBack} />;
}
