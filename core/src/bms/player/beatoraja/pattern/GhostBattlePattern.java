package bms.player.beatoraja.pattern;

import java.util.Optional;

public class GhostBattlePattern {
    private static long lanes = -1;

    public static Optional<Long> consume() {
        if(lanes == -1) return Optional.empty();
        Long lanes = GhostBattlePattern.lanes;
        GhostBattlePattern.lanes = -1;
        return Optional.of(lanes);
    }

    public static void forceLaneOrder(long laneOrder) { GhostBattlePattern.lanes = laneOrder; }
}
