import type { InboxLink } from './api/careerInbox';
export function matchDestination(link: InboxLink): 'SERIES' | 'LEAGUE_PREPARATION' | 'COMPETITION_PREPARATION' {
  if (link.matchState === 'IN_PROGRESS' && link.seriesId) return 'SERIES';
  return link.competition === 'LCK_REGULAR_R1_R2' ? 'LEAGUE_PREPARATION' : 'COMPETITION_PREPARATION';
}
export async function currentNavigation(link: InboxLink, refresh: () => Promise<unknown>, valid: () => boolean, apply: () => void) {
  if (link.current && await refresh() !== true) return;
  if (valid()) apply();
}

export function preparationStillCurrent(link: InboxLink, next: InboxLink | null, activeYear: number): boolean {
  return link.seasonYear === activeYear && !!next && next.sourceId === link.sourceId
    && next.seasonYear === link.seasonYear && next.matchState === 'UNSTARTED';
}
