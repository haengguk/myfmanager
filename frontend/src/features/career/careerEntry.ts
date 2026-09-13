import type { CareerSummaryDto } from './api/careerApi.types';
import type { CareerCreateSelection, PointerStorage } from './career.pointer';

export type CareerEntryView = 'main' | 'load' | 'new' | 'club';
export const entryKey = (api: string) => `lolmanager.career.entry.v1:${api}`;
export const draftKey = (api: string) => `lolmanager.career.entry-draft.v1:${api}`;
export const recentKey = (api: string) => `lolmanager.career.recent.v1:${api}`;
export function readEntryView(storage: PointerStorage, api: string, pointer: string | null): CareerEntryView {
  const value = storage.getItem(entryKey(api));
  return value === 'main' || value === 'load' || value === 'new' ? value : pointer ? 'club' : 'main';
}
export function recentCareer(careers: readonly CareerSummaryDto[], preferred: string | null) {
  const entered = careers.find(c => c.careerId === preferred);
  return { career: entered ?? careers.find(c => c.compatibility?.status !== 'UNSUPPORTED') ?? null, previouslyEntered: !!entered };
}
export function readEntryDraft(storage: PointerStorage, api: string): CareerCreateSelection {
  try {
    const v = JSON.parse(storage.getItem(draftKey(api)) ?? 'null');
    if (v && ['saveName', 'managerName', 'managedTeamCode'].every(k => typeof v[k] === 'string')) return { saveName: v.saveName, managerName: v.managerName, managedTeamCode: v.managedTeamCode };
  } catch { /* An optional input draft never changes command recovery. */ }
  return { saveName: '', managerName: '', managedTeamCode: '' };
}
export function displayNameError(value: string): string | null {
  const normalized = value.normalize('NFC').trim();
  return [...normalized].length < 1 || [...normalized].length > 80 || /[\u0000-\u001f\u007f]/.test(normalized) ? '1~80자로 입력하세요. 제어 문자는 사용할 수 없습니다.' : null;
}
