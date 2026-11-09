package com.teslamaps.features.hunting;

// Ported from SkyHanni's FusionDisplay.kt and FusionKeybinds.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Last fused shard with its Hunting Box amount, PURE REPTILE count, and repeat/confirm keys. */
public final class FusionHelper {

    private static final Pattern FUSION =
            Pattern.compile("FUSION! You obtained(?: an?)? (?<shardName>.+) Shard(?: x(?<amount>\\d+))?!(?: NEW!)?");
    private static final List<String> SAMPLE = List.of("§6Hummingbird§7: §f42", "§b§lPURE REPTILE§7: §e3");

    private static ShardData.Shard last;
    private static int pureReptiles;
    private static List<String> lines = List.of();
    private static boolean inMachine;
    private static HudEditElement hud;
    private static long lastKey;

    private FusionHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("fusionDisplay", "Fusion Display").section("Shards")
                .enabled(() -> cfg().fusionDisplay)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * 10 + 6)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("FusionDisplay").section("Shards").when(() -> cfg().fusionDisplay)
                .tick(FusionHelper::tick)
                .serverChat(FusionHelper::onChat)
                .hudForeground("fusiondisplay", FusionHelper::render);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        String title = mc.gui.screen() instanceof AbstractContainerScreen<?> cs ? StringUtil.stripTrim(cs.getTitle().getString()) : "";
        inMachine = title.equals("Fusion Box") || title.equals("Confirm Fusion") || title.equals("Shard Fusion");
        if (!inMachine || last == null) {
            lines = List.of();
            return;
        }
        String first = "§6" + last.displayName() + "§7: §f" + ShardData.inBox(last);
        lines = pureReptiles > 0 ? List.of(first, "§b§lPURE REPTILE§7: §e" + pureReptiles) : List.of(first);
    }

    private static void onChat(String raw) {
        String text = StringUtil.stripTrim(raw);
        if (text.startsWith("PURE REPTILE")) {
            pureReptiles++;
            return;
        }
        if (!text.startsWith("FUSION!")) return;
        Matcher m = FUSION.matcher(text);
        if (!m.matches()) return;
        ShardData.Shard s = ShardData.byDisplayName(m.group("shardName"));
        if (s == null) return;
        if (s != last) {
            last = s;
            pureReptiles = 0;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null || !inMachine) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), l);
    }

    private static int width(List<String> l) {
        int w = 0;
        for (String s : l) w = Math.max(w, Minecraft.getInstance().font.width(s));
        return w + 8;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(l), l.size() * 10 + 6);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    /** Called from ContainerKeyMixin. One middle click per key press. */
    public static boolean onKey(AbstractContainerScreen<?> cs, int key) {
        TeslaMapsConfig c = cfg();
        if (!c.fusionKeybinds || key == -1 || !c.section("Shards") || LegitMode.blocksCheats()) return false;
        boolean repeat = key == c.fusionRepeatKey, confirm = key == c.fusionConfirmKey;
        if (repeat == confirm) return false;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        int slot;
        if (repeat && title.equals("Fusion Box")) slot = 47;
        else if (confirm && title.equals("Confirm Fusion")) slot = 33;
        else return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null || slot >= cs.getMenu().slots.size()) return true;
        long now = System.currentTimeMillis();
        if (now - lastKey < 150) return true;
        lastKey = now;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, 2, ContainerInput.CLONE, mc.player);
        return true;
    }
}
