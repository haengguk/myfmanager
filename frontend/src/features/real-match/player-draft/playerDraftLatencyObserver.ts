export type PlayerDraftLatencyPhase =
  | 'PLAYER_ACTION_CONFIRM_INPUT'
  | 'PLAYER_ACTION_REQUEST_PREPARED'
  | 'PLAYER_ACTION_FETCH_START'
  | 'PLAYER_ACTION_RESPONSE_HEADERS'
  | 'PLAYER_ACTION_RESPONSE_BODY_START'
  | 'PLAYER_ACTION_RESPONSE_BODY_COMPLETE'
  | 'PLAYER_ACTION_JSON_PARSE_START'
  | 'PLAYER_ACTION_JSON_PARSE_COMPLETE'
  | 'PLAYER_ACTION_VALIDATION_START'
  | 'PLAYER_ACTION_VALIDATION_COMPLETE'
  | 'PLAYER_ACTION_STATE_ADAPTER_START'
  | 'PLAYER_ACTION_STATE_ADAPTER_COMPLETE'
  | 'PLAYER_ACTION_DOM_STABLE'
  | 'PLAYER_DRAFT_SIMULATE_CLICK'
  | 'PLAYER_DRAFT_SIMULATE_FETCH_START'
  | 'PLAYER_DRAFT_SIMULATE_RESPONSE_HEADERS'
  | 'PLAYER_DRAFT_SIMULATE_RESPONSE_BODY_START'
  | 'PLAYER_DRAFT_SIMULATE_RESPONSE_BODY_COMPLETE'
  | 'PLAYER_DRAFT_SIMULATE_JSON_PARSE_START'
  | 'PLAYER_DRAFT_SIMULATE_JSON_PARSE_COMPLETE'
  | 'PLAYER_DRAFT_SIMULATE_VALIDATION_START'
  | 'PLAYER_DRAFT_SIMULATE_VALIDATION_COMPLETE'
  | 'PLAYER_DRAFT_SIMULATE_COMMON_SEMANTIC_VALIDATION_START'
  | 'PLAYER_DRAFT_SIMULATE_COMMON_SEMANTIC_VALIDATION_COMPLETE'
  | 'PLAYER_DRAFT_SIMULATE_NORMALIZATION_START'
  | 'PLAYER_DRAFT_SIMULATE_NORMALIZATION_COMPLETE'
  | 'PLAYER_DRAFT_SIMULATE_REACT_STATE_START'
  | 'PLAYER_DRAFT_SIMULATE_REACT_STATE_COMPLETE'
  | 'PLAYER_DRAFT_SIMULATE_PLAYBACK_DOM_STABLE';

export interface PlayerDraftLatencyEventV1 {
  readonly schemaVersion: 'PLAYER_DRAFT_LATENCY_EVENT_V1';
  readonly phase: PlayerDraftLatencyPhase;
  readonly correlationId: string;
  readonly monotonicMilliseconds: number;
  readonly detail: Readonly<Record<string, string | number | boolean | null>>;
}

type PlayerDraftLatencyDetail = Readonly<Record<string, string | number | boolean | null>>;

declare global {
  interface Window {
    __LOLMANAGER_PLAYER_DRAFT_LATENCY_OBSERVER_V1__?:
      (event: PlayerDraftLatencyEventV1) => void;
  }
}

/** Playwright-only observer boundary. It is a no-op without an injected observer. */
export function markPlayerDraftLatency(
  phase: PlayerDraftLatencyPhase,
  correlationId: string,
  detail: PlayerDraftLatencyDetail | (() => PlayerDraftLatencyDetail) = {},
): void {
  const observer = window.__LOLMANAGER_PLAYER_DRAFT_LATENCY_OBSERVER_V1__;
  if (typeof observer !== 'function') return;
  try {
    observer({
      schemaVersion: 'PLAYER_DRAFT_LATENCY_EVENT_V1',
      phase,
      correlationId,
      monotonicMilliseconds: performance.now(),
      detail: typeof detail === 'function' ? detail() : detail,
    });
  } catch {
    // Diagnostic collection must never change the application flow it observes.
  }
}
