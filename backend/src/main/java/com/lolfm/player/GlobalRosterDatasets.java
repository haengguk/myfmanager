package com.lolfm.player;

import java.util.List;

/** Exact user-authored August snapshots. Changing a dataset is an explicit resource update. */
final class GlobalRosterDatasets {
    private GlobalRosterDatasets() { }

    static final List<Dataset> OVERSEAS = List.of(
            new Dataset(
                    new PlayerResourceSpec("LPL", 12, "lpl-player-ratings-2026-08-23-v1", "2026-08-23T00:00:00+08:00",
                        "39b8baafb741f00abaf8110f884a4c8696676114163feebf9cc6b4c5a27473ca", "2026-08-22"),
                    new PlayerResourceSpec("LPL", 12, "lpl-player-identities-2026-08-23-v1", "2026-08-23",
                        "c544698f0a8b4aa713d6b59151b7bc1c495ae7f200eaa8c8d8e9785515586ff8", null),
                    new PlayerResourceSpec("LPL", 12, "lpl-champion-proficiency-2026-08-23-v1", "2026-08-23",
                        "7cc6d019f74a2469d8c1cb6d1785e373b8e466b381e2c4e9566d82a155f3cec1", null),
                    new PlayerResourceSpec("LPL", 12, "lpl-player-career-contract-honors-2026-08-24-v1", "2026-08-24",
                        "5ef6358a1bda4100388d60e36e68e746dab3aa770b96fd8587b00792ab78f2dd", null)),
            new Dataset(
                    new PlayerResourceSpec("LEC", 10, "lec-player-ratings-2026-08-23-v1", "2026-08-23T00:00:00+02:00",
                        "fe1ef31abafdfc13d26b94c1688e5e2bacff19b84af8a140288ce7a264e9ae8a", "2026-08-22"),
                    new PlayerResourceSpec("LEC", 10, "lec-player-identities-2026-08-23-v1", "2026-08-23",
                        "9d42948ae69821512f8ddca929049e10a1def46a7b8ba7748c1d8bfff11ce310", null),
                    new PlayerResourceSpec("LEC", 10, "lec-champion-proficiency-2026-08-23-v1", "2026-08-23",
                        "6a113b70829da211a1c1c5f106a7b4f7ded4854096e53e2438e42e5125f768f1", null),
                    new PlayerResourceSpec("LEC", 10, "lec-player-career-contract-honors-2026-08-24-v1", "2026-08-24",
                        "af1c4303d12f9a87794991278154a37274ccc86901bb057fabc659dbcdfdabaf", null)),
            new Dataset(
                    new PlayerResourceSpec("LCS", 8, "lcs-player-ratings-2026-08-23-v1", "2026-08-23T00:00:00-07:00",
                        "368806cf2443d73144f68af0e3299d4b666f44570c00c39d51dd38926725ed2f", "2026-08-22"),
                    new PlayerResourceSpec("LCS", 8, "lcs-player-identities-2026-08-23-v1", "2026-08-23",
                        "061007dc39cbe469edaf477b42bfb9272338893bd089591770a0e9078f80a0b1", null),
                    new PlayerResourceSpec("LCS", 8, "lcs-champion-proficiency-2026-08-23-v1", "2026-08-23",
                        "e5875410e4e6f868285f0bcfe3edc03389f87160290db1649ee0d39c790bea60", null),
                    new PlayerResourceSpec("LCS", 8, "lcs-player-career-contract-honors-2026-08-24-v1", "2026-08-24",
                        "281adcae70cff232fec7ee6a49f8e70da1f472fc738e3563864b3f79a47d553d", null)),
            new Dataset(
                    new PlayerResourceSpec("LCP", 8, "lcp-player-ratings-2026-08-23-v1", "2026-08-23T00:00:00+08:00",
                        "62683c40374fc5edce090066e3126ce50b18cdb0136d7c9fa1591bff9de1d85d", "2026-08-22"),
                    new PlayerResourceSpec("LCP", 8, "lcp-player-identities-2026-08-23-v1", "2026-08-23",
                        "66d8b33c2a23268a85fe14f2eed9ecf52da30dc6760d40b96f0be67b0732a41f", null),
                    new PlayerResourceSpec("LCP", 8, "lcp-champion-proficiency-2026-08-23-v1", "2026-08-23",
                        "78ed2309ee49000b13deb7b66fe8f233efc91f7e0033da162bed4ac4ff53e697", null),
                    new PlayerResourceSpec("LCP", 8, "lcp-player-career-contract-honors-2026-08-24-v1", "2026-08-24",
                        "09aacdbe8513f1a8a61f0ccdfd712e114d90717303b6774ae868d77e800eb62b", null)),
            new Dataset(
                    new PlayerResourceSpec("CBLOL", 8, "cblol-player-ratings-2026-08-23-v1", "2026-08-23T00:00:00-03:00",
                        "be65c96cf3741bb40903fd6656e2d91ded46e890527db78aa7077343de2c5ef5", "2026-08-22"),
                    new PlayerResourceSpec("CBLOL", 8, "cblol-player-identities-2026-08-23-v1", "2026-08-23",
                        "b03f109e358328c95f39ad172417ea43368d156f249db6916af0cd2d6ad15d5b", null),
                    new PlayerResourceSpec("CBLOL", 8, "cblol-champion-proficiency-2026-08-23-v1", "2026-08-23",
                        "b1890643eb2f1c9b5526116f29ef4f9e2f1943639bdb0e35a8155bb2f91a0709", null),
                    new PlayerResourceSpec("CBLOL", 8, "cblol-player-career-contract-honors-2026-08-24-v1", "2026-08-24",
                        "f4442f0ae58d4531cdb8958007407bf5c1f20a2c09f590b01642c6c5271ebce5", null)));

    record Dataset(PlayerResourceSpec ratings, PlayerResourceSpec identities,
                   PlayerResourceSpec proficiencies, PlayerResourceSpec career) { }
}
