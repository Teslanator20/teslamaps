package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.WitherDragons.Dragon;
import com.teslamaps.player.PlayerTracker;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DragonPriority {

    static final Dragon[] DEFAULT_ORDER = {Dragon.RED, Dragon.ORANGE, Dragon.BLUE, Dragon.PURPLE, Dragon.GREEN};
    static final Dragon[] DRAGON_LIST = {Dragon.ORANGE, Dragon.GREEN, Dragon.RED, Dragon.BLUE, Dragon.PURPLE};

    public static Dragon findPriority(List<Dragon> spawningDragons) {
        if (!TeslaMapsConfig.get().witherDragonPriority) {
            Dragon best = spawningDragons.get(0);
            for (Dragon d : spawningDragons) if (indexOf(DEFAULT_ORDER, d) < indexOf(DEFAULT_ORDER, best)) best = d;
            return best;
        }
        return sortPriority(spawningDragons);
    }

    private static Dragon sortPriority(List<Dragon> spawningDragons) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        double totalPower = WitherDragons.blessingPower * (WitherDragons.paulBuff() ? 1.25 : 1.0)
                + (WitherDragons.blessingTime > 0 ? 2.5 : 0.0);

        String playerClass = PlayerTracker.getLocalClass();
        if (playerClass == null) WitherDragons.msg("§cFailed to get dungeon class.");

        boolean hasPurple = spawningDragons.contains(Dragon.PURPLE);

        Dragon[] priorityList;
        if (totalPower >= config.witherDragonNormalPower || (hasPurple && totalPower >= config.witherDragonEasyPower)) {
            boolean bersOrMage = "Berserk".equals(playerClass) || "Mage".equals(playerClass);
            priorityList = bersOrMage ? DRAGON_LIST : reversed(DRAGON_LIST);
        } else {
            priorityList = DEFAULT_ORDER;
        }

        final Dragon[] order = priorityList;
        spawningDragons.sort(Comparator.comparingInt(d -> indexOf(order, d)));

        if (totalPower >= config.witherDragonEasyPower) {
            boolean splitTarget = hasPurple || config.witherDragonSoloDebuffAll;
            if ((config.witherDragonSoloDebuff == 1 && "Tank".equals(playerClass) && splitTarget)
                    || ("Healer".equals(playerClass) && splitTarget)) {
                spawningDragons.sort(Comparator.comparingInt((Dragon d) -> indexOf(order, d)).reversed());
            }
        }

        if (config.debugMode) {
            String dragons = spawningDragons.stream().map(d -> d.dispName).collect(Collectors.joining(", "));
            StringBuilder listed = new StringBuilder();
            for (Dragon d : order) listed.append(d.dispName.charAt(0)).append(", ");
            WitherDragons.msg("§7Priority: §6" + totalPower + " §7Class: §f" + playerClass
                    + " §7Dragons: §a" + dragons + " §7-> §c" + listed);
        }

        return spawningDragons.get(0);
    }

    static int indexOf(Dragon[] arr, Dragon dragon) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == dragon) return i;
        return -1;
    }

    static Dragon[] reversed(Dragon[] arr) {
        Dragon[] out = new Dragon[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = arr[arr.length - 1 - i];
        return out;
    }
}
