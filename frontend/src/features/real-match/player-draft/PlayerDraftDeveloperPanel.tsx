import type { PlayerDraftSessionResponseDto } from './api/playerDraftApi.types';
import { PlayerDraftHistory } from './PlayerDraftHistory';
import type { PlayerDraftChampionCatalogEntry } from './playerDraft.types';

function HashValue({ value }: { value: string | null }) {
  return <dd title={value ?? undefined}>{value ? `${value.slice(0, 18)}…` : '없음'}</dd>;
}

export function PlayerDraftDeveloperPanel({ session, catalog, revealFrom }: {
  session: PlayerDraftSessionResponseDto;
  catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
  revealFrom: number;
}) {
  return (
    <main className="pd-developer-view" aria-labelledby="pd-developer-heading">
      <header>
        <div><span>DEVELOPMENT VERIFICATION</span><h1 id="pd-developer-heading">직접 밴픽 확인 정보</h1></div>
        <span className="rm-state-badge is-current">프로덕션 화면과 분리됨</span>
      </header>
      <dl className="pd-developer-facts">
        <div><dt>세션 상태</dt><dd>{session.status}</dd></div>
        <div><dt>Revision</dt><dd>{session.revision}</dd></div>
        <div><dt>결정 수</dt><dd>{session.decisions.length} / 20</dd></div>
        <div><dt>제어 진영</dt><dd>{session.controlledSide} · PLAYER</dd></div>
        <div><dt>State hash</dt><HashValue value={session.stateHash} /></div>
        <div><dt>Selectable identity</dt><HashValue value={session.selectableSetIdentity} /></div>
      </dl>
      <PlayerDraftHistory decisions={session.decisions} catalog={catalog} revealFrom={revealFrom} />
    </main>
  );
}
