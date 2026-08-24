import type { MatchSetupOptionsViewModel, MatchTeamOptionViewModel } from './matchSession.types';
import type { Position } from './realMatch.types';

const positions: readonly Position[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

const roster = (teamId: string, names: readonly string[]) => positions.map((position, index) => ({
  playerId: `${teamId.toLowerCase()}-${names[index].toLowerCase()}`,
  playerName: names[index],
  position,
}));

const team = (
  code: string,
  name: string,
  record: string,
  players: readonly string[],
): MatchTeamOptionViewModel => ({
  teamId: code,
  code,
  name,
  league: 'LCK',
  record,
  roster: roster(code, players),
});

export const matchSetupOptionsFixture: MatchSetupOptionsViewModel = {
  seasonLabel: '2026년 8월 24일 · 경기 생성',
  gameNumber: 1,
  seriesType: '단판',
  draftRule: '하드 피어리스',
  defaultSeed: '73',
  teams: [
    team('GEN', 'Gen.G', '12승 3패', ['Kiin', 'Canyon', 'Chovy', 'Ruler', 'Duro']),
    team('T1', 'T1', '10승 5패', ['Doran', 'Oner', 'Faker', 'Gumayusi', 'Keria']),
    team('HLE', 'Hanwha Life Esports', '9승 6패', ['Zeus', 'Peanut', 'Zeka', 'Viper', 'Delight']),
    team('DK', 'Dplus KIA', '8승 7패', ['Siwoo', 'Lucid', 'ShowMaker', 'Aiming', 'BeryL']),
    team('KT', 'kt Rolster', '8승 7패', ['PerfecT', 'Cuzz', 'Bdd', 'deokdam', 'Peter']),
    team('DRX', 'DRX', '6승 9패', ['Rich', 'Sponge', 'Ucal', 'Teddy', 'Andil']),
    team('NS', 'Nongshim RedForce', '6승 9패', ['Kingen', 'GIDEON', 'Fisher', 'Jiwoo', 'Lehends']),
    team('BRO', 'OKSavingsBank BRION', '5승 10패', ['Morgan', 'HamBak', 'Clozer', 'Hype', 'Pollu']),
    team('BFX', 'BNK FEARX', '5승 10패', ['Clear', 'Raptor', 'VicLa', 'Diable', 'Kellin']),
    team('DNF', 'DN Freecs', '4승 11패', ['DuDu', 'Pyosik', 'BuLLDoG', 'Berserker', 'Life']),
  ],
};

export const matchSetupStateFixtures = {
  initial: { blueTeamId: '', redTeamId: '', seed: '73' },
  ready: { blueTeamId: 'GEN', redTeamId: 'T1', seed: '73' },
  optionsLoading: { status: 'loading' },
  optionsError: { status: 'error' },
  rosterLoading: { status: 'loading', teamId: 'GEN' },
  rosterError: { status: 'error', teamId: 'GEN' },
  sameTeam: { blueTeamId: 'GEN', redTeamId: 'GEN', seed: '73' },
  emptySeed: { blueTeamId: 'GEN', redTeamId: 'T1', seed: '' },
  invalidSeed: { blueTeamId: 'GEN', redTeamId: 'T1', seed: '잘못된 seed!' },
  generatedSeed: { blueTeamId: 'GEN', redTeamId: 'T1', seed: 'LM-A7K2Q9' },
} as const;
