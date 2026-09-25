package com.teslamaps.features.theend;

// Ported from Devonian's EyesPlacedDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class EyesPlacedHud {

    private static final String SAMPLE = "§dEyes Placed§f: §d8§f/§d8";
    private static final String NONE = "§dEyes Placed§f: §e0§f/§d8";

    private static String line = null;
    private static String status = null;
    private static Object lastLevel = null;
    private static int timer;
    private static HudEditElement hud;

    private EyesPlacedHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("eyesPlaced", "Eyes Placed").section("The End").ctx(HudEditElement.HudCtx.THE_END)
                .enabled(() -> cfg().eyesPlacedHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("EyesPlacedHud").section("The End").when(() -> cfg().eyesPlacedHud)
                .tick(EyesPlacedHud::tick)
                .hudHiddenInInventory("eyesplaced", EyesPlacedHud::render)
                .config("The End", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Dragon's Nest");
                    b.toggle("Eyes Placed HUD", () -> c.eyesPlacedHud, v -> c.eyesPlacedHud = v)
                            .desc("Summoning eyes placed (n/8), or dragon spawned / egg respawning, read from the tab list. Position in /tmap gui.")
                            .tags("summoning eye", "dragon");
                    b.toggle("  Show Anywhere", () -> c.eyesPlacedAnywhere, v -> c.eyesPlacedAnywhere = v)
                            .desc("Not only inside the Dragon's Nest");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            status = null;
        }
        if (++timer % 10 != 0) return;
        if (!SkyblockUtils.isInSkyblock()) {
            line = null;
            return;
        }
        for (String raw : TabListUtils.getTabListLines()) {
            String l = StringUtil.stripTrim(raw);
            if (l.equals("Dragon spawned!")) { status = "§aDragon Spawned!"; break; }
            if (l.equals("Egg respawning!")) { status = "§dEgg Respawning!"; break; }
            if (l.startsWith("Eyes placed: ")) {
                int slash = l.indexOf('/', 13);
                if (slash < 0) continue;
                try {
                    int n = Integer.parseInt(l.substring(13, slash).trim());
                    String col = n > 6 ? "§d" : n >= 4 ? "§a" : "§e";
                    status = "§dEyes Placed§f: " + col + n + "§f/§d8";
                } catch (NumberFormatException ignored) {}
                break;
            }
        }
        boolean show = cfg().eyesPlacedAnywhere || inNest();
        line = show ? (status != null ? status : NONE) : null;
    }

    private static boolean inNest() {
        if (!EndProtector.inArea()) return false;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String l = ScoreboardUtils.cleanLine(raw);
            if (l.contains("Dragon") && l.contains("Nest")) return true;
        }
        return false;
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
