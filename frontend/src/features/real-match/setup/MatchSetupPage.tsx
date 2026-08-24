import { useMemo, useState } from 'react';
import { isReferenceSelection } from '../matchSession.adapter';
import type { MatchSetupOptionsViewModel, MatchSetupSelection, MatchTeamOptionViewModel } from '../matchSession.types';
import type { TeamSide } from '../realMatch.types';
import { validateCanonicalSignedInt64Seed } from '../seedValidation';
import { MatchSetupActions } from './MatchSetupActions';
import { MatchSetupHeader } from './MatchSetupHeader';
import { SeedInput } from './SeedInput';
import { SetupErrorMessage } from './SetupErrorMessage';
import { TeamSelectionPanel } from './TeamSelectionPanel';

export function MatchSetupPage({ options, onBack, onLegacy, onStart }: {
  options: MatchSetupOptionsViewModel;
  onBack: () => void;
  onLegacy: () => void;
  onStart: (selection: MatchSetupSelection) => void | Promise<void>;
}) {
  const [blueTeamId, setBlueTeamId] = useState(options.referenceBlueTeamCode);
  const [redTeamId, setRedTeamId] = useState(options.referenceRedTeamCode);
  const [seed, setSeed] = useState(options.defaultSeed);
  const [referenceApplied, setReferenceApplied] = useState(true);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const teamById = useMemo(() => new Map(options.teams.map((team) => [team.teamId, team])), [options.teams]);
  const selected = (teamId: string): MatchTeamOptionViewModel | null => teamById.get(teamId) ?? null;
  const conflict = Boolean(blueTeamId && redTeamId && blueTeamId === redTeamId);
  const seedError = validateCanonicalSignedInt64Seed(seed);
  const selection: MatchSetupSelection = { blueTeamId, redTeamId, seed, gameNumber: options.gameNumber, seriesType: options.seriesType };
  const referenceReady = !conflict && !seedError && isReferenceSelection(selection);
  const ready = options.teams.length === 10 && referenceReady && !createError;

  const blockReason = !blueTeamId || !redTeamId
    ? 'BLUE와 RED 팀을 모두 선택하세요.'
    : conflict
      ? 'BLUE와 RED에는 서로 다른 팀을 선택해야 합니다.'
      : seedError
        ? seedError
        : !referenceReady
          ? '이 조합은 V1-B 실제 API 연결 후 실행할 수 있습니다. 승인 reference를 적용하세요.'
          : createError ?? '';
  const pairCopy = ready ? 'GEN · T1 · seed 73 · 승인된 자동 Draft 결과로 이동합니다.' : blockReason;

  const changeTeam = (side: TeamSide, teamId: string) => {
    setCreateError(null);
    setReferenceApplied(false);
    if (side === 'BLUE') setBlueTeamId(teamId);
    else setRedTeamId(teamId);
  };
  const applyReference = () => {
    setBlueTeamId(options.referenceBlueTeamCode);
    setRedTeamId(options.referenceRedTeamCode);
    setSeed(options.defaultSeed);
    setReferenceApplied(true);
    setCreateError(null);
  };
  const start = async () => {
    if (!ready || creating) return;
    setCreating(true);
    setCreateError(null);
    try {
      await onStart(selection);
    } catch {
      setCreateError('승인 reference session을 준비하지 못했습니다. fixture 무결성을 확인하세요.');
      setCreating(false);
    }
  };

  return (
    <div className="rm-setup-app">
      <MatchSetupHeader options={options} onBack={onBack} onLegacy={onLegacy} />
      <main className="rm-setup-stage">
        <TeamSelectionPanel side="BLUE" teams={options.teams} selectedTeamId={blueTeamId} selectedTeam={selected(blueTeamId)}
          oppositeTeamId={redTeamId} disabled={creating} optionsLoading={false} optionsError={false}
          rosterLoading={false} rosterError={false} conflict={conflict} onChange={(teamId) => changeTeam('BLUE', teamId)} />
        <section className="rm-setup-seed-panel" aria-labelledby="rm-seed-heading">
          <div className="rm-setup-versus"><strong>VS</strong><span>{selected(blueTeamId) && selected(redTeamId) ? `${selected(blueTeamId)?.code} 대 ${selected(redTeamId)?.code}` : '팀을 선택하세요'}</span></div>
          <h2 id="rm-seed-heading" className="lm-sr-only">경기 seed</h2>
          <SeedInput value={seed} disabled={creating} referenceApplied={referenceApplied && seed === options.defaultSeed}
            error={seedError} onChange={(value) => { setSeed(value); setReferenceApplied(false); setCreateError(null); }}
            onUseReference={applyReference} onClear={() => { setSeed(''); setReferenceApplied(false); setCreateError(null); }} />
          <div className="rm-repro-note">
            <div><span aria-hidden="true">i</span><p><strong>V8 승인 reference</strong>현재 V1-A에서는 GEN 대 T1, seed 73 경기만 실행할 수 있습니다.</p></div>
            {!referenceReady && !conflict && !seedError ? <SetupErrorMessage>다른 팀·seed 조합은 V1-B 실제 API 연결 후 지원합니다.</SetupErrorMessage> : null}
            {conflict ? <SetupErrorMessage>BLUE와 RED에는 서로 다른 팀을 선택해야 합니다.</SetupErrorMessage> : null}
            {createError ? <SetupErrorMessage>{createError}</SetupErrorMessage> : null}
          </div>
        </section>
        <TeamSelectionPanel side="RED" teams={options.teams} selectedTeamId={redTeamId} selectedTeam={selected(redTeamId)}
          oppositeTeamId={blueTeamId} disabled={creating} optionsLoading={false} optionsError={false}
          rosterLoading={false} rosterError={false} conflict={conflict} onChange={(teamId) => changeTeam('RED', teamId)} />
      </main>
      <MatchSetupActions ready={ready} creating={creating} statusTitle={creating ? 'Reference session 준비 중' : ready ? '자동 Draft 결과 확인 가능' : '실행 가능한 reference 확인'} statusCopy={pairCopy} onStart={start} />
    </div>
  );
}
