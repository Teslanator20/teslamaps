package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.LerpingBossEventAccessor;
import com.teslamaps.utils.SkyblockUtils;
import java.util.Locale;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;

public class BossBarHealth {

    public static Component onRender(LerpingBossEvent event, Component originalName) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.bossbarHealth && !c.watcherBossbarKills) return originalName;
        if (!DungeonManager.isInDungeon()) {
            if (c.bossbarHealth && c.bossbarHealthPercent && SkyblockUtils.getArea().contains("Kuudra"))
                return withPercent(event, originalName);
            return originalName;
        }
        String name = originalName.getString().replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
        if (c.watcherBossbarKills && name.equals("The Watcher") && !DungeonManager.isInBossArea()) {
            float f = ((LerpingBossEventAccessor) event).teslamaps$getTargetPercent();
            DungeonFloor fl = DungeonManager.getCurrentFloor();
            int total = 12 + (fl != null ? fl.getLevel() : 0);
            return originalName.copy().append(Component.literal(" §8- §d" + Math.round(f * total) + "/" + total));
        }
        if (!c.bossbarHealth) return originalName;
        Component interest = SadanFeatures.interestName(event);
        if (interest != null) return interest;

        float maxHealth = getMaxHealth(name);
        if (maxHealth <= 0) {
            if (c.bossbarHealthPercent && DungeonManager.isInBossArea()) return withPercent(event, originalName);
            return originalName;
        }

        float percent = ((LerpingBossEventAccessor) event).teslamaps$getTargetPercent();
        float currentHealth = Math.round(percent * maxHealth);

        return originalName.copy().append(Component.literal(
                " §r§8- §a" + formatHealth(currentHealth) + "§7/§a" + formatHealth(maxHealth) + "§c❤"));
    }

    // Ported from Devonian's BossBarHealth.kt
    private static Component withPercent(LerpingBossEvent event, Component originalName) {
        int tenths = Math.round(event.getProgress() * 1000f);
        return originalName.copy().append(Component.literal(" §8- §d" + tenths / 10 + "." + tenths % 10 + "%"));
    }

    private static float getMaxHealth(String name) {
        String floorName = DungeonManager.getFloorName();
        boolean master = floorName != null && floorName.startsWith("M");
        DungeonFloor floor = DungeonManager.getCurrentFloor();

        return switch (name) {
            case "The Watcher" -> 12f + (floor != null ? floor.getLevel() : 0);
            case "Thorn" -> master ? 6f : 4f;
            case "Maxor" -> master ? 800_000_000f : 100_000_000f;
            case "Storm" -> master ? 1_000_000_000f : 400_000_000f;
            case "Goldor" -> master ? 1_200_000_000f : 750_000_000f;
            case "Necron" -> master ? 1_400_000_000f : 1_000_000_000f;
            default -> 0f;
        };
    }

    private static String formatHealth(float health) {
        if (health >= 1_000_000_000f) {
            float h = health / 1_000_000_000f;
            if (h % 1.0f == 0f) return (int) h + "B";
            return String.format(Locale.US, "%.1fB", h);
        }
        if (health >= 1_000_000f) return (int) (health / 1_000_000f) + "M";
        if (health >= 1000f) return (int) (health / 1000f) + "k";
        return String.valueOf((int) health);
    }
}
