export type TrainingIntensity = 'RECOVERY' | 'LIGHT' | 'NORMAL' | 'INTENSIVE';
export type TrainingFocus = 'BALANCED' | 'COMMON_SKILLS' | 'ROLE_SKILLS' | 'SPECIFIC_SKILL' | 'CHAMPION_FOCUS';
export interface TrainingPlan { intensity: TrainingIntensity; focus: TrainingFocus; skill: string | null; champions: string[] }
export interface TrainingSchedule { team: string; current: TrainingPlan | null; effectiveOn: string; pending: TrainingPlan | null; pendingOn: string | null; pendingClear: boolean }
export interface DevelopmentPlayer { playerId: string; currentAbility: number; potentialAbility: number | null; growthStatus: string; trainingEfficiency: number; effectivePlan: TrainingPlan; development: { internalRatings: Record<string, number>; internalProficiencies: Record<string, number>; fatigue: number; override: TrainingSchedule | null } }
export interface DevelopmentGain { date: string; playerId: string; internalGain: number; proficiencyGain: number; integerRises: Record<string, number>; seasonYear: number }
export interface CareerDevelopment { schemaVersion: 'CAREER_DEVELOPMENT_VIEW_V1'; careerId: string; seasonYear: number; revision: number; currentDate: string; managedTeam: string; readOnly: boolean; teamPlan: TrainingSchedule | null; players: DevelopmentPlayer[]; recentChanges: DevelopmentGain[]; monthlySummaries: DevelopmentGain[]; legalChampions: Record<string, string[]>; policyVersion: string }
export interface TrainingCommand { schemaVersion: 'CAREER_TRAINING_COMMAND_V1'; sourceYear: number; expectedRevision: number; playerId: string | null; plan: TrainingPlan | null; clearOverride: boolean; clientCommandId: string }
export interface TrainingChange { replayed: boolean; receipt: { clientCommandId: string; careerId: string; sourceYear: number; resultingRevision: number; effectiveOn: string; stateHash: string }; development: CareerDevelopment }
const intensities = ['RECOVERY', 'LIGHT', 'NORMAL', 'INTENSIVE'], focuses = ['BALANCED', 'COMMON_SKILLS', 'ROLE_SKILLS', 'SPECIFIC_SKILL', 'CHAMPION_FOCUS'];
const obj = (v: unknown): Record<string, unknown> => { if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('훈련 데이터 형식 오류'); return v as Record<string, unknown>; };
const check: (v: unknown) => asserts v = v => { if (!v) throw new Error('훈련 데이터 계약 오류'); };
const integer = (v: unknown, lo = 0, hi = Number.MAX_SAFE_INTEGER): v is number => Number.isSafeInteger(v) && Number(v) >= lo && Number(v) <= hi;
const date = (v: unknown) => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v);
function plan(value: unknown) { const p = obj(value); check(intensities.includes(String(p.intensity)) && focuses.includes(String(p.focus)) && Array.isArray(p.champions)); check((p.focus === 'SPECIFIC_SKILL') === (typeof p.skill === 'string' && !!p.skill)); check(p.focus === 'CHAMPION_FOCUS' ? p.champions.length >= 1 && p.champions.length <= 2 && p.champions.every(c => typeof c === 'string' && !!c) && new Set(p.champions).size === p.champions.length : p.champions.length === 0); }
export function validateTrainingCommand(value: unknown): TrainingCommand {
  const v = obj(value); check(Object.keys(v).sort().join() === 'schemaVersion sourceYear expectedRevision playerId plan clearOverride clientCommandId'.split(' ').sort().join());
  check(v.schemaVersion === 'CAREER_TRAINING_COMMAND_V1' && integer(v.sourceYear, 2026) && integer(v.expectedRevision) && (v.playerId === null || typeof v.playerId === 'string' && !!v.playerId) && typeof v.clearOverride === 'boolean');
  check(typeof v.clientCommandId === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v.clientCommandId));
  if (v.clearOverride) check(v.playerId !== null && v.plan === null); else { plan(v.plan); if (v.playerId === null) check(!['SPECIFIC_SKILL', 'CHAMPION_FOCUS'].includes(String(obj(v.plan).focus))); }
  return v as unknown as TrainingCommand;
}
export function validateCareerDevelopment(value: unknown): CareerDevelopment {
  const v = obj(value); check(v.schemaVersion === 'CAREER_DEVELOPMENT_VIEW_V1' && typeof v.careerId === 'string' && integer(v.seasonYear, 2026) && integer(v.revision) && date(v.currentDate) && typeof v.readOnly === 'boolean' && typeof v.managedTeam === 'string' && Array.isArray(v.players));
  const seen = new Set(); for (const raw of v.players) { const p = obj(raw), d = obj(p.development), r = obj(d.internalRatings), prof = obj(d.internalProficiencies); check(typeof p.playerId === 'string' && !seen.has(p.playerId)); seen.add(p.playerId); check(integer(p.currentAbility, 1, 200) && (p.potentialAbility === null || integer(p.potentialAbility, 1, 200)) && integer(p.trainingEfficiency, 250, 1000) && integer(d.fatigue, 0, 1000)); check(Object.keys(r).length === 12 && Object.values(r).every(n => integer(n, 1000, 20000)) && Object.values(prof).every(n => integer(n, 1000, 20000))); plan(p.effectivePlan); }
  for (const field of ['recentChanges', 'monthlySummaries']) { check(Array.isArray(v[field])); for (const raw of v[field]) { const g = obj(raw); check(date(g.date) && typeof g.playerId === 'string' && seen.has(g.playerId) && integer(g.internalGain) && integer(g.proficiencyGain) && integer(g.seasonYear, 2026)); check(Object.values(obj(g.integerRises)).every(n => integer(n, 1))); } }
  obj(v.legalChampions); return v as unknown as CareerDevelopment;
}
export function validateTrainingChange(value: unknown): TrainingChange {
  const v = obj(value), d = validateCareerDevelopment(v.development), r = obj(v.receipt); check(typeof v.replayed === 'boolean' && r.careerId === d.careerId && integer(r.sourceYear, 2026) && integer(r.resultingRevision) && r.resultingRevision <= d.revision && date(r.effectiveOn) && typeof r.clientCommandId === 'string' && typeof r.stateHash === 'string' && /^[0-9a-f]{64}$/.test(r.stateHash)); return v as unknown as TrainingChange;
}
export const trainingOperationKey = (career: string) => `lolfm.career.training-command.v1.${career}`;
export function readTrainingOperation(storage: Pick<Storage, 'getItem'>, career: string): TrainingCommand | null { const raw = storage.getItem(trainingOperationKey(career)); return raw ? validateTrainingCommand(JSON.parse(raw)) : null; }
