import { CareerContractError } from './careerApi.validation.ts';
export const ROSTER_ROLES = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'] as const;
export type RosterRole = typeof ROSTER_ROLES[number];
export interface RosterPlayer {
  playerId: string; nickname: string; position: RosterRole; provisional: boolean;
  initialOrganizationId: string | null; initialOwnerTeam: string | null; initialSquad: string; eligibilityReason: string | null; detailsJson: string;
  gameplay: { playerId: string; nickname: string; position: RosterRole; ratings: Record<string, number>; proficiencies: { championId: string; position: RosterRole; value: number }[] };
}
export interface RosterMember { playerId: string; ownerTeam: string | null; organizationId: string | null; squad: string; eligibilityReason: string | null }
export interface RosterOrganization { organizationId: string; displayName: string; competitiveTeam: string | null; kind: string }
export interface CareerRoster {
  schemaVersion: 'CAREER_ROSTER_VIEW_V1' | 'CAREER_ROSTER_VIEW_V2'; careerId: string; seasonYear: number; revision: number; readOnly: boolean; managedTeam: string;
  directory: { players: Record<string, RosterPlayer>; organizations: Record<string, RosterOrganization> };
  state: { policyVersion: 'CAREER_ROSTER_LINEUP_V1' | 'CAREER_OPERATING_ROSTER_V2'; members: Record<string, RosterMember>; lineups: Record<string, string[]> };
  registeredPlayers: Record<string, string[]>; activeSeriesPlayers: Record<string, string[]>; allowedCommands: string[]; applicationPolicy: string;
}
export interface RosterCommand { schemaVersion: 'CAREER_ROSTER_COMMAND_V1'; sourceYear: number; team: string; playerId: string; action: 'SELECT_STARTER' | 'MOVE_SQUAD'; targetOrganizationId: string | null; replacementPlayerId: string | null; expectedRevision: number; clientCommandId: string }
export interface RosterChange { replayed: boolean; receipt: { clientCommandId: string; careerId: string; sourceYear: number; resultingRevision: number; stateHash: string; applicationPolicy: string }; roster: CareerRoster }
type Obj = Record<string, unknown>;
function check(ok: unknown, path: string): asserts ok { if (!ok) throw new CareerContractError(`roster.${path}`); }
function obj(v: unknown, path: string): Obj { check(v !== null && typeof v === 'object' && !Array.isArray(v), path); return v as Obj; }
function exact(v: unknown, keys: string, path: string): Obj { const o = obj(v, path); check(Object.keys(o).sort().join() === keys.split(' ').sort().join(), path); return o; }
function text(v: unknown): v is string { return typeof v === 'string' && v.length > 0; }
function nullable(v: unknown): boolean { return v === null || text(v); }
function integer(v: unknown): v is number { return Number.isSafeInteger(v) && (v as number) >= 0; }
function strings(v: unknown, path: string): string[] { check(Array.isArray(v) && v.every(text) && new Set(v).size === v.length, path); return v; }
function role(v: unknown): boolean { return (ROSTER_ROLES as readonly unknown[]).includes(v); }
export function validateRosterCommand(value: unknown): RosterCommand {
  const v = exact(value, 'schemaVersion sourceYear team playerId action targetOrganizationId replacementPlayerId expectedRevision clientCommandId', 'command');
  check(v.schemaVersion === 'CAREER_ROSTER_COMMAND_V1' && integer(v.sourceYear) && v.sourceYear >= 2026 && integer(v.expectedRevision), 'command.version');
  check(text(v.team) && text(v.playerId) && (v.action === 'SELECT_STARTER' || v.action === 'MOVE_SQUAD') && nullable(v.targetOrganizationId) && nullable(v.replacementPlayerId), 'command.input');
  check(typeof v.clientCommandId === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v.clientCommandId), 'command.uuid');
  return v as unknown as RosterCommand;
}
export function validateCareerRoster(value: unknown): CareerRoster {
  const v = exact(value, 'schemaVersion careerId seasonYear revision readOnly managedTeam directory state registeredPlayers activeSeriesPlayers allowedCommands applicationPolicy', 'view');
  check(['CAREER_ROSTER_VIEW_V1', 'CAREER_ROSTER_VIEW_V2'].includes(v.schemaVersion as string) && typeof v.careerId === 'string' && /^career_[0-9a-f]{64}$/.test(v.careerId) && integer(v.seasonYear) && integer(v.revision) && typeof v.readOnly === 'boolean' && text(v.managedTeam), 'scope');
  check(v.applicationPolicy === 'UNSTARTED_SERIES_WITHIN_REGISTERED_POOL_ELSE_NEXT_REGISTRATION', 'policy');
  const d = exact(v.directory, 'players organizations', 'directory'), players = obj(d.players, 'players'), orgs = obj(d.organizations, 'organizations');
  for (const [id, raw] of Object.entries(orgs)) { const o = exact(raw, 'organizationId displayName competitiveTeam kind', 'organization'); check(o.organizationId === id && text(o.displayName) && nullable(o.competitiveTeam) && text(o.kind), 'organization.identity'); }
  for (const [id, raw] of Object.entries(players)) {
    const p = exact(raw, 'playerId nickname position gameplay provisional initialOrganizationId initialOwnerTeam initialSquad eligibilityReason detailsJson', 'player');
    check(p.playerId === id && text(p.nickname) && role(p.position) && typeof p.provisional === 'boolean' && nullable(p.initialOrganizationId) && nullable(p.initialOwnerTeam) && text(p.initialSquad) && nullable(p.eligibilityReason), 'player.identity');
    check(typeof p.detailsJson === 'string', 'player.details'); try { obj(JSON.parse(p.detailsJson), 'player.details'); } catch { throw new CareerContractError('roster.player.details'); }
    const g = exact(p.gameplay, 'playerId nickname position ratings proficiencies', 'gameplay'); check(g.playerId === id && g.nickname === p.nickname && g.position === p.position, 'gameplay.identity');
    const ratings = obj(g.ratings, 'ratings'); check(Object.keys(ratings).length === 12 && Object.values(ratings).every(n => integer(n) && n >= 1 && n <= 20), 'ratings.range');
    check(Array.isArray(g.proficiencies) && g.proficiencies.length > 0, 'proficiencies'); const seen = new Set();
    for (const rawProf of g.proficiencies) { const pr = exact(rawProf, 'championId position value', 'proficiency'); check(text(pr.championId) && !seen.has(pr.championId) && pr.position === p.position && integer(pr.value) && pr.value >= 1 && pr.value <= 20, 'proficiency.identity'); seen.add(pr.championId); }
  }
  const s = exact(v.state, 'policyVersion members lineups', 'state'), members = obj(s.members, 'members'), lineups = obj(s.lineups, 'lineups');
  check(s.policyVersion === (v.schemaVersion === 'CAREER_ROSTER_VIEW_V1' ? 'CAREER_ROSTER_LINEUP_V1' : 'CAREER_OPERATING_ROSTER_V2') && Object.keys(players).sort().join() === Object.keys(members).sort().join(), 'members.population');
  for (const [id, raw] of Object.entries(members)) { const m = exact(raw, 'playerId ownerTeam organizationId squad eligibilityReason', 'member'); check(m.playerId === id && nullable(m.ownerTeam) && nullable(m.organizationId) && text(m.squad) && nullable(m.eligibilityReason), 'member.identity'); if (m.ownerTeam !== null) check(text(m.organizationId) && m.ownerTeam === obj(orgs[m.organizationId], 'member.organization').competitiveTeam && text(m.ownerTeam) && m.ownerTeam in lineups, 'member.owner'); }
  const selected = new Set<string>();
  for (const [team, raw] of Object.entries(lineups)) { const ids = strings(raw, 'lineup'); check(v.schemaVersion === 'CAREER_ROSTER_VIEW_V1' ? ids.length === 5 : ids.length <= 5, 'lineup.count'); const roles = new Set(); for (const id of ids) { const m = obj(members[id], 'lineup.member'), p = obj(players[id], 'lineup.player'); check(m.ownerTeam === team && m.squad === 'FIRST_TEAM' && m.eligibilityReason === null && !selected.has(id) && !roles.has(p.position), 'lineup.eligibility'); selected.add(id); roles.add(p.position); } }
  check(v.managedTeam in lineups, 'managedTeam');
  for (const field of ['registeredPlayers', 'activeSeriesPlayers']) for (const ids of Object.values(obj(v[field], field))) check(strings(ids, field).every(id => id in players), `${field}.identity`);
  const commands = strings(v.allowedCommands, 'commands'); check(commands.every(c => ['SELECT_STARTER', 'MOVE_SQUAD'].includes(c)) && (!v.readOnly || commands.length === 0), 'commands.readOnly');
  return v as unknown as CareerRoster;
}
export function validateRosterChange(value: unknown): RosterChange {
  const v = exact(value, 'replayed receipt roster', 'change'); check(typeof v.replayed === 'boolean', 'replayed');
  const roster = validateCareerRoster(v.roster), r = exact(v.receipt, 'clientCommandId careerId sourceYear resultingRevision stateHash applicationPolicy', 'receipt');
  check(r.careerId === roster.careerId && integer(r.sourceYear) && integer(r.resultingRevision) && r.resultingRevision <= roster.revision && text(r.clientCommandId) && typeof r.stateHash === 'string' && /^[0-9a-f]{64}$/.test(r.stateHash) && text(r.applicationPolicy), 'receipt.scope');
  return v as unknown as RosterChange;
}
export const rosterOperationKey = (career: string) => `lolfm.career.roster-command.v1.${career}`;
export function readRosterOperation(storage: Pick<Storage, 'getItem'>, career: string): RosterCommand | null { const raw = storage.getItem(rosterOperationKey(career)); return raw ? validateRosterCommand(JSON.parse(raw)) : null; }
