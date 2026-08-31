import { useEffect, useMemo, useRef, useState } from 'react';
import { MatchUtilityBar } from '../MatchChrome';
import { loadMatchSetupOptions } from '../matchDataSource';
import type { MatchSetupOptionsViewModel } from '../matchSession.types';
import { validateCanonicalSignedInt64Seed } from '../seedValidation';
import { SeedInput } from '../setup/SeedInput';
import { SetupErrorMessage } from '../setup/SetupErrorMessage';
import { TeamSelectionPanel } from '../setup/TeamSelectionPanel';
import type { TeamSide } from '../realMatch.contract';
import { createSeries, SeriesApiFailure } from './api/seriesApi.client';
import type { SeriesFormat, SeriesViewDto } from './api/seriesApi.types';
import { createSeriesRequest } from './series.adapter';
import type { SeriesSetupSelection } from './series.types';

function setupError(error: unknown): string {
  if (error instanceof SeriesApiFailure) return error.userMessage;
  if (error instanceof DOMException && error.name === 'AbortError') return '시리즈 생성 응답 수신을 중단했습니다.';
  return 'LIVE 팀과 로스터를 준비하지 못했습니다. 서버 연결 상태를 확인하세요.';
}

export function SeriesSetupPage({ onBack, onCreated }: {
  onBack: () => void;
  onCreated: (series: SeriesViewDto, options: MatchSetupOptionsViewModel) => void;
}) {
  const [options, setOptions] = useState<MatchSetupOptionsViewModel | null>(null);
  const [loading, setLoading] = useState(true); const [optionsError, setOptionsError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0); const [format, setFormat] = useState<SeriesFormat>('BO3');
  const [managedTeamCode, setManagedTeamCode] = useState(''); const [opponentTeamCode, setOpponentTeamCode] = useState('');
  const [managedSide, setManagedSide] = useState<TeamSide>('BLUE'); const [seed, setSeed] = useState('73');
  const [creating, setCreating] = useState(false); const [createError, setCreateError] = useState<string | null>(null);
  const controllerRef = useRef<AbortController | null>(null); const creatingRef = useRef(false);
  const logicalCreateRef = useRef<{ key: string; id: string } | null>(null);

  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setOptionsError(null);
    loadMatchSetupOptions('LIVE', controller.signal).then((value) => {
      if (controller.signal.aborted) return;
      setOptions(value); setLoading(false);
      setManagedTeamCode((current) => current || value.teams[0]?.code || '');
      setOpponentTeamCode((current) => current || value.teams.find((team) => team.code !== value.teams[0]?.code)?.code || '');
      setSeed((current) => current || value.defaultSeed);
    }).catch((error: unknown) => {
      if (controller.signal.aborted) return; setLoading(false); setOptionsError(setupError(error));
    });
    return () => controller.abort();
  }, [attempt]);

  useEffect(() => () => controllerRef.current?.abort(), []);

  const teamByCode = useMemo(() => new Map(options?.teams.map((team) => [team.code, team]) ?? []), [options]);
  const conflict = Boolean(managedTeamCode && opponentTeamCode && managedTeamCode === opponentTeamCode);
  const seedError = validateCanonicalSignedInt64Seed(seed);
  const ready = Boolean(options && options.teams.length >= 2 && managedTeamCode && opponentTeamCode && !conflict && !seedError && !creating);
  const selection: SeriesSetupSelection = { format, managedTeamCode, opponentTeamCode, game1ManagedSide: managedSide, rootSeed: seed };
  const selectionKey = JSON.stringify(selection);
  const managedTeam = teamByCode.get(managedTeamCode) ?? null; const opponentTeam = teamByCode.get(opponentTeamCode) ?? null;
  const opponentSide: TeamSide = managedSide === 'BLUE' ? 'RED' : 'BLUE';

  const change = (setter: (value: string) => void, value: string) => {
    setter(value); setCreateError(null); logicalCreateRef.current = null;
  };
  const create = async () => {
    if (!ready || !options || creatingRef.current) return;
    const logical = logicalCreateRef.current?.key === selectionKey
      ? logicalCreateRef.current : { key: selectionKey, id: crypto.randomUUID() };
    logicalCreateRef.current = logical; creatingRef.current = true; setCreating(true); setCreateError(null);
    const controller = new AbortController(); controllerRef.current = controller;
    try {
      const series = await createSeries(createSeriesRequest(selection, logical.id), controller.signal);
      logicalCreateRef.current = null; onCreated(series, options);
    } catch (error) {
      const failure = error instanceof SeriesApiFailure ? error : null;
      setCreateError(`${setupError(error)}${failure && ['NETWORK', 'TIMEOUT', 'CANCELLED'].includes(failure.kind) ? ' 같은 생성 작업 ID를 유지했습니다. 다시 시도하면 중복 시리즈를 만들지 않고 서버 결과를 확인합니다.' : ''}`);
      if (!failure || !['NETWORK', 'TIMEOUT', 'CANCELLED'].includes(failure.kind)) logicalCreateRef.current = null;
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null;
      creatingRef.current = false; setCreating(false);
    }
  };

  return (
    <div className="rm-setup-app sr-setup-app" aria-busy={creating}>
      <MatchUtilityBar meta="SERIES_FRONTEND_V1 · LIVE" onBack={onBack} backLabel="경기 준비로 돌아가기" />
      <section className="sr-setup-heading" aria-labelledby="series-setup-heading">
        <div><span>경기 센터 / 시리즈 준비</span><h1 id="series-setup-heading">BO3 / BO5 시리즈</h1></div>
        <p>내 팀을 선택하고 Game 1 진영을 정하면 이후 진영·seed·점수·피어리스 기록은 서버가 관리합니다.</p>
      </section>
      <main className="rm-setup-stage sr-setup-stage">
        <TeamSelectionPanel side={managedSide} teams={options?.teams ?? []} selectedTeamId={managedTeamCode} selectedTeam={managedTeam}
          oppositeTeamId={opponentTeamCode} disabled={creating} optionsLoading={loading} optionsError={Boolean(optionsError)} rosterLoading={false} rosterError={false} conflict={conflict}
          onChange={(teamCode) => change(setManagedTeamCode, teamCode)} />
        <section className="sr-setup-controls" aria-label="시리즈 조건">
          <fieldset disabled={creating || loading}><legend>시리즈 형식</legend><div className="sr-segmented">
            {(['BO3', 'BO5'] as const).map((value) => <button key={value} type="button" aria-pressed={format === value} className={format === value ? 'is-selected' : ''} onClick={() => { setFormat(value); logicalCreateRef.current = null; }}><strong>{value}</strong><span>{value === 'BO3' ? '2선승' : '3선승'}</span></button>)}
          </div></fieldset>
          <fieldset disabled={creating || loading}><legend>Game 1 내 팀 진영</legend><div className="sr-segmented">
            {(['BLUE', 'RED'] as const).map((side) => <button key={side} type="button" aria-pressed={managedSide === side} className={`${managedSide === side ? 'is-selected ' : ''}is-${side.toLowerCase()}`} onClick={() => { setManagedSide(side); logicalCreateRef.current = null; }}><strong>{side}</strong><span>{managedTeamCode || '내 팀'}</span></button>)}
          </div><p>Game 2부터 진영은 서버가 자동 교대합니다.</p></fieldset>
          <SeedInput value={seed} disabled={creating || loading} defaultApplied={seed === '73'} error={seedError}
            onChange={(value) => change(setSeed, value)} onUseDefault={() => change(setSeed, '73')} onClear={() => change(setSeed, '')} />
          <div className="sr-setup-summary" aria-live="polite">
            <span>{format}</span><strong>{managedTeamCode || '내 팀'} vs {opponentTeamCode || '상대 팀'}</strong>
            <p>Game 1 · {managedTeamCode || '내 팀'} {managedSide} · root seed {seed || '미입력'}</p>
          </div>
          {optionsError ? <><SetupErrorMessage>{optionsError}</SetupErrorMessage><button type="button" className="rm-secondary-action" onClick={() => setAttempt((value) => value + 1)}>LIVE Options 다시 시도</button></> : null}
          {conflict ? <SetupErrorMessage>내 팀과 상대 팀은 서로 달라야 합니다.</SetupErrorMessage> : null}
          {createError ? <SetupErrorMessage>{createError}</SetupErrorMessage> : null}
          <button className="rm-primary-action sr-create-action" type="button" disabled={!ready} onClick={create}>
            {creating ? <><span className="rm-spinner" aria-hidden="true" />시리즈 생성 중</> : `${format} 시리즈 생성`}
          </button>
          <small>V1은 process-local입니다. 백엔드가 재시작되면 진행 중 시리즈가 사라질 수 있습니다.</small>
        </section>
        <TeamSelectionPanel side={opponentSide} teams={options?.teams ?? []} selectedTeamId={opponentTeamCode} selectedTeam={opponentTeam}
          oppositeTeamId={managedTeamCode} disabled={creating} optionsLoading={loading} optionsError={Boolean(optionsError)} rosterLoading={false} rosterError={false} conflict={conflict}
          onChange={(teamCode) => change(setOpponentTeamCode, teamCode)} />
      </main>
    </div>
  );
}
