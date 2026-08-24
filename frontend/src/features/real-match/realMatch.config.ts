import type { MatchDataSource } from './realMatch.contract';

function positiveInteger(value: string | undefined, fallback: number): number {
  if (!value) return fallback;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

const configuredSource = import.meta.env.VITE_REAL_MATCH_DATA_SOURCE?.trim().toLowerCase();
const apiBaseUrl = (import.meta.env.VITE_REAL_MATCH_API_BASE_URL?.trim() || 'http://localhost:8080').replace(/\/+$/, '');

export const realMatchConfig = {
  dataSource: (configuredSource === 'reference' ? 'REFERENCE' : 'LIVE') as MatchDataSource,
  apiBaseUrl,
  optionsTimeoutMs: positiveInteger(import.meta.env.VITE_REAL_MATCH_OPTIONS_TIMEOUT_MS, 30_000),
  simulateTimeoutMs: positiveInteger(import.meta.env.VITE_REAL_MATCH_SIMULATE_TIMEOUT_MS, 300_000),
} as const;
