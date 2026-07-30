package com.lolfm.champion;

import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ThirtyChampionRoleProfiles {
    public static final String VERSION =
            "initial-30-role-matchup-profile-candidate-v1";
    private static final Set<String> RETAINED = Set.of(
            "renekton", "jax", "lee-sin", "viego", "leblanc",
            "viktor", "lucian", "jinx", "nautilus", "lulu");

    private ThirtyChampionRoleProfiles() {
    }

    public static ChampionRoleMatchupProfileCatalog catalog() {
        return ChampionRoleMatchupProfileCatalog.diagnosticsCandidate(
                VERSION, entries().stream().map(Entry::profile).toList());
    }

    public static List<Entry> entries() {
        List<Entry> values = new ArrayList<>();
        // Existing prototype values are retained verbatim.
        add(values, "renekton", Position.TOP,
                a(5,6,16,12,12,15,12,13,5,15,15,14,10,10,8),
                s("BURST","GAP_CLOSE","SUSTAIN","DURABILITY"),
                s("RANGE_CONTROL","DISENGAGE","ANTI_TANK"),
                "Dash, stun and sustain define a durable short-range trade pattern.");
        add(values, "jax", Position.TOP,
                a(4,4,12,17,14,14,9,10,10,7,14,11,8,14,15),
                s("SUSTAINED_DAMAGE","MOBILITY","ANTI_DIVE","ANTI_TANK"),
                s("RANGE_CONTROL","POKE","SUSTAIN"),
                "Leap and counter-strike reward extended melee duels and anti-dive play.");
        add(values, "ornn", Position.TOP,
                a(7,7,9,8,5,11,18,18,9,7,20,12,14,14,12),
                s("CROWD_CONTROL","ENGAGE","DURABILITY","PICK"),
                s("MOBILITY","SUSTAIN","SUSTAINED_DAMAGE"),
                "Long crowd-control chains create engages while durability absorbs return fire.");
        add(values, "gwen", Position.TOP,
                a(6,5,12,19,15,13,4,8,12,10,11,13,7,15,20),
                s("SUSTAINED_DAMAGE","MOBILITY","ANTI_DIVE","ANTI_TANK"),
                s("CROWD_CONTROL","PICK","RANGE_CONTROL"),
                "Repeated snips and mist favor mobile extended fights into durable targets.");
        add(values, "kennen", Position.TOP,
                a(16,15,14,10,17,15,17,18,8,4,6,13,15,10,5),
                s("RANGE_CONTROL","MOBILITY","CROWD_CONTROL","ENGAGE"),
                s("SUSTAIN","DURABILITY","ANTI_TANK"),
                "Ranged marks and lightning rush enable explosive multi-target initiation.");
        add(values, "ksante", Position.TOP,
                a(5,4,11,13,13,14,17,14,15,7,19,11,16,18,12),
                s("CROWD_CONTROL","DURABILITY","PICK","ANTI_DIVE"),
                s("POKE","RANGE_CONTROL","SUSTAIN"),
                "Displacement and defensive tools isolate targets and repel dives.");

        add(values, "lee-sin", Position.JUNGLE,
                a(5,8,15,10,18,18,12,15,10,12,10,5,15,11,6),
                s("MOBILITY","GAP_CLOSE","ENGAGE","PICK"),
                s("WAVE_CONTROL","ANTI_TANK","RANGE_CONTROL"),
                "Multiple dashes and displacement produce mobile picks and flexible engages.");
        add(values, "viego", Position.JUNGLE,
                a(5,5,12,16,13,13,9,9,7,14,10,6,10,8,12),
                s("SUSTAINED_DAMAGE","SUSTAIN","MOBILITY"),
                s("POKE","WAVE_CONTROL","DISENGAGE"),
                "Sustained sword attacks and resets reward prolonged skirmishes.");
        add(values, "sejuani", Position.JUNGLE,
                a(5,5,9,7,9,14,19,18,11,7,19,6,16,17,7),
                s("CROWD_CONTROL","ENGAGE","DURABILITY","ANTI_DIVE"),
                s("POKE","RANGE_CONTROL","SUSTAINED_DAMAGE"),
                "Layered freezes and a long-range ultimate anchor durable initiation.");
        add(values, "vi", Position.JUNGLE,
                a(4,5,16,12,13,19,17,19,5,7,14,6,18,10,13),
                s("GAP_CLOSE","ENGAGE","PICK","CROWD_CONTROL"),
                s("RANGE_CONTROL","POKE","DISENGAGE"),
                "Point-and-click lockdown and a charged dash force decisive single-target access.");
        add(values, "nidalee", Position.JUNGLE,
                a(18,18,15,10,20,15,3,5,17,15,5,7,14,9,4),
                s("RANGE_CONTROL","POKE","MOBILITY","SUSTAIN"),
                s("CROWD_CONTROL","DURABILITY","ANTI_TANK"),
                "Spears, form swaps and healing create evasive poke and pursuit.");
        add(values, "maokai", Position.JUNGLE,
                a(10,12,8,6,7,13,20,19,14,11,19,8,19,18,5),
                s("CROWD_CONTROL","ENGAGE","PICK","DURABILITY"),
                s("SUSTAINED_DAMAGE","MOBILITY","ANTI_TANK"),
                "Reliable roots and zone control start fights and punish entry.");

        add(values, "leblanc", Position.MID,
                a(13,13,18,5,20,18,10,8,18,3,4,8,19,11,2),
                s("BURST","MOBILITY","GAP_CLOSE","PICK"),
                s("SUSTAIN","DURABILITY","ANTI_TANK"),
                "Distortion and chains create elusive burst picks with strong escape.");
        add(values, "viktor", Position.MID,
                a(16,15,13,16,4,2,10,5,10,4,7,19,7,13,12),
                s("RANGE_CONTROL","POKE","SUSTAINED_DAMAGE","WAVE_CONTROL"),
                s("MOBILITY","GAP_CLOSE","SUSTAIN"),
                "Long-range fields and persistent damage control waves and space.");
        add(values, "azir", Position.MID,
                a(19,16,10,20,14,11,16,12,18,3,5,18,9,16,18),
                s("RANGE_CONTROL","SUSTAINED_DAMAGE","WAVE_CONTROL","DISENGAGE"),
                s("SUSTAIN","DURABILITY","BURST"),
                "Soldiers provide exceptional reach and sustained zone control with a defensive wall.");
        add(values, "orianna", Position.MID,
                a(18,16,13,15,6,4,17,14,18,5,8,18,13,17,11),
                s("RANGE_CONTROL","CROWD_CONTROL","DISENGAGE","WAVE_CONTROL"),
                s("MOBILITY","GAP_CLOSE","SUSTAIN"),
                "Ball positioning supplies persistent range control and fight-shaping crowd control.");
        add(values, "ahri", Position.MID,
                a(14,13,15,9,19,17,14,9,17,5,6,13,18,12,4),
                s("MOBILITY","GAP_CLOSE","PICK","BURST"),
                s("ANTI_TANK","SUSTAIN","DURABILITY"),
                "Charm and repeated dashes enable safe picks and flexible repositioning.");
        add(values, "sylas", Position.MID,
                a(5,5,16,14,15,18,14,13,8,17,12,11,16,13,10),
                s("BURST","GAP_CLOSE","SUSTAIN","PICK"),
                s("RANGE_CONTROL","POKE","DISENGAGE"),
                "Chains, dashes and healing support explosive close-range skirmishes.");

        add(values, "lucian", Position.ADC,
                a(11,12,16,14,16,11,2,5,12,3,6,14,7,7,8),
                s("BURST","MOBILITY","SUSTAINED_DAMAGE"),
                s("CROWD_CONTROL","SUSTAIN","DURABILITY"),
                "Dash weaving and spell-triggered shots deliver mobile short-range bursts.");
        add(values, "jinx", Position.ADC,
                a(18,13,10,19,6,1,8,5,8,2,5,17,8,6,15),
                s("RANGE_CONTROL","SUSTAINED_DAMAGE","WAVE_CONTROL","ANTI_TANK"),
                s("GAP_CLOSE","SUSTAIN","DURABILITY"),
                "Weapon swapping provides exceptional sustained range and wave pressure.");
        add(values, "ezreal", Position.ADC,
                a(19,20,12,13,19,8,3,3,18,4,5,12,10,12,8),
                s("RANGE_CONTROL","POKE","MOBILITY","DISENGAGE"),
                s("CROWD_CONTROL","ENGAGE","DURABILITY"),
                "Skillshot reach and blink emphasize safe poke and escape.");
        add(values, "kaisa", Position.ADC,
                a(9,8,17,18,17,16,3,8,13,4,6,12,10,10,17),
                s("BURST","SUSTAINED_DAMAGE","MOBILITY","ANTI_TANK"),
                s("CROWD_CONTROL","SUSTAIN","DURABILITY"),
                "Hybrid damage, invisibility and ultimate access combine burst with sustained output.");
        add(values, "aphelios", Position.ADC,
                a(17,14,15,20,5,2,8,5,9,7,5,19,8,10,18),
                s("SUSTAINED_DAMAGE","WAVE_CONTROL","ANTI_TANK","RANGE_CONTROL"),
                s("MOBILITY","GAP_CLOSE","DURABILITY"),
                "Weapon combinations trade mobility for dense sustained damage and wave control.");
        add(values, "varus", Position.ADC,
                a(20,20,15,16,4,2,15,11,12,3,5,16,17,11,18),
                s("RANGE_CONTROL","POKE","PICK","ANTI_TANK"),
                s("MOBILITY","GAP_CLOSE","SUSTAIN"),
                "Long-range arrows and blight pressure targets before binding them.");

        add(values, "nautilus", Position.SUPPORT,
                a(5,4,10,3,5,16,20,20,7,2,18,5,19,12,3),
                s("CROWD_CONTROL","ENGAGE","PICK","DURABILITY"),
                s("SUSTAINED_DAMAGE","SUSTAIN","ANTI_TANK"),
                "Hook and layered lockdown create direct, durable initiation.");
        add(values, "lulu", Position.SUPPORT,
                a(14,11,5,4,8,2,14,4,19,8,6,8,5,20,2),
                s("DISENGAGE","ANTI_DIVE","CROWD_CONTROL","RANGE_CONTROL"),
                s("GAP_CLOSE","ANTI_TANK","ENGAGE"),
                "Polymorph, shields and growth excel at denying dives.");
        add(values, "rakan", Position.SUPPORT,
                a(8,6,10,5,20,18,18,19,17,10,9,6,16,16,3),
                s("MOBILITY","ENGAGE","CROWD_CONTROL","DISENGAGE"),
                s("ANTI_TANK","WAVE_CONTROL","SUSTAINED_DAMAGE"),
                "Long dashes and chained charm/knock-up make initiation highly mobile.");
        add(values, "braum", Position.SUPPORT,
                a(7,5,5,4,7,8,17,9,20,9,20,4,8,20,4),
                s("DISENGAGE","DURABILITY","ANTI_DIVE","CROWD_CONTROL"),
                s("SUSTAINED_DAMAGE","WAVE_CONTROL","ANTI_TANK"),
                "Shielding and passive stun protect allies and punish divers.");
        add(values, "renata-glasc", Position.SUPPORT,
                a(15,13,8,4,6,3,18,11,20,10,7,8,16,19,3),
                s("DISENGAGE","ANTI_DIVE","CROWD_CONTROL","PICK"),
                s("GAP_CLOSE","SUSTAINED_DAMAGE","ANTI_TANK"),
                "Hostile takeover and bailout reverse committed enemy engages.");
        add(values, "bard", Position.SUPPORT,
                a(14,13,9,6,18,10,15,10,17,12,7,9,15,14,4),
                s("MOBILITY","DISENGAGE","PICK","CROWD_CONTROL"),
                s("ANTI_TANK","DURABILITY","SUSTAINED_DAMAGE"),
                "Portals, stasis and roaming mobility create unconventional picks and escapes.");
        return List.copyOf(values);
    }

    private static void add(List<Entry> out, String id, Position position, int[] v,
                            List<ChampionMatchupTrait> strengths,
                            List<ChampionMatchupTrait> weaknesses, String summary) {
        EnumMap<ChampionMatchupTrait, Integer> traits =
                new EnumMap<>(ChampionMatchupTrait.class);
        for (int i = 0; i < v.length; i++) {
            traits.put(ChampionMatchupTrait.values()[i], v[i]);
        }
        ChampionRoleKey key = new ChampionRoleKey(new ChampionId(id), position);
        out.add(new Entry(new ChampionRoleMatchupProfile(key, VERSION, traits),
                strengths, weaknesses, summary,
                RETAINED.contains(id) ? Source.EXISTING_PROTOTYPE_RETAINED
                        : Source.MANUAL_KIT_ASSESSMENT, true));
    }

    private static int[] a(int... values) { return values; }
    private static List<ChampionMatchupTrait> s(String... values) {
        return java.util.Arrays.stream(values)
                .map(ChampionMatchupTrait::valueOf).toList();
    }

    public enum Source {
        MANUAL_KIT_ASSESSMENT, EXISTING_PROTOTYPE_RETAINED,
        EXISTING_PROTOTYPE_REVISED
    }

    public record Entry(ChampionRoleMatchupProfile profile,
                        List<ChampionMatchupTrait> primaryStrengthTraits,
                        List<ChampionMatchupTrait> primaryWeaknessTraits,
                        String kitInteractionSummary, Source profileSource,
                        boolean candidateOnly) {
        public Entry {
            primaryStrengthTraits = List.copyOf(primaryStrengthTraits);
            primaryWeaknessTraits = List.copyOf(primaryWeaknessTraits);
        }
    }
}
