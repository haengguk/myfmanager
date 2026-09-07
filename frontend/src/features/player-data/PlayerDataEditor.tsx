import { useEffect, useRef, useState } from 'react';
import type { RosterPlayer } from '../career/api/careerRoster.contract';
import { realMatchConfig } from '../real-match/realMatch.config';
import { currentAbility, SKILL_LABELS } from './playerAbility';

interface Player { definition: RosterPlayer; currentAbility: number; potentialAbility: number | null; revision: number }
interface View { applicationPolicy: 'NEW_CAREERS_ONLY'; revision: number; players: Player[] }
interface Edit { playerId: string; ratings: Record<string, number>; potentialAbility: number | null; expectedRevision: number; clientCommandId: string }
interface Change { replayed: boolean; receipt: { clientCommandId: string; playerId: string; revision: number; applicationPolicy: string; player: Player } }
const ROOT = `${realMatchConfig.apiBaseUrl}/api/v1/player-data`, KEY = 'lolfm.player-data.pending.v1';
class EditFailure extends Error { constructor(message: string, readonly status: number) { super(message); } }
async function request<T>(signal: AbortSignal, body?: Edit): Promise<T> {
  const r = await fetch(ROOT, { signal: AbortSignal.any([signal, AbortSignal.timeout(30_000)]), method: body ? 'POST' : 'GET', headers: body ? { 'Content-Type': 'application/json' } : undefined, body: body ? JSON.stringify(body) : undefined });
  if (!r.ok) { const value = await r.json().catch(() => ({})) as { message?: string }; throw new EditFailure(value.message ?? '선수 데이터를 불러오거나 저장하지 못했습니다.', r.status); }
  return r.json() as Promise<T>;
}
function validateView(v: View): View {
  if (v.applicationPolicy !== 'NEW_CAREERS_ONLY' || !Number.isSafeInteger(v.revision) || !Array.isArray(v.players) || !v.players.length) throw new Error('선수 데이터 응답을 확인할 수 없습니다.');
  for (const p of v.players) if (currentAbility(p.definition.gameplay.ratings) !== p.currentAbility || (p.potentialAbility !== null && (!Number.isInteger(p.potentialAbility) || p.potentialAbility < 1 || p.potentialAbility > 200))) throw new Error('CA·PA 응답을 확인할 수 없습니다.');
  return v;
}
export function PlayerDataEditor() {
  const [view, setView] = useState<View | null>(null), [selected, setSelected] = useState<string | null>(null);
  const [search, setSearch] = useState(''), [ratings, setRatings] = useState<Record<string, number>>({}), [pa, setPa] = useState('');
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [pending, setPending] = useState(false);
  const [operation, setOperation] = useState<Edit | null>(null), [corrupt, setCorrupt] = useState(false);
  const active = useRef<AbortController | null>(null);
  useEffect(() => {
    const controller = new AbortController(); active.current = controller;
    try { const raw = sessionStorage.getItem(KEY); if (raw) { const saved = JSON.parse(raw) as Edit; currentAbility(saved.ratings); if (!saved.playerId || !saved.clientCommandId || !Number.isSafeInteger(saved.expectedRevision)) throw new Error(); setOperation(saved); } }
    catch { setCorrupt(true); setError('복구할 편집 요청이 손상되었습니다. 새 요청으로 덮어쓰지 않았습니다.'); }
    void request<View>(controller.signal).then(v => { if (!controller.signal.aborted) setView(validateView(v)); }).catch(e => { if (!controller.signal.aborted) setError(String(e)); });
    return () => { active.current?.abort(); active.current = null; };
  }, []);
  const choose = (p: Player) => { setSelected(p.definition.playerId); setRatings({ ...p.definition.gameplay.ratings }); setPa(p.potentialAbility === null ? '' : String(p.potentialAbility)); setNotice(''); };
  const save = async () => {
    if (!view || pending || corrupt || (!selected && !operation)) return;
    const controller = new AbortController(); active.current?.abort(); active.current = controller; setPending(true); setError(''); setNotice('');
    try {
      if (!operation) currentAbility(ratings);
      const potential = pa === '' ? null : Number(pa);
      if (!operation && potential !== null && (!Number.isInteger(potential) || potential < 1 || potential > 200)) throw new Error('PA는 1~200 정수 또는 미입력이어야 합니다.');
      const body: Edit = operation ?? { playerId: selected!, ratings: { ...ratings }, potentialAbility: potential, expectedRevision: view.revision, clientCommandId: crypto.randomUUID() };
      sessionStorage.setItem(KEY, JSON.stringify(body)); setOperation(body);
      const result = await request<Change>(controller.signal, body);
      if (controller.signal.aborted) return;
      if (result.receipt.clientCommandId !== body.clientCommandId || result.receipt.playerId !== body.playerId || result.receipt.applicationPolicy !== 'NEW_CAREERS_ONLY') throw new Error('저장 응답이 원본 요청과 일치하지 않습니다. 같은 요청으로 다시 확인해 주세요.');
      sessionStorage.removeItem(KEY); setOperation(null);
      const refreshed = validateView(await request<View>(controller.signal)); if (controller.signal.aborted) return;
      setView(refreshed); const edited = refreshed.players.find(p => p.definition.playerId === body.playerId); if (edited) choose(edited);
      setNotice(`${result.replayed ? '원래 저장 결과를 복구했습니다.' : '저장했습니다.'} 새 Career부터 적용됩니다. 현재 Career의 능력치는 유지됩니다.`);
    } catch (e) {
      if (!controller.signal.aborted) {
        setError(e instanceof Error ? e.message : '저장 여부를 확인하지 못했습니다.');
        if (e instanceof EditFailure && (e.status === 400 || e.status === 409)) {
          sessionStorage.removeItem(KEY); setOperation(null);
          try { const latest = validateView(await request<View>(controller.signal)); if (!controller.signal.aborted) setView(latest); } catch { /* Keep the edit and the original error. */ }
        }
      }
    } finally { if (active.current === controller) { setPending(false); active.current = null; } }
  };
  const rows = view?.players.filter(p => `${p.definition.nickname} ${p.definition.playerId} ${p.definition.position} ${p.definition.initialOwnerTeam ?? ''}`.toLowerCase().includes(search.toLowerCase())).sort((a, b) => b.currentAbility - a.currentAbility || a.definition.playerId.localeCompare(b.definition.playerId)) ?? [];
  const player = view?.players.find(p => p.definition.playerId === selected);
  let preview: number | null = null; try { preview = currentAbility(ratings); } catch { /* An unfinished input has no preview. */ }
  return <section className="ca-calendar" aria-label="선수 데이터 편집" aria-busy={pending}>
    <h3>새 Career 선수 데이터</h3><p>전체 선수의 CA로 현재 기량을 비교하고 12개 능력치와 PA를 편집합니다. 저장값은 앞으로 만드는 Career에만 적용됩니다.</p>
    <p>PA는 평생 능력 천장의 작성값입니다. 성장·AI 판단에는 아직 사용하지 않습니다. CA보다 낮은 PA도 임의로 올리지 않습니다.</p>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    {operation && <button className="lm-secondary-button" disabled={pending || corrupt} onClick={() => { void save(); }}>원래 편집 저장 결과 다시 확인</button>}
    <label>선수 검색 <input value={search} onChange={e => setSearch(e.target.value)} placeholder="닉네임·선수 ID·포지션·팀" /></label>
    <div style={{ maxHeight: 240, overflow: 'auto' }}><table><thead><tr><th>선수</th><th>포지션</th><th>CA</th><th>PA</th></tr></thead><tbody>{rows.map(p => <tr key={p.definition.playerId}><td><button className="lm-text-button" disabled={pending || !!operation} onClick={() => choose(p)}>{p.definition.nickname}</button></td><td>{p.definition.position}</td><td>{p.currentAbility}</td><td>{p.potentialAbility ?? '미입력'}</td></tr>)}</tbody></table></div>
    {!view && <p>선수 데이터 확인 중…</p>}
    {player && <div><h4>{player.definition.nickname} · {player.definition.playerId}</h4><fieldset disabled={pending || !!operation || corrupt}><legend>능력치 12개 · 정수 1~20</legend><div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 12 }}>{Object.entries(ratings).map(([key, value]) => <label key={key}>{SKILL_LABELS[key] ?? key} <input type="number" min={1} max={20} step={1} value={Number.isNaN(value) ? '' : value} onChange={e => setRatings(r => ({ ...r, [key]: e.target.value === '' ? NaN : Number(e.target.value) }))} /></label>)}</div><label>PA · 정수 1~200 <input type="number" min={1} max={200} step={1} value={pa} onChange={e => setPa(e.target.value)} /></label></fieldset>
      <p>편집 후 CA: <strong>{preview ?? '입력 확인 필요'}</strong>{preview !== null && pa !== '' && Number(pa) < preview ? ' · PA가 현재 CA보다 낮습니다. 작성값을 그대로 저장합니다.' : ''}</p>
      <details><summary>챔피언 숙련도 함께 보기</summary><p>{player.definition.gameplay.proficiencies.map(p => `${p.championId} ${p.value}`).join(' · ')}</p></details>
      <button className="lm-primary-button" disabled={pending || !!operation || corrupt || preview === null} onClick={() => { void save(); }}>새 Career용 데이터 저장</button></div>}
  </section>;
}
