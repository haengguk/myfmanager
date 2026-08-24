import { MatchUtilityBar } from '../MatchChrome';
import type { MatchResultViewModel } from '../matchSession.types';
import { FinalTeamScore } from './FinalTeamScore';

export function MatchResultHeader({ result, onBack }: { result: MatchResultViewModel; onBack: () => void }) {
  return (
    <>
      <MatchUtilityBar meta={`${result.seasonLabel} · Game ${result.gameNumber}`} onBack={onBack} />
      <FinalTeamScore result={result} />
    </>
  );
}
