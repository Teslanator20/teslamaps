package com.teslamaps.dungeon;

// Ported from NoammAddons' MaxorsCrystals.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.DungeonTimers;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MaxorCrystals {

    private MaxorCrystals() {}

    private static final String ALERT = "§e§l⚠ §b§lCrystal §e§l⚠";
    private static final ServerTickTimer SPAWN = new ServerTickTimer();

    private static long pickupMs = 0;
    private static final Set<Integer> knownCrystals = new HashSet<>();
    private static boolean holding = false;
    private static Object lastLevel = null;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MaxorCrystals").when(() -> cfg().maxorCrystals)
                .tick(MaxorCrystals::tick)
                .serverChat(MaxorCrystals::onChat)
                .hud("maxorcrystals", MaxorCrystals::render);
        Feature.of("MaxorCrystals.off").when(() -> !cfg().maxorCrystals).tick(MaxorCrystals::reset);
    }

    private static void reset() {
        if (pickupMs == 0 && !holding && !SPAWN.active() && knownCrystals.isEmpty()) return;
        pickupMs = 0;
        knownCrystals.clear();
        holding = false;
        SPAWN.stop();
        DungeonTimers.clear("maxorcrystal");
    }

    private static void onChat(String raw) {
        String msg = StringUtil.strip(raw).trim();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (msg.endsWith(" picked up an Energy Crystal!")
                && msg.startsWith(mc.player.getName().getString() + " ")) {
            pickupMs = System.currentTimeMillis();
            knownCrystals.clear();
            for (Entity e : mc.level.entitiesForRendering()) if (e instanceof EndCrystal) knownCrystals.add(e.getId());
        }
        if (msg.equals("[BOSS] Maxor: THAT BEAM! IT HURTS! IT HURTS!!") || msg.equals("[BOSS] Maxor: YOU TRICKED ME!")) {
            SPAWN.start(34);
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }

        if (c.maxorCrystalSpawnTimer && SPAWN.active()) {
            DungeonTimers.set("maxorcrystal", "§bCrystal§f: §b" + ServerTickTimer.seconds(SPAWN.remaining()));
        } else DungeonTimers.clear("maxorcrystal");

        boolean p1 = F7Devices.phase() == 1;
        holding = false;
        if (p1 && c.maxorCrystalAlert) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (!s.isEmpty() && StringUtil.strip(s.getHoverName().getString()).trim().equalsIgnoreCase("Energy Crystal")) {
                    holding = true;
                    break;
                }
            }
        }

        if (pickupMs == 0) return;
        if (!p1 || System.currentTimeMillis() - pickupMs > 60000) { pickupMs = 0; return; }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof EndCrystal) || knownCrystals.contains(e.getId())) continue;
            if ((int) e.getY() != 224) { knownCrystals.add(e.getId()); continue; }
            double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
            if (dx * dx + dz * dz >= 25) { knownCrystals.add(e.getId()); continue; }
            placed((System.currentTimeMillis() - pickupMs) / 1000.0);
            break;
        }
    }

    private static void placed(double secs) {
        pickupMs = 0;
        knownCrystals.clear();
        TeslaMapsConfig c = cfg();
        if (!c.maxorCrystalPlaceTimer) return;
        Minecraft mc = Minecraft.getInstance();
        double pb = c.maxorCrystalPb;
        String suffix;
        if (pb <= 0 || secs < pb) {
            suffix = pb <= 0 ? " §d§l(PB)" : String.format(Locale.ROOT, " §d§l(PB) §7old: %.3fs", pb);
            c.maxorCrystalPb = secs;
            TeslaMapsConfig.save();
        } else {
            suffix = String.format(Locale.ROOT, " §7(PB: %.3fs)", pb);
        }
        mc.player.sendSystemMessage(Component.literal(
                String.format(Locale.ROOT, "§b[TeslaMaps] §aCrystal placed in §e%.3fs§a.", secs) + suffix));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!holding) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float scale = 3f;
        g.pose().pushMatrix();
        g.pose().translate(w / 2f, h * 0.2f);
        g.pose().scale(scale, scale);
        g.text(mc.font, ALERT, -mc.font.width(ALERT) / 2, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
