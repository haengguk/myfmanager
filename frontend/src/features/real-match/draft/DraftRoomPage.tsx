import { MatchUtilityBar } from '../MatchChrome';
import type { DraftViewModel } from '../realMatch.types';
import { AutomaticDraftBoard } from './AutomaticDraftBoard';
import { DraftHeader } from './DraftHeader';
import { DraftTeamPanel } from './DraftTeamPanel';

export function DraftRoomPage({ viewModel, onBack, onContinue }: {
  viewModel: DraftViewModel;
  onBack: () => void;
  onContinue: () => void;
}) {
  return (
    <div className="rm-draft-app">
      <MatchUtilityBar meta={viewModel.seasonLabel} onBack={onBack} />
      <DraftHeader viewModel={viewModel} />
      <main className="rm-draft-stage">
        <DraftTeamPanel side="BLUE" teamCode={viewModel.teams.BLUE.code} roster={viewModel.rosters.BLUE}
          bans={viewModel.bans.BLUE} currentPosition={null} championsById={viewModel.championsById} />
        <AutomaticDraftBoard viewModel={viewModel} onContinue={onContinue} />
        <DraftTeamPanel side="RED" teamCode={viewModel.teams.RED.code} roster={viewModel.rosters.RED}
          bans={viewModel.bans.RED} currentPosition={null} championsById={viewModel.championsById} />
      </main>
    </div>
  );
}
