package com.teslamaps.features.crimson;

// After GanKura's CrimsonDropHandler / CrimsonRareDrop / CrimsonLootTrackerHud
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CrimsonDrops {

    private record Drop(String name, String id, String boss) {}

    // longest names first so "Hot Kuudra Key" wins over "Kuudra Key"
    private static final List<Drop> DROPS = List.of(
            new Drop("Fire Freeze Staff", "FIRE_FREEZE_STAFF", "MAGE OUTLAW"),
            new Drop("Wand of Strength", "WAND_OF_STRENGTH", "MAGE OUTLAW"),
            new Drop("Fire Fury Staff", "FIRE_FURY_STAFF", "MAGMA BOSS"),
            new Drop("Hot Kuudra Key", "KUUDRA_HOT_TIER_KEY", null),
            new Drop("Fire Veil Wand", "FIRE_VEIL_WAND", "ASHFANG"),
            new Drop("Magma Urchin", "MAGMA_URCHIN", null),
            new Drop("Flaming Fist", "FLAMING_FIST", "BARBARIAN DUKE X"),
            new Drop("Kuudra Key", "KUUDRA_TIER_KEY", null),
            new Drop("Ragnarock", "RAGNAROCK_AXE", "BLADESOUL"));
    private static final List<String> BOSSES = List.of("MAGMA BOSS", "ASHFANG", "BARBARIAN DUKE X", "BLADESOUL", "MAGE OUTLAW");
    private static final String PET_EPIC = "MAGMA_CUBE;3", PET_LEG = "MAGMA_CUBE;4";
    private static final int SCAN_TICKS = 1200;

    private static Tracker tracker;
    private static HudEditElement hud;
    private static String killedBoss;
    private static int scanLeft;
    private static final Set<UUID> seen = new HashSet<>();

    private CrimsonDrops() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("crimson_loot", "§c§lCrimson Boss Loot")
                .name(PET_EPIC, "§5Magma Cube Pet §7(Epic)")
                .name(PET_LEG, "§6Magma Cube Pet §7(Legendary)");
        hud = HudEditElement.of("crimsonLootTracker", "Crimson Boss Loot").section("Crimson Isle")
                .enabled(() -> cfg().crimsonLootTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("CrimsonDrops").section("Crimson Isle").when(() -> cfg().crimsonDropAlert || cfg().crimsonLootTracker)
                .tick(CrimsonDrops::tick)
                .serverChat(CrimsonDrops::onChat)
                .hudHiddenInInventory("crimsonloottracker", CrimsonDrops::render);
    }

    public static void resetTracker(boolean total) {
        if (tracker == null) return;
        if (total) tracker.resetTotal();
        else tracker.resetSession();
    }

    public static void toggleView() {
        if (tracker != null) tracker.toggleView();
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.endsWith(" DOWN!")) return;
        String boss = t.substring(0, t.length() - 6);
        if (!BOSSES.contains(boss)) return;
        killedBoss = boss;
        scanLeft = SCAN_TICKS;
        seen.clear();
    }

    private static void tick() {
        if (cfg().crimsonLootTracker) tracker.tick();
        if (scanLeft <= 0) return;
        scanLeft--;
        Minecraft mc = Minecraft.getInstance();
        if (scanLeft % 2 != 0 || !Crimson.here()) return;
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            if (as.isRemoved() || as.distanceToSqr(mc.player) > 30 * 30 || seen.contains(as.getUUID())) continue;
            Component name = as.getCustomName();
            String plain = StringUtil.stripTrim(name.getString());
            if (plain.equals("[Lvl 1] Magma Cube") && "MAGMA BOSS".equals(killedBoss)) {
                if (hasColor(name, ChatFormatting.GOLD)) found(as, PET_LEG, "§7[Lvl 1] §6Magma Cube");
                else if (hasColor(name, ChatFormatting.DARK_PURPLE)) found(as, PET_EPIC, "§7[Lvl 1] §5Magma Cube");
                continue;
            }
            for (Drop d : DROPS) {
                if (d.boss != null && !d.boss.equals(killedBoss)) continue;
                if (!plain.contains(d.name)) continue;
                found(as, d.id, name.getString());
                break;
            }
        }
    }

    private static void found(ArmorStand as, String key, String display) {
        seen.add(as.getUUID());
        if (cfg().crimsonLootTracker) tracker.add(key, 1);
        if (!cfg().crimsonDropAlert) return;
        long n = tracker.count(key, Tracker.View.TOTAL);
        Crimson.title("§c§lDROP!", display + (n > 0 ? " §7(#" + n + ")" : ""), null);
        Crimson.say("§eDropped " + display + "§e!");
        com.teslamaps.utils.LoudSound.play(SoundEvents.PLAYER_LEVELUP, 1f, 0.5f);
    }

    private static boolean hasColor(Component c, ChatFormatting f) {
        Integer rgb = f.getColor();
        if (rgb == null) return false;
        TextColor col = c.getStyle().getColor();
        if (col != null && col.getValue() == rgb) return true;
        for (Component s : c.getSiblings()) if (hasColor(s, f)) return true;
        return false;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().crimsonLootTracker || mc.options.hideGui || hud == null || !Crimson.here()) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
