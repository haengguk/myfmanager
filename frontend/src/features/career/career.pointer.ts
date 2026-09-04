import type { CareerApiFailure } from './api/careerApi.failure';
import type { CareerPendingAdvanceDto } from './api/careerApi.types';

export const CAREER_POINTER_KEY = 'lolmanager.career.pointer.v1';
export const CAREER_CREATE_OPERATION_KEY = 'lolmanager.career.create-operation.v2';
const LEGACY_CAREER_CREATE_OPERATION_KEY = 'lolmanager.career.create-operation.v1';
export const CAREER_ADVANCE_OPERATION_KEY = 'lolmanager.career.advance-operations.v2';
const LEGACY_CAREER_ADVANCE_OPERATION_KEY = 'lolmanager.career.advance-operation.v1';
export const CAREER_RETURN_CONTEXT_KEY = 'lolmanager.career.return-context.v1';
const CAREER_ID = /^career_[0-9a-f]{64}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export interface CareerCreateSelection { saveName: string; managerName: string; managedTeamCode: string }
export interface CareerCreateOperation { schemaVersion: 'CAREER_CREATE_OPERATION_V2'; canonicalSelectionKey: string; selection: CareerCreateSelection; clientCommandId: string }
export interface CareerAdvanceOperation { schemaVersion: 'CAREER_ADVANCE_OPERATION_V1'; careerId: string; expectedCalendarRevision: number; mode: 'ADVANCE_ONE_DAY' | 'ADVANCE_TO_NEXT_EVENT'; clientCommandId: string }
interface CareerAdvanceOperations { schemaVersion: 'CAREER_ADVANCE_OPERATIONS_V2'; operations: Record<string, CareerAdvanceOperation> }
export interface CareerReturnContext { schemaVersion: 'CAREER_RETURN_CONTEXT_V1'; careerId: string }
export interface PointerStorage { getItem(key: string): string | null; setItem(key: string, value: string): void; removeItem(key: string): void }

export function normalizeCareerSelection(value: CareerCreateSelection): CareerCreateSelection {
  return { saveName: value.saveName.normalize('NFC').trim(), managerName: value.managerName.normalize('NFC').trim(), managedTeamCode: value.managedTeamCode.trim() };
}
export function careerCanonicalSelectionKey(value: CareerCreateSelection): string { return JSON.stringify(normalizeCareerSelection(value)); }
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
  const raw = storage.getItem(CAREER_CREATE_OPERATION_KEY);
  try {
    if (raw) {
      const value = JSON.parse(raw) as CareerCreateOperation;
      if (value.schemaVersion !== 'CAREER_CREATE_OPERATION_V2' || typeof value.canonicalSelectionKey !== 'string' || !value.selection || careerCanonicalSelectionKey(value.selection) !== value.canonicalSelectionKey || !UUID.test(value.clientCommandId)) throw new Error('invalid');
      return value;
    }
    const legacyRaw = storage.getItem(LEGACY_CAREER_CREATE_OPERATION_KEY); if (!legacyRaw) return null;
    const legacy = JSON.parse(legacyRaw) as { schemaVersion?: unknown; fingerprint?: unknown; selection?: CareerCreateSelection; clientCommandId?: unknown };
    if (legacy.schemaVersion !== 'CAREER_CREATE_OPERATION_V1' || typeof legacy.fingerprint !== 'string' || !legacy.selection || careerCanonicalSelectionKey(legacy.selection) !== legacy.fingerprint || typeof legacy.clientCommandId !== 'string' || !UUID.test(legacy.clientCommandId)) throw new Error('invalid legacy');
    const migrated: CareerCreateOperation = { schemaVersion: 'CAREER_CREATE_OPERATION_V2', canonicalSelectionKey: legacy.fingerprint, selection: normalizeCareerSelection(legacy.selection), clientCommandId: legacy.clientCommandId };
    storage.setItem(CAREER_CREATE_OPERATION_KEY, JSON.stringify(migrated)); storage.removeItem(LEGACY_CAREER_CREATE_OPERATION_KEY); return migrated;
  } catch { storage.removeItem(CAREER_CREATE_OPERATION_KEY); storage.removeItem(LEGACY_CAREER_CREATE_OPERATION_KEY); return null; }
}
export function logicalCareerCreate(storage: PointerStorage, selection: CareerCreateSelection, uuid: () => string = () => crypto.randomUUID()): CareerCreateOperation {
  const normalized = normalizeCareerSelection(selection); const canonicalSelectionKey = careerCanonicalSelectionKey(normalized); const current = readCareerCreateOperation(storage);
  if (current?.canonicalSelectionKey === canonicalSelectionKey) return current;
  const next: CareerCreateOperation = { schemaVersion: 'CAREER_CREATE_OPERATION_V2', canonicalSelectionKey, selection: normalized, clientCommandId: uuid() };
  if (!UUID.test(next.clientCommandId)) throw new Error('UUID generator returned an invalid identity');
  storage.setItem(CAREER_CREATE_OPERATION_KEY, JSON.stringify(next)); return next;
}
export function clearCareerCreateOperation(storage: PointerStorage): void { storage.removeItem(CAREER_CREATE_OPERATION_KEY); storage.removeItem(LEGACY_CAREER_CREATE_OPERATION_KEY); }

