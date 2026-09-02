import type { CareerApiFailure } from './api/careerApi.failure';

export const CAREER_POINTER_KEY = 'lolmanager.career.pointer.v1';
export const CAREER_CREATE_OPERATION_KEY = 'lolmanager.career.create-operation.v1';
export const CAREER_RETURN_CONTEXT_KEY = 'lolmanager.career.return-context.v1';
const CAREER_ID = /^career_[0-9a-f]{64}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export interface CareerCreateSelection { saveName: string; managerName: string; managedTeamCode: string }
export interface CareerCreateOperation { schemaVersion: 'CAREER_CREATE_OPERATION_V1'; fingerprint: string; selection: CareerCreateSelection; clientCommandId: string }
export interface CareerReturnContext { schemaVersion: 'CAREER_RETURN_CONTEXT_V1'; careerId: string }
export interface PointerStorage { getItem(key: string): string | null; setItem(key: string, value: string): void; removeItem(key: string): void }

export function normalizeCareerSelection(value: CareerCreateSelection): CareerCreateSelection {
  return { saveName: value.saveName.normalize('NFC').trim(), managerName: value.managerName.normalize('NFC').trim(), managedTeamCode: value.managedTeamCode.trim() };
}
export function careerSelectionFingerprint(value: CareerCreateSelection): string { return JSON.stringify(normalizeCareerSelection(value)); }
export function readCareerPointer(storage: PointerStorage): string | null {
  const value = storage.getItem(CAREER_POINTER_KEY); if (!value) return null;
  if (!CAREER_ID.test(value)) { storage.removeItem(CAREER_POINTER_KEY); return null; }
  return value;
}
export function writeCareerPointer(storage: PointerStorage, careerId: string): void {
  if (!CAREER_ID.test(careerId)) throw new Error('canonical Career identity required'); storage.setItem(CAREER_POINTER_KEY, careerId);
}
export function clearCareerPointer(storage: PointerStorage): void { storage.removeItem(CAREER_POINTER_KEY); }
export function readCareerCreateOperation(storage: PointerStorage): CareerCreateOperation | null {
  const raw = storage.getItem(CAREER_CREATE_OPERATION_KEY); if (!raw) return null;
  try {
    const value = JSON.parse(raw) as CareerCreateOperation;
    if (value.schemaVersion !== 'CAREER_CREATE_OPERATION_V1' || typeof value.fingerprint !== 'string' || !value.selection || careerSelectionFingerprint(value.selection) !== value.fingerprint || !UUID.test(value.clientCommandId)) throw new Error('invalid');
    return value;
  } catch { storage.removeItem(CAREER_CREATE_OPERATION_KEY); return null; }
}
export function logicalCareerCreate(storage: PointerStorage, selection: CareerCreateSelection, uuid: () => string = () => crypto.randomUUID()): CareerCreateOperation {
  const normalized = normalizeCareerSelection(selection); const fingerprint = careerSelectionFingerprint(normalized); const current = readCareerCreateOperation(storage);
  if (current?.fingerprint === fingerprint) return current;
  const next: CareerCreateOperation = { schemaVersion: 'CAREER_CREATE_OPERATION_V1', fingerprint, selection: normalized, clientCommandId: uuid() };
  if (!UUID.test(next.clientCommandId)) throw new Error('UUID generator returned an invalid identity');
  storage.setItem(CAREER_CREATE_OPERATION_KEY, JSON.stringify(next)); return next;
}
export function clearCareerCreateOperation(storage: PointerStorage): void { storage.removeItem(CAREER_CREATE_OPERATION_KEY); }
export function isAmbiguousCareerCreateFailure(failure: CareerApiFailure): boolean {
  return ['NETWORK', 'TIMEOUT', 'CANCELLED'].includes(failure.kind) || failure.retryable || failure.httpStatus === 503;
}
export function careerPointerRecoveryAction(failure: CareerApiFailure): 'CLEAR_NOT_FOUND' | 'KEEP_RETRYABLE' | 'KEEP_INTEGRITY' | 'KEEP_CONTRACT' | 'KEEP_OTHER' {
  if (failure.code === 'CAREER_NOT_FOUND') return 'CLEAR_NOT_FOUND';
  if (isAmbiguousCareerCreateFailure(failure)) return 'KEEP_RETRYABLE';
  if (['CAREER_COMMAND_RECEIPT_INTEGRITY_FAILURE', 'CAREER_LINKED_SEASON_INTEGRITY_FAILURE', 'CAREER_RESOURCE_INTEGRITY_FAILURE'].includes(failure.code ?? '')) return 'KEEP_INTEGRITY';
  if (failure.kind === 'CONTRACT' || failure.kind === 'INVALID_JSON') return 'KEEP_CONTRACT';
  return 'KEEP_OTHER';
}
export function writeCareerReturnContext(storage: PointerStorage, careerId: string): void {
  if (!CAREER_ID.test(careerId)) throw new Error('canonical Career identity required');
  storage.setItem(CAREER_RETURN_CONTEXT_KEY, JSON.stringify({ schemaVersion: 'CAREER_RETURN_CONTEXT_V1', careerId } satisfies CareerReturnContext));
}
export function readCareerReturnContext(storage: PointerStorage): CareerReturnContext | null {
  const raw = storage.getItem(CAREER_RETURN_CONTEXT_KEY); if (!raw) return null;
  try { const value = JSON.parse(raw) as CareerReturnContext; if (value.schemaVersion !== 'CAREER_RETURN_CONTEXT_V1' || !CAREER_ID.test(value.careerId)) throw new Error('invalid'); return value; }
  catch { storage.removeItem(CAREER_RETURN_CONTEXT_KEY); return null; }
}
export function clearCareerReturnContext(storage: PointerStorage): void { storage.removeItem(CAREER_RETURN_CONTEXT_KEY); }
