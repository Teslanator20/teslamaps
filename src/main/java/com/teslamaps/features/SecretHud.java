package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.scanner.SecretTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// NoammAddons Secrets.kt "Secret Hud" / Zen RoomSecrets.kt
public class SecretHud {

    private static final String SAMPLE = "§7Secrets: §c3§7/§a7";
    private static final String[] PREFIXES = {"Secrets:", "Secrets", "S:", "None"};

    private static HudEditElement hud;
    private static String line;
    private static int lastFound = -2, lastTotal = -2;
    private static String lastPrefix;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("secretHud", "Secret HUD").ctx(HudEditElement.HudCtx.DUNGEON).section("Secrets & Waypoints")
                .enabled(() -> cfg().secretHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("SecretHud").section("Secrets & Waypoints").when(() -> cfg().secretHud)
                .tick(SecretHud::tick)
                .hud("secrethud", SecretHud::render)
                .config("Secrets & Waypoints", b -> {
                    b.label("Secret HUD");
                    b.toggle("Secret HUD", () -> cfg().secretHud, v -> cfg().secretHud = v)
                            .desc("Secrets x/y of the room you stand in, move it in /tmap gui");
                    b.dropdown("  Prefix", PREFIXES, () -> cfg().secretHudPrefix, v -> cfg().secretHudPrefix = v);
                });
    }

    private static void tick() {
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) { line = null; return; }
        long now = System.currentTimeMillis();
        int found = -1, total = -1;
        if (SecretTracker.barNoneAt - SecretTracker.barAt > 1000 && now - SecretTracker.barNoneAt < 3000) {
            total = 0;
        } else if (now - SecretTracker.barAt < 3000) {
            found = SecretTracker.barFound;
            total = SecretTracker.barTotal;
        } else {
            DungeonRoom room = DungeonManager.getCurrentRoom();
            if (room == null || !room.isIdentified()) { line = null; return; }
            total = room.getSecrets();
            found = room.getFoundSecrets();
        }
        String prefix = cfg().secretHudPrefix;
        if (line != null && found == lastFound && total == lastTotal && prefix.equals(lastPrefix)) return;
        lastFound = found;
        lastTotal = total;
        lastPrefix = prefix;
        String p = "None".equals(prefix) || prefix.isEmpty() ? "" : "§7" + prefix + " ";
        if (total <= 0) line = p + "§7None";
        else if (found < 0) line = p + "§7?§7/§a" + total;
        else {
            String c = found >= total ? "§a" : found * 2 < total ? "§c" : "§e";
            line = p + c + found + "§7/§a" + total;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
