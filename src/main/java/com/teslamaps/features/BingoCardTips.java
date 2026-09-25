package com.teslamaps.features;

// Ported from SkyHanni's BingoCardTips.kt + BingoCardReader.kt, tips from SkyHanni-REPO constants/Bingo.json
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Bingo Card: goals tinted by difficulty, tooltip gets the difficulty, a guide and "Found by". */
public final class BingoCardTips {

    private static final String URL = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/Bingo.json";

    private record Tip(String difficulty, List<String> guide, String found) {}
    private record Goal(Tip tip, boolean community) {}

    private static volatile Map<String, Tip> tips = Map.of();
    private static volatile boolean fetched;
    private static Feature feature;
    private static Screen lastScreen;
    private static boolean inCard;
    private static int timer;
    private static Map<Integer, Integer> colors = Map.of();
    private static Map<ItemStack, Goal> goals = new IdentityHashMap<>();

    private BingoCardTips() {}

    public static void register() {
        feature = Feature.of("BingoCardTips").section("Inventory").when(() -> TeslaMapsConfig.get().bingoCardTips)
                .tick(BingoCardTips::tick);
        RepoCache.onReload("BingoCardTips", () -> { fetched = false; fetch(); });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (inCard && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void fetch() {
        if (fetched) return;
        fetched = true;
        Thread t = new Thread(() -> {
            try {
                JsonElement json = RepoCache.fetch("sh-Bingo", URL, 10);
                if (!(json instanceof JsonObject o) || !(o.get("bingo_tips") instanceof JsonObject all)) return;
                Map<String, Tip> out = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : all.entrySet()) {
                    if (!(e.getValue() instanceof JsonObject g)) continue;
                    List<String> guide = new ArrayList<>();
                    if (g.get("guide") instanceof JsonArray arr) for (JsonElement l : arr) guide.add(l.getAsString());
                    String found = g.has("found") && !g.get("found").isJsonNull() ? g.get("found").getAsString() : null;
                    String diff = g.has("difficulty") ? g.get("difficulty").getAsString() : "";
                    out.put(e.getKey(), new Tip(diff, guide, found));
                }
                tips = out;
            } catch (Exception ignored) {
            }
        }, "teslamaps-bingo");
        t.setDaemon(true);
        t.start();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            inCard = mc.screen instanceof AbstractContainerScreen<?>
                    && StringUtil.stripTrim(mc.screen.getTitle().getString()).equals("Bingo Card");
            colors = Map.of();
            goals = new IdentityHashMap<>();
            timer = 0;
        }
        if (!inCard || timer-- > 0) return;
        timer = 10;
        fetch();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        Map<Integer, Integer> col = new HashMap<>();
        Map<ItemStack, Goal> gs = new IdentityHashMap<>();
        boolean hideDone = TeslaMapsConfig.get().bingoHideDoneDifficulty;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(st);
            boolean personal = false, community = false, done = false;
            for (String l : lore) {
                if (l.endsWith("Personal Goal")) personal = true;
                else if (l.endsWith("Community Goal")) community = true;
                if (l.contains("GOAL REACHED")) done = true;
            }
            if (!personal && !community) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            Tip tip = community ? communityTip(name) : tips.get(name);
            gs.put(st, new Goal(tip, community));
            if (hideDone && done) continue;
            col.put(slot.index, color(tip));
        }
        colors = col;
        goals = gs;
    }

    private static Tip communityTip(String name) {
        for (Map.Entry<String, Tip> e : tips.entrySet()) {
            String key = e.getKey();
            int cut = key.indexOf(" (Community Goal)");
            if (name.startsWith(cut < 0 ? key : key.substring(0, cut))) return e.getValue();
        }
        return null;
    }

    private static int color(Tip tip) {
        if (tip == null) return 0x78AAAAAA;
        return switch (tip.difficulty().toUpperCase(java.util.Locale.ROOT)) {
            case "EASY" -> 0x7855FF55;
            case "MEDIUM" -> 0x78FFFF55;
            case "HARD" -> 0x78FF5555;
            default -> 0x78AAAAAA;
        };
    }

    private static String difficultyName(String d) {
        return switch (d.toUpperCase(java.util.Locale.ROOT)) {
            case "EASY" -> "§aEasy";
            case "MEDIUM" -> "§eMedium";
            case "HARD" -> "§cHard";
            default -> "§7" + d;
        };
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (!inCard || colors.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        Perf.begin("BingoCardTips");
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            Integer c = colors.get(slot.index);
            if (c == null) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, c);
        }
        Perf.end();
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        Goal goal = goals.get(stack);
        if (goal == null || goal.tip() == null || lines.isEmpty()) return;
        if (StringUtil.stripTrim(lines.get(0).getString()).startsWith("Row #")) return;
        Tip tip = goal.tip();
        lines.set(0, lines.get(0).copy().append(Component.literal(" §7(" + difficultyName(tip.difficulty()) + "§7)")));
        int reward = -1;
        for (int i = 0; i < lines.size(); i++) {
            String s = StringUtil.stripTrim(lines.get(i).getString());
            if (goal.community() ? s.startsWith("Contribution Rewards") : s.equals("Reward")) { reward = i; break; }
        }
        int index = reward - 2;
        if (index < 0) return;
        lines.add(++index, Component.empty());
        lines.add(++index, Component.literal("§eGuide:"));
        for (String l : tip.guide()) lines.add(++index, Component.literal(" §7" + l));
        if (tip.found() != null) lines.add(++index, Component.literal("§7Found by: §e" + tip.found()));
    }
}
