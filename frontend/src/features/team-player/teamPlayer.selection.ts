import type { PlayerSummaryDto, TeamPlayerWorkspaceDto } from './api/teamPlayerApi.types';

const POINTER_KEY = 'lolmanager.team-player.pointer.v1';
const POINTER_SCHEMA = 'TEAM_PLAYER_NAVIGATION_POINTER_V1';

export interface TeamPlayerSelection { teamCode: string; playerId: string }

interface StoredPointer extends TeamPlayerSelection { schemaVersion: typeof POINTER_SCHEMA; catalogVersion: string }

export function resolveInitialSelection(workspace: TeamPlayerWorkspaceDto, storage: Storage): TeamPlayerSelection {
  let stored: Partial<StoredPointer> | null = null;
  try { stored = JSON.parse(storage.getItem(POINTER_KEY) ?? 'null') as Partial<StoredPointer> | null; }
  catch { storage.removeItem(POINTER_KEY); }
  if (stored?.schemaVersion === POINTER_SCHEMA && stored.catalogVersion === workspace.metadata.catalog.catalogVersion
      && typeof stored.teamCode === 'string' && typeof stored.playerId === 'string') {
    const team = workspace.teams.teams.find((entry) => entry.teamCode === stored?.teamCode);
    if (team?.lineup.some((entry) => entry.playerId === stored?.playerId)) return { teamCode: stored.teamCode, playerId: stored.playerId };
  }
  const firstTeam = workspace.teams.teams[0];
  return { teamCode: firstTeam.teamCode, playerId: firstTeam.lineup[0].playerId };
}

export function writeSelection(storage: Storage, selection: TeamPlayerSelection, catalogVersion: string): void {
  const value: StoredPointer = { schemaVersion: POINTER_SCHEMA, catalogVersion, ...selection };
  storage.setItem(POINTER_KEY, JSON.stringify(value));
}

export function selectTeam(workspace: TeamPlayerWorkspaceDto, teamCode: string): TeamPlayerSelection {
  const team = workspace.teams.teams.find((entry) => entry.teamCode === teamCode);
  if (!team) throw new Error('Unknown authoritative team selection');
  return { teamCode: team.teamCode, playerId: team.lineup[0].playerId };
}

export function selectedPlayer(workspace: TeamPlayerWorkspaceDto, playerId: string): PlayerSummaryDto | null {
  return workspace.players.players.find((entry) => entry.playerId === playerId) ?? null;
}

export class PlayerDetailRequestCoordinator<T> {
  private current: { playerId: string; controller: AbortController; promise: Promise<T> } | null = null;

  load(playerId: string, loader: (signal: AbortSignal) => Promise<T>): Promise<T> {
    if (this.current?.playerId === playerId && !this.current.controller.signal.aborted) return this.current.promise;
    this.current?.controller.abort();
    const controller = new AbortController();
    const promise = loader(controller.signal).finally(() => {
      if (this.current?.promise === promise) this.current = null;
    });
    this.current = { playerId, controller, promise };
    return promise;
  }

  abort(): void {
    this.current?.controller.abort();
    this.current = null;
  }
}
