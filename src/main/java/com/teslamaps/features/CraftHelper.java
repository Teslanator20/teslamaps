package com.teslamaps.features;

// Ported from Skysoft's CraftingPlanBuilder.kt (tree walk), SkyOcean's crafthelper for the HUD idea
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.CraftCost;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Recipe tree for one target item, every ingredient checked against inventory, sacks and storage. */
public final class CraftHelper {

    private static final String SECTION = "Inventory";
    private static final int ROW = 10, MAX_DEPTH = 8, MAX_LINES = 40;
    private static final List<String> SAMPLE = List.of("§6Craft: §fEnchanted Diamond Block §7x1",
            "§c✖ §fEnchanted Diamond Block §70/1", "§7└ §c✖ §fEnchanted Diamond §7120/160",
            "§7  └ §a✔ §fDiamond §76400/6400");

    private static volatile List<String> lines = List.of();
    private static int linesWidth;
    private static int timer;
    private static boolean wasDone;
    private static String doneFor = "";
    private static HudEditElement el;

    private CraftHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("craftHelper", "Craft Helper").section(SECTION)
                .enabled(() -> cfg().craftHelper)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * ROW)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("CraftHelper").section(SECTION).when(() -> cfg().craftHelper)
                .tick(CraftHelper::tick)
                .hudForeground("crafthelper", CraftHelper::render)
                .config(SECTION, b -> {
                    b.label("Craft Helper");
                    b.toggle("Craft Helper", () -> cfg().craftHelper, v -> cfg().craftHelper = v)
                            .desc("Recipe tree for a target item with progress from inventory, sacks and storage. /tmap craft <item> [amount]");
                    b.toggle("  Count Sacks", () -> cfg().craftHelperSacks, v -> cfg().craftHelperSacks = v);
                    b.toggle("  Count Storage", () -> cfg().craftHelperStorage, v -> cfg().craftHelperStorage = v);
                    b.toggle("  Done Message", () -> cfg().craftHelperDoneMessage, v -> cfg().craftHelperDoneMessage = v);
                    b.button("  Clear Target", CraftHelper::clear);
                    b.info("Sacks count once you opened them (item search index on); position in /tmap gui");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("craft")
                        .executes(c -> status(c.getSource()))
                        .then(ClientCommands.literal("clear").executes(c -> {
                            clear();
                            c.getSource().sendFeedback(Component.literal("§7Craft target cleared"));
                            return 1;
                        }))
                        .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                                .suggests((c, sb) -> {
                                    String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
                                    if (typed.length() < 2) return sb.buildFuture();
                                    int n = 0;
                                    for (String name : SkyblockItems.allNames()) {
                                        String plain = com.teslamaps.utils.StringUtil.stripTrim(name);
                                        String low = plain.toLowerCase(Locale.ROOT);
                                        if (low.startsWith(typed) && !low.equals(typed) && n++ < 30) sb.suggest(plain);
                                    }
                                    return sb.buildFuture();
                                })
                                .executes(c -> set(c.getSource(), StringArgumentType.getString(c, "item")))))));
    }

    private static void clear() {
        cfg().craftHelperTarget = "";
        lines = List.of();
        TeslaMapsConfig.save();
    }

    private static int status(FabricClientCommandSource src) {
        String t = cfg().craftHelperTarget;
        src.sendFeedback(Component.literal(t.isEmpty() ? "§7No craft target. /tmap craft <item> [amount]"
                : "§7Craft target: §f" + display(t) + " §7x" + cfg().craftHelperAmount));
        return 1;
    }

    private static int set(FabricClientCommandSource src, String arg) {
        String text = arg.trim();
        int amount = 1;
        int sp = text.lastIndexOf(' ');
        if (sp > 0) {
            try {
                amount = Math.max(1, Integer.parseInt(text.substring(sp + 1).replace(",", "")));
                text = text.substring(0, sp).trim();
            } catch (NumberFormatException ignored) {}
        }
        String id = SkyblockItems.idForName(text);
        if (id == null) id = text.toUpperCase(Locale.ROOT).replace(' ', '_');
        cfg().craftHelperTarget = id;
        cfg().craftHelperAmount = amount;
        TeslaMapsConfig.save();
        timer = 0;
        wasDone = false;
        src.sendFeedback(Component.literal("§7Craft target: §f" + display(id) + " §7x" + amount
                + (cfg().craftHelper ? "" : " §c(Craft Helper is off in the settings)")));
        return 1;
    }

    private static String display(String id) {
        String n = SkyblockItems.coloredName(id);
        return n != null ? n : "§f" + id;
    }

    private static void tick() {
        String target = cfg().craftHelperTarget;
        if (target == null || target.isEmpty()) {
            if (!lines.isEmpty()) lines = List.of();
            return;
        }
        if (timer-- > 0) return;
        timer = 20;
        Map<String, Long> have = available();
        Builder b = new Builder(have);
        int amount = Math.max(1, cfg().craftHelperAmount);
        b.out.add("§6Craft: " + display(target) + " §7x" + amount);
        b.append(target, amount, 0, "", true, new HashSet<>());
        if (b.loading) b.out.add("§8loading recipes...");
        List<String> out = b.out.size() > MAX_LINES ? new ArrayList<>(b.out.subList(0, MAX_LINES)) : b.out;
        if (b.out.size() > MAX_LINES) out.add("§8... " + (b.out.size() - MAX_LINES) + " more");
        linesWidth = width(out);
        lines = out;

        boolean done = !b.loading && b.unmet == 0;
        String key = target + "x" + amount;
        if (!key.equals(doneFor)) {
            doneFor = key;
            wasDone = done;
        } else if (done && !wasDone) {
            wasDone = true;
            Minecraft mc = Minecraft.getInstance();
            if (cfg().craftHelperDoneMessage && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §aCraft Helper: you have everything for "
                        + display(target) + " §7x" + amount));
            }
        } else if (!done) {
            wasDone = false;
        }
    }

    private static Map<String, Long> available() {
        Map<String, Long> out = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) add(out, inv.getItem(i));
        }
        if (cfg().craftHelperStorage) {
            for (int i = 1; i <= 9; i++) addAll(out, StorageCache.items("epage_" + i));
            for (int i = 1; i <= 18; i++) addAll(out, StorageCache.items("backpack_" + i));
        }
        if (cfg().craftHelperSacks) {
            for (Map.Entry<String, Integer> e : ItemIndex.sackAmounts().entrySet()) {
                String id = SkyblockItems.idForName(e.getKey());
                if (id != null) out.merge(id, (long) e.getValue(), Long::sum);
            }
        }
        return out;
    }

    private static void addAll(Map<String, Long> out, Map<Integer, ItemStack> items) {
        if (items == null) return;
        for (ItemStack st : items.values()) add(out, st);
    }

    private static void add(Map<String, Long> out, ItemStack st) {
        if (st == null || st.isEmpty()) return;
        String id = ItemUtil.skyblockId(st);
        if (!id.isEmpty()) out.merge(id, (long) st.getCount(), Long::sum);
    }

    private static final class Builder {
        final Map<String, Long> have;
        final List<String> out = new ArrayList<>();
        boolean loading;
        int unmet;

        Builder(Map<String, Long> have) {
            this.have = have;
        }

        void append(String neuId, long required, int depth, String indent, boolean last, Set<String> ancestors) {
            if (required <= 0) return;
            String id = neuId.replace('-', ':');
            long owned = have.getOrDefault(id, 0L);
            long used = Math.min(owned, required);
            have.put(id, owned - used);
            long missing = required - used;
            String branch = depth == 0 ? "" : indent + (last ? "└ " : "├ ");
            out.add("§7" + branch + (missing == 0 ? "§a✔ " : "§c✖ ") + display(id) + " §7" + fmt(used) + "/" + fmt(required));
            if (missing == 0) return;
            if (depth >= MAX_DEPTH || ancestors.contains(neuId)) {
                unmet++;
                return;
            }
            List<CraftCost.Recipe> recipes = CraftCost.recipesOrNull(neuId);
            if (recipes == null) {
                loading = true;
                unmet++;
                return;
            }
            if (recipes.isEmpty()) {
                unmet++;
                return;
            }
            CraftCost.Recipe r = recipes.get(0);
            long crafts = (missing - 1) / Math.max(1, r.count()) + 1;
            Set<String> next = new HashSet<>(ancestors);
            next.add(neuId);
            String childIndent = depth == 0 ? "" : indent + (last ? "  " : "│ ");
            List<Map.Entry<String, Integer>> ings = new ArrayList<>(r.ingredients().entrySet());
            for (int i = 0; i < ings.size(); i++) {
                Map.Entry<String, Integer> e = ings.get(i);
                append(e.getKey(), e.getValue() * crafts, depth + 1, childIndent, i == ings.size() - 1, next);
            }
        }
    }

    private static String fmt(long n) {
        return n >= 100_000 ? com.teslamaps.utils.NumberUtil.formatSuffixed(n) : String.valueOf(n);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, el.x(), el.y(), el.scale(), l, linesWidth);
    }

    private static int width(List<String> l) {
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : l) w = Math.max(w, font.width(s));
        return w;
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * ROW + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * ROW, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
