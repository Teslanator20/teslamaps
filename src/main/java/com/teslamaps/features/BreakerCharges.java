package com.teslamaps.features;

// Devonian DungeonBreakerCharges.kt (damage), Zen DungeonBreaker.kt (lore)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BreakerCharges {

    private static final String SAMPLE = "§aCharges§f: §620";
    private static final Pattern LORE = Pattern.compile("Charges: (\\d+)\\s*/\\s*(\\d+)");

    private static HudEditElement hud;
    private static String line;
    private static int lastCharges = -1;
    private static int timer;

    private BreakerCharges() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("breakerCharges", "Dungeon Breaker Charges").ctx(HudEditElement.HudCtx.DUNGEON).section("Timers")
                .enabled(() -> cfg().breakerCharges)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("BreakerCharges").section("Timers").when(() -> cfg().breakerCharges)
                .tick(BreakerCharges::tick)
                .hud("breakercharges", BreakerCharges::render)
                .config("Timers", b -> {
                    b.label("Dungeon Breaker");
                    b.toggle("Dungeon Breaker Charges", () -> cfg().breakerCharges, v -> cfg().breakerCharges = v)
                            .desc("Charges left on your Dungeon Breaker, move it in /tmap gui");
                });
    }

    private static void tick() {
        if (++timer < 5) return;
        timer = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !DungeonManager.isInDungeon()) { line = null; lastCharges = -1; return; }
        int charges = -1;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !"DUNGEONBREAKER".equals(ItemUtil.skyblockId(s))) continue;
            charges = read(s);
            break;
        }
        if (charges < 0) { line = null; lastCharges = -1; return; }
        if (charges == lastCharges && line != null) return;
        lastCharges = charges;
        String c = charges >= 15 ? "§6" : charges >= 10 ? "§a" : "§c";
        line = "§aCharges§f: " + c + charges;
    }

    private static int read(ItemStack s) {
        for (String l : ItemUtil.loreLines(s)) {
            if (!l.contains("Charges:")) continue;
            Matcher m = LORE.matcher(l);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        Integer dmg = s.get(DataComponents.DAMAGE);
        return Math.max(0, 20 - (dmg == null ? 1 : dmg) / 78);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
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