function validAdvanceOperation(value: unknown, expectedCareerId?: string): value is CareerAdvanceOperation {
  if (!value || typeof value !== 'object') return false;
  const operation = value as CareerAdvanceOperation;
  return operation.schemaVersion === 'CAREER_ADVANCE_OPERATION_V1'
    && CAREER_ID.test(operation.careerId)
    && (!expectedCareerId || operation.careerId === expectedCareerId)
    && Number.isSafeInteger(operation.expectedCalendarRevision)
    && operation.expectedCalendarRevision >= 0
    && ['ADVANCE_ONE_DAY', 'ADVANCE_TO_NEXT_EVENT'].includes(operation.mode)
    && UUID.test(operation.clientCommandId);
}

function readAdvanceOperations(storage: PointerStorage): CareerAdvanceOperations {
  const raw = storage.getItem(CAREER_ADVANCE_OPERATION_KEY);
  const operations: Record<string, CareerAdvanceOperation> = {};
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as CareerAdvanceOperations;
      if (parsed.schemaVersion !== 'CAREER_ADVANCE_OPERATIONS_V2' || !parsed.operations || typeof parsed.operations !== 'object' || Array.isArray(parsed.operations)) throw new Error('invalid map');
      for (const [careerId, value] of Object.entries(parsed.operations)) if (validAdvanceOperation(value, careerId)) operations[careerId] = value;
    } catch { storage.removeItem(CAREER_ADVANCE_OPERATION_KEY); }
  }
  const legacyRaw = storage.getItem(LEGACY_CAREER_ADVANCE_OPERATION_KEY);
  if (legacyRaw) {
    try { const legacy = JSON.parse(legacyRaw) as unknown; if (validAdvanceOperation(legacy)) operations[legacy.careerId] ??= legacy; }
    catch { /* invalid legacy is removed below */ }
    storage.removeItem(LEGACY_CAREER_ADVANCE_OPERATION_KEY);
  }
  const result: CareerAdvanceOperations = { schemaVersion: 'CAREER_ADVANCE_OPERATIONS_V2', operations };
  if (Object.keys(operations).length) storage.setItem(CAREER_ADVANCE_OPERATION_KEY, JSON.stringify(result));
  else storage.removeItem(CAREER_ADVANCE_OPERATION_KEY);
  return result;
}

function writeCareerAdvanceOperation(storage: PointerStorage, operation: CareerAdvanceOperation): void {
  if (!validAdvanceOperation(operation)) throw new Error('invalid advance operation');
  const current = readAdvanceOperations(storage);
  current.operations[operation.careerId] = operation;
  storage.setItem(CAREER_ADVANCE_OPERATION_KEY, JSON.stringify(current));
}

export function readCareerAdvanceOperation(storage: PointerStorage, careerId: string): CareerAdvanceOperation | null {
  if (!CAREER_ID.test(careerId)) return null;
  return readAdvanceOperations(storage).operations[careerId] ?? null;
}
export function logicalCareerAdvance(storage: PointerStorage, careerId: string, expectedCalendarRevision: number, mode: CareerAdvanceOperation['mode'], uuid: () => string = () => crypto.randomUUID()): CareerAdvanceOperation {
  const current = readCareerAdvanceOperation(storage, careerId);
  if (current) {
    if (current.expectedCalendarRevision === expectedCalendarRevision && current.mode === mode) return current;
    throw new Error('pending Career advance cannot be replaced');
  }
  const next: CareerAdvanceOperation = { schemaVersion: 'CAREER_ADVANCE_OPERATION_V1', careerId, expectedCalendarRevision, mode, clientCommandId: uuid() };
  if (!validAdvanceOperation(next)) throw new Error('invalid advance operation');
  writeCareerAdvanceOperation(storage, next); return next;
}
export function reconcileCareerAdvanceOperation(storage: PointerStorage, careerId: string, pending: CareerPendingAdvanceDto | null): CareerAdvanceOperation | null {
  if (!pending) return readCareerAdvanceOperation(storage, careerId);
  const server: CareerAdvanceOperation = { schemaVersion: 'CAREER_ADVANCE_OPERATION_V1', careerId, expectedCalendarRevision: pending.expectedCalendarRevision, mode: pending.mode, clientCommandId: pending.clientCommandId };
  writeCareerAdvanceOperation(storage, server); return server;
}
export function clearCareerAdvanceOperation(storage: PointerStorage, careerId: string): void {
  const current = readAdvanceOperations(storage);
  delete current.operations[careerId];
  if (Object.keys(current.operations).length) storage.setItem(CAREER_ADVANCE_OPERATION_KEY, JSON.stringify(current));
  else storage.removeItem(CAREER_ADVANCE_OPERATION_KEY);
}
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
