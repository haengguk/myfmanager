import { useEffect, useMemo, useState } from 'react';
import type { MatchSetupOptionsViewModel, MatchSetupSelection, MatchTeamOptionViewModel } from '../matchSession.types';
import type { TeamSide } from '../realMatch.types';
import { MatchSetupActions } from './MatchSetupActions';
import { MatchSetupHeader } from './MatchSetupHeader';
import { SeedInput } from './SeedInput';
import { SetupErrorMessage } from './SetupErrorMessage';
import { TeamSelectionPanel } from './TeamSelectionPanel';

const seedPattern = /^[A-Za-z0-9_-]{1,48}$/;

export function MatchSetupPage({
  options,
  onBack,
  onStart,
}: {
  options: MatchSetupOptionsViewModel;
  onBack: () => void;
  onStart: (selection: MatchSetupSelection) => void | Promise<void>;
}) {
  const [optionsStatus, setOptionsStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [blueTeamId, setBlueTeamId] = useState('');
  const [redTeamId, setRedTeamId] = useState('');
  const [seed, setSeed] = useState(options.defaultSeed);
  const [seedGenerated, setSeedGenerated] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setOptionsStatus(options.teams.length ? 'ready' : 'error'), 280);
    return () => window.clearTimeout(timer);
  }, [options.teams.length]);

  const teamById = useMemo(() => new Map(options.teams.map((team) => [team.teamId, team])), [options.teams]);
  const selected = (teamId: string): MatchTeamOptionViewModel | null => teamById.get(teamId) ?? null;
  const conflict = Boolean(blueTeamId && redTeamId && blueTeamId === redTeamId);
  const seedError = !seed
    ? 'seed를 입력해야 경기를 생성할 수 있습니다.'
    : seedPattern.test(seed)
      ? null
      : '영문, 숫자, 하이픈, 밑줄만 1–48자로 입력하세요.';
  const ready = optionsStatus === 'ready' && Boolean(blueTeamId && redTeamId) && !conflict && !seedError && !createError;

  const blockReason = optionsStatus === 'loading'
    ? '팀 목록을 불러오는 중입니다.'
    : optionsStatus === 'error'
      ? '팀 options를 불러오지 못했습니다.'
      : !blueTeamId || !redTeamId
        ? 'BLUE와 RED 팀을 모두 선택하세요.'
        : conflict
          ? 'BLUE와 RED에는 서로 다른 팀을 선택해야 합니다.'
          : seedError ?? createError ?? '';
  const pairCopy = ready && selected(blueTeamId) && selected(redTeamId)
    ? `${selected(blueTeamId)?.code} · ${selected(redTeamId)?.code} · seed ${seed} · 생성 후 Draft로 이동합니다.`
    : blockReason;

  const changeTeam = (side: TeamSide, teamId: string) => {
    setCreateError(null);
    if (side === 'BLUE') setBlueTeamId(teamId);
    else setRedTeamId(teamId);
  };

  const generateSeed = () => {
    const randomPart = typeof crypto !== 'undefined' && 'getRandomValues' in crypto
      ? crypto.getRandomValues(new Uint32Array(1))[0].toString(36).toUpperCase()
      : Date.now().toString(36).toUpperCase();
    setSeed(`LM-${randomPart.slice(-8)}`);
    setSeedGenerated(true);
    setCreateError(null);
  };

  const start = async () => {
    if (!ready || creating) return;
    setCreating(true);
    setCreateError(null);
    try {
      await new Promise((resolve) => window.setTimeout(resolve, 320));
      await onStart({ blueTeamId, redTeamId, seed, gameNumber: options.gameNumber, seriesType: options.seriesType });
    } catch {
      setCreateError('경기 생성에 실패했습니다. 선택한 조건을 유지한 채 다시 시도하세요.');
      setCreating(false);
    }
  };

  return (
    <div className="rm-setup-app">
      <MatchSetupHeader options={options} onBack={onBack} />
      <main className="rm-setup-stage">
        <TeamSelectionPanel
          side="BLUE"
          teams={options.teams}
          selectedTeamId={blueTeamId}
          selectedTeam={selected(blueTeamId)}
          oppositeTeamId={redTeamId}
          disabled={creating}
          optionsLoading={optionsStatus === 'loading'}
          optionsError={optionsStatus === 'error'}
          rosterLoading={false}
          rosterError={false}
          conflict={conflict}
          onChange={(teamId) => changeTeam('BLUE', teamId)}
        />
        <section className="rm-setup-seed-panel" aria-labelledby="rm-seed-heading">
          <div className="rm-setup-versus"><strong>VS</strong><span>{selected(blueTeamId) && selected(redTeamId) ? `${selected(blueTeamId)?.code} 대 ${selected(redTeamId)?.code}` : '팀을 선택하세요'}</span></div>
          <h2 id="rm-seed-heading" className="lm-sr-only">경기 seed</h2>
          <SeedInput
            value={seed}
            disabled={creating}
            generated={seedGenerated}
            error={seedError}
            onChange={(value) => { setSeed(value); setSeedGenerated(false); setCreateError(null); }}
            onGenerate={generateSeed}
            onClear={() => { setSeed(''); setSeedGenerated(false); setCreateError(null); }}
          />
          <div className="rm-repro-note">
            <div><span aria-hidden="true">i</span><p><strong>재현 가능한 경기 생성</strong>seed는 숫자 전용 값이 아닌 문자열로 저장됩니다.</p></div>
            {conflict ? <SetupErrorMessage>BLUE와 RED에는 서로 다른 팀을 선택해야 합니다. 한쪽 팀을 변경하세요.</SetupErrorMessage> : null}
            {createError ? <SetupErrorMessage>{createError}</SetupErrorMessage> : null}
          </div>
        </section>
        <TeamSelectionPanel
          side="RED"
          teams={options.teams}
          selectedTeamId={redTeamId}
          selectedTeam={selected(redTeamId)}
          oppositeTeamId={blueTeamId}
          disabled={creating}
          optionsLoading={optionsStatus === 'loading'}
          optionsError={optionsStatus === 'error'}
          rosterLoading={false}
          rosterError={false}
          conflict={conflict}
          onChange={(teamId) => changeTeam('RED', teamId)}
        />
      </main>
      <MatchSetupActions
        ready={ready}
        creating={creating}
        statusTitle={creating ? '경기 생성 중' : ready ? '경기 시작 가능' : '경기 시작 조건 확인'}
        statusCopy={creating ? '선택한 팀과 seed로 Match Session을 준비하고 있습니다.' : pairCopy}
        onStart={start}
      />
    </div>
  );
}
