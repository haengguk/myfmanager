import type { MatchTeamOptionViewModel } from '../matchSession.types';
import type { TeamSide } from '../realMatch.types';

interface TeamSelectorProps {
  side: TeamSide;
  teams: readonly MatchTeamOptionViewModel[];
  selectedTeamId: string;
  oppositeTeamId: string;
  disabled: boolean;
  onChange: (teamId: string) => void;
}

export function TeamSelector({ side, teams, selectedTeamId, oppositeTeamId, disabled, onChange }: TeamSelectorProps) {
  return (
    <label className="rm-team-selector">
      <span className="lm-sr-only">{side} 팀 선택</span>
      <select value={selectedTeamId} disabled={disabled} onChange={(event) => onChange(event.target.value)}>
        <option value="">팀을 선택하세요</option>
        {teams.map((team) => (
          <option key={team.teamId} value={team.teamId} disabled={team.teamId === oppositeTeamId}>
            {team.code} · {team.name}
          </option>
        ))}
      </select>
    </label>
  );
}

export function TeamRosterPreview({ team, loading, error }: { team: MatchTeamOptionViewModel | null; loading: boolean; error: boolean }) {
  if (loading) {
    return (
      <div className="rm-setup-panel-state" aria-live="polite">
        <span className="rm-spinner" aria-hidden="true" /><strong>로스터 불러오는 중</strong><p>선발 선수 5명을 확인하고 있습니다.</p>
      </div>
    );
  }
  if (error) {
    return <div className="rm-setup-panel-state"><strong>로스터를 불러오지 못했습니다</strong><p>팀 선택은 유지됩니다. 로스터 연결 상태를 확인하세요.</p></div>;
  }
  if (!team) {
    return <div className="rm-setup-panel-state"><strong>선택된 팀이 없습니다</strong><p>위 목록에서 이 진영에 배정할 팀을 선택하세요.</p></div>;
  }
  return (
    <div className="rm-team-preview">
      <header>
        <div><h2>{team.code}</h2><p>{team.league} · {team.name}</p></div>
        <div><strong>{team.record}</strong><span>현재 시즌</span></div>
      </header>
      <div className="rm-setup-roster">
        {team.roster.map((player) => (
          <div className="rm-setup-roster-row" key={player.playerId}>
            <span>{player.position}</span>
            <strong title={player.playerName}>{player.playerName}</strong>
            <i aria-hidden="true">{player.playerName.slice(0, 1).toUpperCase()}</i>
          </div>
        ))}
      </div>
    </div>
  );
}

interface TeamSelectionPanelProps extends TeamSelectorProps {
  selectedTeam: MatchTeamOptionViewModel | null;
  optionsLoading: boolean;
  optionsError: boolean;
  rosterLoading: boolean;
  rosterError: boolean;
  conflict: boolean;
}

export function TeamSelectionPanel(props: TeamSelectionPanelProps) {
  const { side, selectedTeam, teams, optionsLoading, optionsError, rosterLoading, rosterError, conflict } = props;
  return (
    <section className={`rm-setup-team rm-side-${side.toLowerCase()}${conflict ? ' is-conflict' : ''}`} aria-labelledby={`rm-setup-${side.toLowerCase()}-heading`}>
      <div className="rm-team-picker">
        <div className="rm-setup-emblem" role="img" aria-label={`${selectedTeam?.code ?? side} 팀 약칭 로고 자리`}>{selectedTeam?.code ?? '—'}</div>
        <div className="rm-team-picker-copy">
          <div><strong id={`rm-setup-${side.toLowerCase()}-heading`}>{side} 진영</strong><span>{teams.length}개 팀</span></div>
          <TeamSelector {...props} disabled={props.disabled || optionsLoading || optionsError} />
        </div>
      </div>
      {optionsLoading ? (
        <div className="rm-setup-panel-state" aria-live="polite"><span className="rm-spinner" aria-hidden="true" /><strong>팀 목록 불러오는 중</strong><p>선택 가능한 {teams.length}개 팀을 확인하고 있습니다.</p></div>
      ) : optionsError ? (
        <div className="rm-setup-panel-state"><strong>팀 목록을 불러오지 못했습니다</strong><p>options API 연결 전 fixture 상태를 확인하세요.</p></div>
      ) : (
        <TeamRosterPreview team={selectedTeam} loading={rosterLoading} error={rosterError} />
      )}
    </section>
  );
}
