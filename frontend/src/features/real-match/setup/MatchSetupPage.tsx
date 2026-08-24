import { useEffect, useMemo, useRef, useState } from 'react';
import { RealMatchApiFailure } from '../api/realMatchApi.client';
import type { MatchRequestStage } from '../api/realMatchApi.types';
import { loadMatchSetupOptions } from '../matchDataSource';
import type { MatchSetupOptionsViewModel, MatchSetupSelection, MatchTeamOptionViewModel } from '../matchSession.types';
import type { MatchDataSource, TeamSide } from '../realMatch.contract';
import { validateCanonicalSignedInt64Seed } from '../seedValidation';
import { MatchSetupActions } from './MatchSetupActions';
import { MatchSetupHeader } from './MatchSetupHeader';
import { SeedInput } from './SeedInput';
import { SetupErrorMessage } from './SetupErrorMessage';
import { TeamSelectionPanel } from './TeamSelectionPanel';

const STAGE_LABELS: Readonly<Record<MatchRequestStage, string>> = {
  CONNECTING: '서버 계산 대기', DOWNLOADING: '응답 다운로드', PARSING: 'JSON 해석',
  VALIDATING: '응답 검증', NORMALIZING: '화면 데이터 준비',
};

function emptyOptions(source: MatchDataSource): MatchSetupOptionsViewModel {
  return {
    source, sourceLabel: `${source} 데이터 공급자`, seasonLabel: `REAL_MATCH_API_V1 · ${source}`,
    gameNumber: 1, seriesType: '단판 · Fresh Game 1', draftRule: 'Professional Draft · 자동',
    defaultSeed: '73', defaultBlueTeamCode: '', defaultRedTeamCode: '', engineImplementationVersion: '연결 대기',
    runtimeProfile: '연결 대기', configurationHash: '', teams: [],
  };
}

function errorMessage(error: unknown): string {
  if (error instanceof RealMatchApiFailure) return error.kind === 'CANCELLED'
    ? '경기 요청을 취소했습니다. 팀과 seed 선택은 유지됩니다.'
    : error.userMessage;
  if (error instanceof DOMException && error.name === 'AbortError') return '경기 요청을 취소했습니다.';
  return '경기 데이터를 준비하지 못했습니다. 서버 연결 상태를 확인하세요.';
}

