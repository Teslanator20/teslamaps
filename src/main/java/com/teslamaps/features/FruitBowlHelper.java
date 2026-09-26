package com.teslamaps.features;

// Ported from SkyHanni's features/misc/FruitBowlFeatures.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FruitBowlHelper {

    private static final Pattern CLICKED = Pattern.compile("^FRUIT BOWL! (.*) profile name is ([a-zA-Z]*)!$");
    private static final Pattern FOUND = Pattern.compile("^([a-zA-Z]*) has been added to the fruit bowl!$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z]+$");
    private static final List<String> SAMPLE = List.of("§e§lFruit Bowl Stats", "§7Missing names: §a2", " §8- §bLime", " §8- §bMango",
            "§7Duplicate names: §a1", " §8- §a1x §bGrapes");

    private static final Set<String> clicked = new HashSet<>();
    private static final Map<String, Integer> duplicates = new HashMap<>();
    private static Set<String> missing = Set.of();
    private static boolean inHand;
    private static String lastPlayer, lastProfile;
    private static int ticks, rescanAt = -1;
    private static List<String> lines = List.of();
    private static int width;
    private static HudEditElement hud;

    private FruitBowlHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("fruitBowlHud", "Fruit Bowl Stats").ctx(HudEditElement.HudCtx.ANY).section("Events")
                .enabled(() -> cfg().fruitBowlHelper && cfg().fruitBowlHud)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        TextHud.width(lines.isEmpty() ? SAMPLE : lines)));
        Feature.of("FruitBowlHelper").section("Events").when(() -> cfg().fruitBowlHelper)
                .tick(FruitBowlHelper::tick)
                .serverChat(FruitBowlHelper::onChat)
                .hud("fruitbowlhud", FruitBowlHelper::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Fruit Bowl Helper", () -> c.fruitBowlHelper, v -> c.fruitBowlHelper = v)
                            .desc("With the Fruit Bowl in hand: marks players you have not clicked yet and lists the missing profile names.");
                    b.toggle("  Highlight Players", () -> c.fruitBowlHighlight, v -> c.fruitBowlHighlight = v);
                    b.color("  Not Clicked Color", () -> c.fruitBowlCanColor, v -> c.fruitBowlCanColor = v);
                    b.color("  Clicked Color", () -> c.fruitBowlDoneColor, v -> c.fruitBowlDoneColor = v);
                    b.toggle("  Stats HUD", () -> c.fruitBowlHud, v -> c.fruitBowlHud = v)
                            .desc("Missing names and duplicates, move it in /tmap gui.");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 10 == 0 || ticks == rescanAt) {
            if (ticks == rescanAt) { clicked.clear(); rescanAt = -1; }
            boolean held = SkyblockUtils.inSkyblockCached() && ItemUtil.skyblockId(mc.player.getMainHandItem()).equals("FRUIT_BOWL");
            missing = held ? readMissing() : Set.of();
            inHand = held && !missing.isEmpty();
            rebuild();
        }
        if (!inHand || !cfg().fruitBowlHighlight) return;
        int can = TeslaMapsConfig.parseColor(cfg().fruitBowlCanColor) & 0xFFFFFF;
        int done = TeslaMapsConfig.parseColor(cfg().fruitBowlDoneColor) & 0xFFFFFF;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.getUUID().version() != 4) continue;
            EntityGlow.mark(p, clicked.contains(p.getGameProfile().name()) ? done : can);
        }
    }

    private static Set<String> readMissing() {
        List<String> lore = ItemUtil.loreLines(Minecraft.getInstance().player.getMainHandItem());
        Set<String> out = new LinkedHashSet<>();
        boolean after = false;
        int n = 0;
        for (String l : lore) {
            String t = l.trim();
            if (!after) {
                if (t.equals("Names missing:") || t.equals("Name missing:")) after = true;
                continue;
            }
            if (t.isEmpty() || ++n > 20) break;
            for (String part : t.split(",")) {
                String s = part.trim();
                if (NAME.matcher(s).matches()) out.add(s);
            }
        }
        return out;
    }

    private static void onChat(String text) {
        if (!inHand) return;
        String t = StringUtil.stripTrim(text);
        if (t.equals("FRUITALICIOUS! You completed your fruit bowl!")) {
            rescanAt = ticks + 10;
            return;
        }
        Matcher m = CLICKED.matcher(t);
        if (m.matches()) {
            lastPlayer = ChatText.cleanPlayerName(m.group(1));
            lastProfile = m.group(2);
            return;
        }
        if (t.equals("You already found this profile name in your bowl!")) {
            if (lastPlayer == null) return;
            String p = lastPlayer;
            lastPlayer = null;
            if (clicked.add(p)) duplicates.merge(lastProfile, 1, Integer::sum);
            rebuild();
            return;
        }
        m = FOUND.matcher(t);
        if (m.matches() && lastPlayer != null) {
            clicked.add(lastPlayer);
            lastPlayer = null;
            Set<String> next = new LinkedHashSet<>(missing);
            next.remove(m.group(1));
            missing = next;
            rebuild();
        }
    }

    private static void rebuild() {
        if (!inHand) {
            lines = List.of();
            return;
        }
        List<String> out = new ArrayList<>();
        out.add("§e§lFruit Bowl Stats");
        out.add("§7Missing names: §a" + missing.size());
        for (String s : missing) out.add(" §8- §b" + s);
        int total = 0;
        for (int v : duplicates.values()) total += v;
        out.add("§7Duplicate names: §a" + total);
        duplicates.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> out.add(" §8- §a" + e.getValue() + "x §b" + e.getKey()));
        lines = out;
        width = TextHud.width(out);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (hud == null || !cfg().fruitBowlHud || mc.options.hideGui || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
