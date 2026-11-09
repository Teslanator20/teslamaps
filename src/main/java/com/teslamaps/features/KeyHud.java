package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// DulkirMod KeyHud.kt / DungeonKeyDisplay.kt
public final class KeyHud {

    private static final String WITHER = "§8Wither Key §aObtained";
    private static final String BLOOD = "§cBlood Key §aObtained";

    private static HudEditElement hud;
    private static boolean wither, blood;

    private KeyHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("keyHud", "Key HUD").ctx(HudEditElement.HudCtx.DUNGEON).section("ESP")
                .enabled(() -> cfg().keyHud)
                .size(() -> Minecraft.getInstance().font.width(WITHER), () -> 19)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, true, false));
        Feature.of("KeyHud").section("ESP").when(() -> cfg().keyHud)
                .serverChat(KeyHud::onChat)
                .tick(KeyHud::tick)
                .hud("keyhud", KeyHud::render)
                .config("ESP", b -> {
                    b.label("Keys");
                    b.toggle("Key HUD", () -> cfg().keyHud, v -> cfg().keyHud = v)
                            .desc("\"Key Obtained\" while your party holds a wither or blood key, move it in /tmap gui");
                });
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String t = com.teslamaps.utils.StringUtil.strip(raw);
        if (t.contains("A Wither Key was picked up!") || t.contains("has obtained Wither Key")) wither = true;
        else if (t.contains("A Blood Key was picked up!") || t.contains("has obtained Blood Key")) blood = true;
        else if (t.contains("opened a WITHER door!") || t.contains("opened the WITHER door!")) wither = false;
        else if (t.contains("opened a BLOOD door!") || t.contains("opened the BLOOD door!")) blood = false;
    }

    private static void tick() {
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) { wither = false; blood = false; }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if ((!wither && !blood) || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), wither, blood);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, boolean w, boolean b) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int ly = 0;
        if (w) { g.text(mc.font, WITHER, 0, ly, 0xFFFFFFFF, true); ly += 10; }
        if (b) g.text(mc.font, BLOOD, 0, ly, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
