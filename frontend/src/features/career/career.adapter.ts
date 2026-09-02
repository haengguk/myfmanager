import type { CareerViewDto } from './api/careerApi.types';

export type CareerResumeRoute =
  | { kind: 'LEAGUE'; leagueId: string; seasonId: string }
  | { kind: 'PLAYER_SERIES'; leagueId: string; seasonId: string; fixtureId: string; seriesId: string };

export function careerResumeRoute(career: CareerViewDto): CareerResumeRoute {
  const { resume } = career;
  if (resume.kind === 'PLAYER_SERIES') {
    const actionable = resume.allowedCommands.includes('RESUME_PLAYER_SERIES') || resume.allowedCommands.includes('RECONCILE_PLAYER_SERIES_COMPLETION');
    if (!actionable || !resume.fixtureId || !resume.seriesId) throw new Error('Career Player Series resume is not actionable');
    return { kind: 'PLAYER_SERIES', leagueId: resume.leagueId, seasonId: resume.seasonId, fixtureId: resume.fixtureId, seriesId: resume.seriesId };
  }
  return { kind: 'LEAGUE', leagueId: resume.leagueId, seasonId: resume.seasonId };
}

export const CAREER_RESUME_COPY: Readonly<Record<CareerViewDto['resume']['kind'], { label: string; description: string }>> = {
  LEAGUE_DASHBOARD: { label: '리그 운영 계속', description: 'League 대시보드에서 서버의 최신 시즌 상태와 가능한 작업을 확인합니다.' },
  PLAYER_SERIES: { label: 'Player Series 계속', description: '연결된 fixture의 기존 BO3 Series를 생성 없이 복원합니다.' },
  SEASON_COMPLETE: { label: '완료 시즌 보기', description: '최종 League 대시보드를 엽니다. 다음 시즌 생성은 아직 지원하지 않습니다.' },
  ATTENTION_REQUIRED: { label: '리그 상태 확인', description: 'League 대시보드에서 차단 상태와 서버가 허용한 복구 작업을 확인합니다.' },
};
