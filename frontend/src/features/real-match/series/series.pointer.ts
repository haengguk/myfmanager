import { SERIES_POINTER_KEY } from './series.types.ts';

const SERIES_ID = /^series_[0-9a-f]{64}$/;

export interface SeriesPointerStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export type SeriesPointerRecoveryAction = 'KEEP_RETRYABLE' | 'KEEP_VERSION_ERROR' | 'CLEAR_NOT_FOUND' | 'CLEAR_EXPIRED' | 'KEEP_OTHER';

export function seriesPointerRecoveryAction(failure: { kind: string; code: string | null }): SeriesPointerRecoveryAction {
  if (failure.code === 'SERIES_NOT_FOUND') return 'CLEAR_NOT_FOUND';
  if (failure.code === 'SERIES_EXPIRED') return 'CLEAR_EXPIRED';
  if (failure.kind === 'NETWORK' || failure.kind === 'TIMEOUT' || failure.kind === 'CANCELLED') return 'KEEP_RETRYABLE';
  if (failure.kind === 'CONTRACT' || failure.kind === 'INVALID_JSON') return 'KEEP_VERSION_ERROR';
  return 'KEEP_OTHER';
}

export function readSeriesPointer(storage: SeriesPointerStorage): string | null {
  const value = storage.getItem(SERIES_POINTER_KEY);
  if (value === null) return null;
  if (!SERIES_ID.test(value)) { storage.removeItem(SERIES_POINTER_KEY); return null; }
  return value;
}

export function writeSeriesPointer(storage: SeriesPointerStorage, seriesId: string): void {
  if (!SERIES_ID.test(seriesId)) throw new Error('Series pointer ID 형식이 올바르지 않습니다.');
  storage.setItem(SERIES_POINTER_KEY, seriesId);
}

export function clearSeriesPointer(storage: SeriesPointerStorage): void { storage.removeItem(SERIES_POINTER_KEY); }
