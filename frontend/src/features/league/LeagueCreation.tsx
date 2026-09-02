import { useEffect, useMemo, useRef, useState } from 'react';
import type { LeagueCreateRequestDto, LeagueSeasonMode } from './api/leagueApi.types';
import { LEAGUE_TEAMS } from './league.adapter';

export interface LeagueCreateSelection { leagueKey: string; seasonKey: string; seasonMode: LeagueSeasonMode; managedTeamCode: string | null; seasonRootSeed: string }
const KEY = /^[0-9A-Za-z][0-9A-Za-z._-]{0,79}$/; const LONG = /^-?(0|[1-9][0-9]*)$/;
function validLong(value: string): boolean { if (!LONG.test(value)) return false; try { const number = BigInt(value); return number >= -9223372036854775808n && number <= 9223372036854775807n; } catch { return false; } }

export function LeagueCreation({ pending, initial, error, onCreate }: { pending: boolean; initial: LeagueCreateSelection | null; error: string | null; onCreate: (selection: LeagueCreateSelection) => void }) {
  const [mode, setMode] = useState<LeagueSeasonMode>(initial?.seasonMode ?? 'HYBRID_MANAGER');
  const [leagueKey, setLeagueKey] = useState(initial?.leagueKey ?? 'lck-2026'); const [seasonKey, setSeasonKey] = useState(initial?.seasonKey ?? 'season-1');
  const [managedTeam, setManagedTeam] = useState(initial?.managedTeamCode ?? 'GEN'); const [seed, setSeed] = useState(initial?.seasonRootSeed ?? '73');
  const headingRef = useRef<HTMLHeadingElement>(null); useEffect(() => headingRef.current?.focus(), []);
  const issue = useMemo(() => !KEY.test(leagueKey) ? '리그 키는 영문·숫자로 시작하고 80자 이하여야 합니다.' : !KEY.test(seasonKey) ? '시즌 키는 영문·숫자로 시작하고 80자 이하여야 합니다.' : !validLong(seed) ? '루트 seed는 signed 64-bit 정수의 표준 문자열이어야 합니다.' : mode === 'HYBRID_MANAGER' && !managedTeam ? 'Hybrid 모드는 관리 팀이 필요합니다.' : null, [leagueKey, managedTeam, mode, seasonKey, seed]);
  const submit = () => { if (!issue && !pending) onCreate({ leagueKey, seasonKey, seasonMode: mode, managedTeamCode: mode === 'HYBRID_MANAGER' ? managedTeam : null, seasonRootSeed: seed }); };
  return <main className="lg-create" aria-labelledby="lg-create-title" aria-busy={pending}>
    <header className="lg-page-head"><div><span>대회 / AI 리그</span><h1 id="lg-create-title" ref={headingRef} tabIndex={-1}>새 AI 리그 시즌</h1></div><p>10팀 더블 라운드 로빈 · 18라운드 · BO3 90경기</p></header>
    <section className="lg-create__stage">
      <div className="lg-create__intro"><span>AI_VS_AI_LEAGUE_V1</span><h2>운영 방식부터 선택하세요</h2><p>Hybrid는 관리 팀의 18경기를 직접 Series로 진행하고 나머지 72경기를 AI가 계산합니다. Spectator는 90경기 모두 자동 진행합니다.</p><dl><div><dt>팀</dt><dd>10</dd></div><div><dt>라운드</dt><dd>18</dd></div><div><dt>경기</dt><dd>90</dd></div></dl></div>
      <form className="lg-create__form" onSubmit={(event) => { event.preventDefault(); submit(); }}>
        <fieldset disabled={pending}><legend>시즌 모드</legend><div className="lg-mode-options">
          <button type="button" aria-pressed={mode === 'HYBRID_MANAGER'} className={mode === 'HYBRID_MANAGER' ? 'is-selected' : ''} onClick={() => setMode('HYBRID_MANAGER')}><span>HYBRID</span><strong>감독 참여</strong><small>내 팀 18 Player · 72 Auto</small></button>
          <button type="button" aria-pressed={mode === 'SPECTATOR_FULL_AUTO'} className={mode === 'SPECTATOR_FULL_AUTO' ? 'is-selected' : ''} onClick={() => setMode('SPECTATOR_FULL_AUTO')}><span>SPECTATOR</span><strong>전체 관전</strong><small>90 Auto · 관리 팀 없음</small></button>
        </div></fieldset>
        <div className="lg-form-grid"><label><span>리그 고정 키</span><input value={leagueKey} onChange={(event) => setLeagueKey(event.target.value)} disabled={pending} autoComplete="off" /></label><label><span>시즌 고정 키</span><input value={seasonKey} onChange={(event) => setSeasonKey(event.target.value)} disabled={pending} autoComplete="off" /></label></div>
        <label><span>관리 팀</span><select value={mode === 'HYBRID_MANAGER' ? managedTeam : ''} onChange={(event) => setManagedTeam(event.target.value)} disabled={pending || mode !== 'HYBRID_MANAGER'}>{mode === 'SPECTATOR_FULL_AUTO' ? <option value="">Spectator 모드는 관리 팀 없음</option> : LEAGUE_TEAMS.map((team) => <option value={team.code} key={team.code}>{team.code} · {team.name}</option>)}</select></label>
        <label><span>시즌 루트 seed</span><input className="is-mono" inputMode="numeric" value={seed} onChange={(event) => setSeed(event.target.value)} disabled={pending} aria-describedby="lg-seed-help" /><small id="lg-seed-help">동일한 seed와 frozen 리소스는 동일한 일정과 경기 결과를 재현합니다.</small></label>
        {issue ? <p className="lg-inline-error" role="alert">{issue}</p> : null}{error ? <p className="lg-inline-error" role="alert">{error}</p> : null}
        <button className="lg-primary" type="submit" disabled={Boolean(issue) || pending}>{pending ? <><span className="lg-spinner" aria-hidden="true" />시즌 생성 확인 중</> : 'AI 리그 시즌 생성'}</button>
      </form>
    </section>
  </main>;
}

export function createLeagueRequest(selection: LeagueCreateSelection, clientCommandId: string): LeagueCreateRequestDto { return { schemaVersion: 'AI_LEAGUE_CREATE_REQUEST_V1', ...selection, clientCommandId }; }