export function MatchSetupPage({ dataSource, onBack, onLegacy, onStart, onCancelStart }: {
  dataSource: MatchDataSource;
  onBack: () => void;
  onLegacy: () => void;
  onStart: (selection: MatchSetupSelection, options: MatchSetupOptionsViewModel, onStage: (stage: MatchRequestStage) => void) => Promise<void>;
  onCancelStart: () => void;
}) {
  const [options, setOptions] = useState<MatchSetupOptionsViewModel | null>(null);
  const [optionsLoading, setOptionsLoading] = useState(true);
  const [optionsError, setOptionsError] = useState<string | null>(null);
  const [optionsAttempt, setOptionsAttempt] = useState(0);
  const [blueTeamId, setBlueTeamId] = useState('');
  const [redTeamId, setRedTeamId] = useState('');
  const [seed, setSeed] = useState('73');
  const [defaultApplied, setDefaultApplied] = useState(true);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [requestStage, setRequestStage] = useState<MatchRequestStage>('CONNECTING');
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const optionsSequenceRef = useRef(0);
  const createAttemptRef = useRef(0);
  const creatingRef = useRef(false);
  const initializedRef = useRef(false);

  useEffect(() => {
    const requestId = ++optionsSequenceRef.current;
    const controller = new AbortController();
    setOptionsLoading(true);
    setOptionsError(null);
    loadMatchSetupOptions(dataSource, controller.signal).then((nextOptions) => {
      if (controller.signal.aborted || requestId !== optionsSequenceRef.current) return;
      setOptions(nextOptions);
      setOptionsLoading(false);
      if (!initializedRef.current) {
        initializedRef.current = true;
        setBlueTeamId(nextOptions.defaultBlueTeamCode);
        setRedTeamId(nextOptions.defaultRedTeamCode);
        setSeed(nextOptions.defaultSeed);
        setDefaultApplied(true);
      }
    }).catch((error: unknown) => {
      if (controller.signal.aborted || requestId !== optionsSequenceRef.current) return;
      setOptions(null);
      setOptionsLoading(false);
      setOptionsError(errorMessage(error));
    });
    return () => controller.abort();
  }, [dataSource, optionsAttempt]);

  useEffect(() => {
    if (!creating) { setElapsedSeconds(0); return; }
    const startedAt = performance.now();
    const timer = window.setInterval(() => setElapsedSeconds(Math.floor((performance.now() - startedAt) / 1000)), 250);
    return () => window.clearInterval(timer);
  }, [creating]);

  useEffect(() => () => {
    createAttemptRef.current += 1;
    creatingRef.current = false;
  }, []);

  const displayOptions = options ?? emptyOptions(dataSource);
  const teamById = useMemo(() => new Map(displayOptions.teams.map((team) => [team.teamId, team])), [displayOptions.teams]);
  const selected = (teamId: string): MatchTeamOptionViewModel | null => teamById.get(teamId) ?? null;
  const conflict = Boolean(blueTeamId && redTeamId && blueTeamId === redTeamId);
  const seedError = validateCanonicalSignedInt64Seed(seed);
  const selection: MatchSetupSelection = { blueTeamId, redTeamId, seed, gameNumber: displayOptions.gameNumber, seriesType: displayOptions.seriesType };
  const optionsReady = Boolean(options && !optionsLoading && !optionsError
    && (options.source === 'LIVE' ? options.teams.length === 10 : options.teams.length >= 2));
  const referenceSelectionReady = !options || options.source !== 'REFERENCE'
    || (blueTeamId === options.defaultBlueTeamCode
      && redTeamId === options.defaultRedTeamCode
      && seed === options.defaultSeed
      && displayOptions.gameNumber === 1);
  const ready = optionsReady && Boolean(blueTeamId && redTeamId) && !conflict && !seedError
    && referenceSelectionReady && !creating;
  const blockReason = optionsLoading
    ? `${dataSource} 데이터 공급자에서 팀과 로스터를 불러오고 있습니다.`
    : optionsError
      ? optionsError
      : !blueTeamId || !redTeamId
        ? 'BLUE와 RED 팀을 모두 선택하세요.'
        : conflict
          ? 'BLUE와 RED에는 서로 다른 팀을 선택해야 합니다.'
          : seedError
            ? seedError
            : !referenceSelectionReady
              ? 'REFERENCE 데이터는 기본 GEN 대 T1 · seed 73 · Fresh Game 1 조합만 실행할 수 있습니다.'
              : '';
  const pairCopy = ready
    ? `${selected(blueTeamId)?.code} · ${selected(redTeamId)?.code} · seed ${seed} · ${displayOptions.source} 자동 Draft를 실행합니다.`
    : creating
      ? `${STAGE_LABELS[requestStage]} 중입니다. 약 33MB 응답은 시간이 걸릴 수 있습니다.`
      : blockReason;

  const changeTeam = (side: TeamSide, teamId: string) => {
    setCreateError(null);
    if (side === 'BLUE') setBlueTeamId(teamId); else setRedTeamId(teamId);
  };
  const applyDefault = () => {
    if (!options) return;
    setSeed(options.defaultSeed); setDefaultApplied(true); setCreateError(null);
  };
  const start = async () => {
    if (!ready || !options || creatingRef.current) return;
    const attempt = ++createAttemptRef.current;
    creatingRef.current = true;
    setCreating(true); setRequestStage('CONNECTING'); setCreateError(null);
    try {
      await onStart(selection, options, (stage) => {
        if (attempt === createAttemptRef.current) setRequestStage(stage);
      });
    } catch (error) {
      if (attempt === createAttemptRef.current) setCreateError(errorMessage(error));
    } finally {
      if (attempt === createAttemptRef.current) {
        creatingRef.current = false;
        setCreating(false);
      }
    }
  };
  const cancel = () => {
    createAttemptRef.current += 1;
    creatingRef.current = false;
    onCancelStart();
    setCreateError('경기 요청을 취소했습니다. 팀과 seed 선택은 유지됩니다.');
    setCreating(false);
  };

  return (
    <div className="rm-setup-app">
      <MatchSetupHeader options={displayOptions} onBack={onBack} onLegacy={onLegacy} />
      <main className="rm-setup-stage">
        <TeamSelectionPanel side="BLUE" teams={displayOptions.teams} selectedTeamId={blueTeamId} selectedTeam={selected(blueTeamId)}
          oppositeTeamId={redTeamId} disabled={creating} optionsLoading={optionsLoading} optionsError={Boolean(optionsError)}
          rosterLoading={false} rosterError={false} conflict={conflict} onChange={(teamId) => changeTeam('BLUE', teamId)} />
        <section className="rm-setup-seed-panel" aria-labelledby="rm-seed-heading">
          <div className="rm-setup-versus"><strong>VS</strong><span>{selected(blueTeamId) && selected(redTeamId) ? `${selected(blueTeamId)?.code} 대 ${selected(redTeamId)?.code}` : '팀을 선택하세요'}</span></div>
          <h2 id="rm-seed-heading" className="lm-sr-only">경기 seed</h2>
          <SeedInput value={seed} disabled={creating || optionsLoading} defaultApplied={defaultApplied && seed === displayOptions.defaultSeed}
            error={seedError} onChange={(value) => { setSeed(value); setDefaultApplied(false); setCreateError(null); }}
            onUseDefault={applyDefault} onClear={() => { setSeed(''); setDefaultApplied(false); setCreateError(null); }} />
          <div className="rm-repro-note">
            <div><span aria-hidden="true">i</span><p><strong>{displayOptions.source} 데이터 공급자</strong>{options ? `${displayOptions.engineImplementationVersion} · ${displayOptions.runtimeProfile}` : 'Options API 계약을 확인하고 있습니다.'}</p></div>
            {optionsError ? <><SetupErrorMessage>{optionsError}</SetupErrorMessage><button className="rm-secondary-action rm-options-retry" type="button" disabled={optionsLoading} onClick={() => setOptionsAttempt((value) => value + 1)}>Options 다시 시도</button></> : null}
            {conflict ? <SetupErrorMessage>BLUE와 RED에는 서로 다른 팀을 선택해야 합니다.</SetupErrorMessage> : null}
            {createError ? <SetupErrorMessage>{createError}</SetupErrorMessage> : null}
          </div>
        </section>
        <TeamSelectionPanel side="RED" teams={displayOptions.teams} selectedTeamId={redTeamId} selectedTeam={selected(redTeamId)}
          oppositeTeamId={blueTeamId} disabled={creating} optionsLoading={optionsLoading} optionsError={Boolean(optionsError)}
          rosterLoading={false} rosterError={false} conflict={conflict} onChange={(teamId) => changeTeam('RED', teamId)} />
      </main>
      <MatchSetupActions ready={ready} creating={creating} elapsedSeconds={elapsedSeconds} stageLabel={STAGE_LABELS[requestStage]}
        statusTitle={creating ? `${displayOptions.source} 경기 생성 중` : ready ? '실제 경기 실행 가능' : optionsError ? '서버 연결 필요' : '경기 조건 확인'}
        statusCopy={pairCopy} onStart={start} onCancel={cancel} />
    </div>
  );
}
