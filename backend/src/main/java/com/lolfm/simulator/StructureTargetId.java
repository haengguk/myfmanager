package com.lolfm.simulator;

import java.util.Objects;

/** Explicit gameplay identity for one structure; display text is never consulted. */
public record StructureTargetId(
        TeamSide defendingSide,
        StructureKind kind,
        Lane lane,
        TowerTier towerTier,
        Integer nexusTurretIndex
) {
    public StructureTargetId {
        Objects.requireNonNull(defendingSide, "defendingSide");
        Objects.requireNonNull(kind, "kind");
        if (kind == StructureKind.TOWER && (lane == null || towerTier == null)) {
            throw new IllegalArgumentException("Tower target requires lane and tier");
        }
        if (kind == StructureKind.INHIBITOR && lane == null) {
            throw new IllegalArgumentException("Inhibitor target requires lane");
        }
        if (kind == StructureKind.NEXUS_TURRET
                && (nexusTurretIndex == null || nexusTurretIndex < 0 || nexusTurretIndex > 1)) {
            throw new IllegalArgumentException("Nexus turret target requires index 0 or 1");
        }
        if (kind != StructureKind.TOWER && towerTier != null
                || kind != StructureKind.TOWER && kind != StructureKind.INHIBITOR && lane != null
                || kind != StructureKind.NEXUS_TURRET && nexusTurretIndex != null) {
            throw new IllegalArgumentException("Target contains fields that do not belong to " + kind);
        }
    }

    public static StructureTargetId tower(TeamSide defending, Lane lane, TowerTier tier) {
        return new StructureTargetId(defending, StructureKind.TOWER, lane, tier, null);
    }

    public static StructureTargetId inhibitor(TeamSide defending, Lane lane) {
        return new StructureTargetId(defending, StructureKind.INHIBITOR, lane, null, null);
    }

    public static StructureTargetId nexusTurret(TeamSide defending, int index) {
        return new StructureTargetId(defending, StructureKind.NEXUS_TURRET, null, null, index);
    }

    public static StructureTargetId nexus(TeamSide defending) {
        return new StructureTargetId(defending, StructureKind.NEXUS, null, null, null);
    }

    public String stableId() {
        return switch (kind) {
            case TOWER -> defendingSide + ":TOWER:" + lane + ":" + towerTier;
            case INHIBITOR -> defendingSide + ":INHIBITOR:" + lane;
            case NEXUS_TURRET -> defendingSide + ":NEXUS_TURRET:" + nexusTurretIndex;
            case NEXUS -> defendingSide + ":NEXUS";
        };
    }

    public LateGameStructureTarget planningTarget() {
        return switch (kind) {
            case TOWER -> switch (towerTier) {
                case OUTER -> LateGameStructureTarget.OUTER;
                case INNER -> LateGameStructureTarget.INNER;
                case INHIBITOR -> LateGameStructureTarget.INHIBITOR_TOWER;
            };
            case INHIBITOR -> LateGameStructureTarget.INHIBITOR;
            case NEXUS_TURRET -> LateGameStructureTarget.NEXUS_TURRET;
            case NEXUS -> LateGameStructureTarget.NEXUS;
        };
    }
}
