package com.teslamaps.features;

// GanKura ArmorStackHandler / ArmorStackHud, SkyHanni ArmorStackDisplay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class ArmorStackHud {

    // Crimson, Terror, Hollow, Fervor, Aurora
    private static final char[] SYMBOLS = {'ᝐ', '⁑', '⚶', '҉', 'Ѫ'};
    private static final String[] COLORS = {"§6", "§1", "§e", "§2", "§9"};
    private static final String SAMPLE = "§6§l10ᝐ";
    private static final long RESET_MS = 2000;

    private static String line = null;
    private static long updatedAt;
    private static HudEditElement hud;

    private ArmorStackHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("armorStackHud", "Armor Stacks").section("Combat")
                .enabled(() -> cfg().armorStackHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, line != null ? line : SAMPLE));
        Feature.of("ArmorStackHud").section("Combat").when(() -> cfg().armorStackHud)
                .tick(ArmorStackHud::tick)
                .hud("armorstackhud", ArmorStackHud::render)
                .config("Combat", b -> {
                    b.label("Armor Stacks");
                    b.toggle("Armor Stack HUD", () -> cfg().armorStackHud, v -> cfg().armorStackHud = v)
                            .desc("Crimson/Terror/Hollow/Fervor/Aurora stacks from the action bar, bold = max. Move it in /tmap gui.");
                });
    }

    public static void onActionBar(Component message) {
        TeslaMapsConfig c = cfg();
        if (!c.armorStackHud || !c.section("Combat")) return;
        String legacy = legacy(message);
        StringBuilder out = null;
        for (int s = 0; s < SYMBOLS.length; s++) {
            int idx = legacy.indexOf(SYMBOLS[s]);
            if (idx < 0) continue;
            int i = idx - 1;
            if (i >= 0 && legacy.charAt(i) == ' ') i--;
            Boolean bold = null;
            StringBuilder digits = new StringBuilder();
            while (i >= 0) {
                char ch = legacy.charAt(i);
                if (Character.isDigit(ch)) {
                    digits.insert(0, ch);
                    i--;
                } else if (i >= 1 && legacy.charAt(i - 1) == '§') {
                    char code = Character.toLowerCase(ch);
                    if (bold == null && code == 'l') bold = true;
                    else if (bold == null && ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r')) bold = false;
                    i -= 2;
                } else break;
            }
            if (digits.isEmpty()) continue;
            if (bold == null) bold = boldBefore(legacy, i + 1);
            if (out == null) out = new StringBuilder();
            else out.append(' ');
            out.append(COLORS[s]).append(bold ? "§l" : "").append(digits).append(SYMBOLS[s]);
        }
        if (out == null) return;
        line = out.toString();
        updatedAt = System.currentTimeMillis();
    }

    private static boolean boldBefore(String s, int pos) {
        boolean bold = false;
        for (int i = 0; i + 1 < pos; i++) {
            if (s.charAt(i) != '§') continue;
            char code = Character.toLowerCase(s.charAt(i + 1));
            if (code == 'l') bold = true;
            else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r') bold = false;
            i++;
        }
        return bold;
    }

    // Hypixel usually sends legacy codes in the text, a styled component gets them rebuilt
    private static String legacy(Component message) {
        StringBuilder sb = new StringBuilder();
        message.visit((style, part) -> {
            if (style.isBold()) sb.append("§l");
            sb.append(part);
            if (style.isBold()) sb.append("§r");
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    private static void tick() {
        if (line != null && System.currentTimeMillis() - updatedAt > RESET_MS) line = null;
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
