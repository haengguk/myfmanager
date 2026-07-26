package com.lolfm.simulator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SideOrientationVerdictEvaluator {
    List<SideOrientationCellStatistics> classify(
            List<SideOrientationCellStatistics> cells,
            Map<String, CellEvidence> evidenceByCell
    ) {
        List<SideOrientationCellStatistics> adjusted = applyHolmByGroup(cells);
        Map<String, SideOrientationCellStatistics> off = new HashMap<>();
        for (SideOrientationCellStatistics cell : adjusted) {
            if ("SECONDARY".equals(cell.auditGroup()) && "CHAMPION_OFF".equals(cell.mode())) {
                off.put(secondaryPairKey(cell), cell);
            }
        }
        List<SideOrientationCellStatistics> result = new ArrayList<>();
        for (SideOrientationCellStatistics cell : adjusted) {
            CellEvidence evidence = evidenceByCell.getOrDefault(
                    cellKey(cell), new CellEvidence(false, false));
            String classification;
            if ("PRIMARY".equals(cell.auditGroup())) {
                classification = primary(cell, evidence.structuralEvidence());
            } else if ("CHAMPION_ON".equals(cell.mode())) {
                SideOrientationCellStatistics baseline = off.get(secondaryPairKey(cell));
                boolean added = baseline != null
                        && cell.orientationDifference() - baseline.orientationDifference() >= 0.015
                        && cell.holmAdjustedPValue() < 0.01
                        && evidence.championApplicationSkew();
                classification = added ? "CHAMPION_POWER_ADDED_SIDE_BIAS"
                        : secondary(cell, evidence.structuralEvidence());
            } else {
                classification = secondary(cell, evidence.structuralEvidence());
            }
            result.add(cell.withAdjusted(cell.holmAdjustedPValue(), classification));
        }
        return List.copyOf(result);
    }

    String verdict(List<SideOrientationCellStatistics> cells, int integrityErrors) {
        if (integrityErrors > 0) return "BLOCKED_BY_SIDE_ORIENTATION_INTEGRITY";
        boolean review = cells.stream().anyMatch(cell ->
                cell.classification().equals("CONFIRMED_SIDE_BIAS")
                        || cell.classification().equals("REVIEW_SIDE_SKEW")
                        || cell.classification().equals("CHAMPION_POWER_ADDED_SIDE_BIAS"));
        return review ? "REVIEW_SIDE_ORIENTATION_ROOT_CAUSE" : "READY_FOR_PHASE_13C";
    }

    private List<SideOrientationCellStatistics> applyHolmByGroup(
            List<SideOrientationCellStatistics> cells
    ) {
        List<SideOrientationCellStatistics> result = new ArrayList<>(cells);
        for (String group : List.of("PRIMARY", "SECONDARY")) {
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < cells.size(); i++) {
                if (group.equals(cells.get(i).auditGroup())) indexes.add(i);
            }
            double[] raw = indexes.stream().mapToDouble(i -> cells.get(i).rawPValue()).toArray();
            double[] adjusted = SideOrientationStatistics.holm(raw);
            for (int i = 0; i < indexes.size(); i++) {
                int index = indexes.get(i);
                result.set(index, cells.get(index).withAdjusted(adjusted[i], "UNCLASSIFIED"));
            }
        }
        return result;
    }

    private String primary(SideOrientationCellStatistics cell, boolean structuralEvidence) {
        double blueAdvantage = Math.abs(cell.blueWinRate() - 0.5);
        boolean excludesFifty = cell.blueWilsonHigh() < 0.5 || cell.blueWilsonLow() > 0.5;
        if (blueAdvantage >= 0.02 && excludesFifty
                && cell.holmAdjustedPValue() < 0.01 && structuralEvidence) {
            return "CONFIRMED_SIDE_BIAS";
        }
        if (cell.effectSizePercentagePoint() >= 1.0 && cell.holmAdjustedPValue() < 0.05) {
            return "REVIEW_SIDE_SKEW";
        }
        return "LIKELY_SAMPLING_NOISE";
    }

    private String secondary(SideOrientationCellStatistics cell, boolean structuralEvidence) {
        if (cell.effectSizePercentagePoint() >= 1.0
                && cell.holmAdjustedPValue() < 0.05 && structuralEvidence) {
            return "REVIEW_SIDE_SKEW";
        }
        return "NO_ADDED_SIDE_BIAS";
    }

    static boolean structuralEvidence(List<SideOrientationMatchRow> rows) {
        if (rows == null || rows.isEmpty()) return false;
        for (SideOrientationResolver resolver : SideOrientationResolver.values()) {
            long blueEvaluations = 0, redEvaluations = 0, blueAttempts = 0, redAttempts = 0;
            for (SideOrientationMatchRow row : rows) {
                var blue = row.funnel().get(resolver).get(TeamSide.BLUE);
                var red = row.funnel().get(resolver).get(TeamSide.RED);
                blueEvaluations += blue.evaluations();
                redEvaluations += red.evaluations();
                blueAttempts += blue.actualAttempts();
                redAttempts += red.actualAttempts();
            }
            if (blueEvaluations > 0 && redEvaluations > 0) {
                double blueRate = blueAttempts / (double) blueEvaluations;
                double redRate = redAttempts / (double) redEvaluations;
                if (Math.abs(blueRate - redRate) >= 0.02) return true;
            }
        }
        return false;
    }

    static boolean championApplicationSkew(List<SideOrientationMatchRow> rows) {
        long blue = rows.stream().mapToLong(SideOrientationMatchRow::blueChampionPowerApplications).sum();
        long red = rows.stream().mapToLong(SideOrientationMatchRow::redChampionPowerApplications).sum();
        long total = blue + red;
        return total > 0 && Math.abs(blue - red) / (double) total >= 0.005;
    }

    static String cellKey(SideOrientationCellStatistics cell) {
        return String.join("|", cell.auditGroup(), cell.fixture(), cell.mode(), cell.skillProfile());
    }

    static String cellKey(SideOrientationMatchRow row) {
        return String.join("|", row.auditGroup(), row.fixtureId(), row.mode(), row.skillProfile());
    }

    private String secondaryPairKey(SideOrientationCellStatistics cell) {
        return cell.fixture() + "|" + cell.skillProfile();
    }

    record CellEvidence(boolean structuralEvidence, boolean championApplicationSkew) {
    }
}
