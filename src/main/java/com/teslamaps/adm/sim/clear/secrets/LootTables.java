// Ported from ADM's p3sim/clear/secrets/Loot.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;

public final class LootTables {
    private LootTables() {}

    // ---- chests
    public static final double CHEST_BLESSING = 0.40;
    public static final double CHEST_ESSENCE = 0.13;
    public static final double CHEST_ITEM = 0.01;
    public static final double SUPERBOOM_DOUBLE = 0.10;

    public static Drop chest(KRandom random) {
        double u = random.nextDouble();
        u -= CHEST_BLESSING;
        if (u < 0) return new Drop.Blessing(Blessings.pickChest(random));
        u -= CHEST_ESSENCE;
        if (u < 0) return new Drop.Essence(LootKt.ESSENCE_TYPES.get(random.nextInt(LootKt.ESSENCE_TYPES.size())));
        u -= CHEST_ITEM;
        if (u < 0) return rewardItem(random);
        return Drop.Nothing.INSTANCE;
    }

    // ---- room clears
    public static final double ROOM_CLEAR_SUPERBOOM = 0.04;
    public static final double ROOM_CLEAR_REVIVE = 0.01;

    // ---- mob kills
    public static final double KILL_SUPERBOOM = 0.019;
    public static final double KILL_REVIVE = 0.005;
    public static final double KILL_BLESSING = 0.028;

    public static Drop mobKill(KRandom random) {
        double u = random.nextDouble();
        if (u < KILL_BLESSING) return new Drop.Blessing(Blessings.pickKill(random));
        if (u < KILL_BLESSING + KILL_SUPERBOOM) return superboom(random);
        if (u < KILL_BLESSING + KILL_SUPERBOOM + KILL_REVIVE) return new Drop.Item(LootItem.REVIVE_STONE);
        return null;
    }

    public static Drop roomClear(KRandom random) {
        double u = random.nextDouble();
        if (u < ROOM_CLEAR_SUPERBOOM) return superboom(random);
        if (u < ROOM_CLEAR_SUPERBOOM + ROOM_CLEAR_REVIVE) return new Drop.Item(LootItem.REVIVE_STONE);
        return null;
    }

    public static Drop.Item rewardItem(KRandom random) {
        return random.nextDouble() * (ROOM_CLEAR_SUPERBOOM + ROOM_CLEAR_REVIVE) < ROOM_CLEAR_SUPERBOOM ? superboom(random) : new Drop.Item(LootItem.REVIVE_STONE);
    }

    private static Drop.Item superboom(KRandom random) { return new Drop.Item(LootItem.SUPERBOOM, random.nextDouble() < SUPERBOOM_DOUBLE ? 2 : 1); }

    // ---- item secrets (a thing lying on the floor)
    private static final List<Pair<LootItem, Integer>> ITEM_WEIGHTS = List.of(
            Pair.of(LootItem.HEALING_POTION, 344), Pair.of(LootItem.TRAINING_WEIGHTS, 304), Pair.of(LootItem.TRAP, 284), Pair.of(LootItem.DECOY, 261),
            Pair.of(LootItem.INFLATABLE_JERRY, 248), Pair.of(LootItem.SPIRIT_LEAP, 197), Pair.of(LootItem.REVIVE_STONE, 136), Pair.of(LootItem.DEFUSE_KIT, 48),
            Pair.of(LootItem.CANDYCOMB, 26));

    public static LootItem itemSecret(KRandom random) {
        int total = 0;
        for (Pair<LootItem, Integer> p : ITEM_WEIGHTS) total += p.second;
        int n = random.nextInt(total);
        for (Pair<LootItem, Integer> p : ITEM_WEIGHTS) { n -= p.second; if (n < 0) return p.first; }
        return ITEM_WEIGHTS.get(ITEM_WEIGHTS.size() - 1).first;
    }

    public static List<LootItem> ITEM_SECRET_ITEMS() {
        List<LootItem> out = new ArrayList<>();
        for (Pair<LootItem, Integer> p : ITEM_WEIGHTS) out.add(p.first);
        return out;
    }
}
