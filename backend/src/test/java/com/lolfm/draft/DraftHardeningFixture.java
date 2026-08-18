package com.lolfm.draft;

import com.lolfm.champion.ChampionId;

final class DraftHardeningFixture {
    final DraftResourceSet resources = DraftTestSupport.RESOURCES;
    final RoleAssignmentSolver roles = new RoleAssignmentSolver(resources.champions().catalog());
    final DraftAvailability availability = new DraftAvailability(resources.champions().catalog(), roles);
    final DraftCompositionEvaluator composition = new DraftCompositionEvaluator(resources.champions().catalog(),
            resources.champions().composition(), roles);
    final DraftMatchupEvaluator matchup = new DraftMatchupEvaluator(roles, resources.champions().matchup());
    final DraftScoringPolicy policy = DraftScoringPolicy.standard();
    final PreDraftPlanner planner = new PreDraftPlanner(resources.champions().catalog(), resources.meta(),
            resources.champions().composition());
    final PickEvaluator picks = new PickEvaluator(resources.champions().catalog(), resources.meta(), matchup,
            roles, composition, availability, policy);
    final BanEvaluator bans = new BanEvaluator(resources.champions().catalog(), resources.meta(),
            resources.champions().composition(), roles, availability, composition, matchup, policy);
    final DraftCandidateGenerator candidates = new DraftCandidateGenerator(resources.champions().catalog(),
            resources.meta(), roles, composition, availability, policy);
    final ShallowDraftSearch search = new ShallowDraftSearch(planner, candidates, picks, bans, policy);

    ChampionId id(String value) { return new ChampionId(value); }
}
