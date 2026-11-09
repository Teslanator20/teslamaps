package com.teslamaps.features.hud;

// Ported from Skyblocker's skyblock/tabhud/widget/MinionWidget
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MinionWidget {

    private record Row(String text, ItemStack icon) {}

    private static final Pattern MINION = Pattern.compile("^(\\d+)x (.*) ([XVI]*) \\[(.*)]$");
    private static final Pattern HEADER = Pattern.compile("^Minions:?\\s*(.*)$");
    private static final int ROW = 12;

    private static Map<String, ItemStack> icons;
    private static List<Row> rows = List.of();
    private static List<Row> sample;
    private static int width, timer;
    private static HudEditElement hud;

    private MinionWidget() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("minionWidget", "Minions Widget")
                .enabled(() -> cfg().minionWidget)
                .size(() -> rowsWidth(shown()), () -> height(shown().size()))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, shown(), rowsWidth(shown())));
        Feature.of("MinionWidget").when(() -> cfg().minionWidget)
                .tick(MinionWidget::tick)
                .hudHiddenInInventory("minionwidget", MinionWidget::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Minions Widget");
                    b.toggle("Minions Widget", () -> c.minionWidget, v -> c.minionWidget = v)
                            .desc("On your island: every minion from the tab list with tier and status (ACTIVE green, SLOW yellow, FULL/BLOCKED red). Needs the Minions tab widget. Position in /tmap gui");
                    b.toggle("  Icons", () -> c.minionWidgetIcons, v -> c.minionWidgetIcons = v);
                    b.toggle("  Only Problems", () -> c.minionWidgetOnlyProblems, v -> c.minionWidgetOnlyProblems = v)
                            .desc("Hide ACTIVE minions");
                });
    }

    private static List<Row> shown() {
        if (!rows.isEmpty()) return rows;
        if (sample == null) sample = List.of(new Row("§3§lMinions §7(3/25)", ItemStack.EMPTY),
                new Row("§f2x Snow XI: §aACTIVE", icon("Snow")), new Row("§f1x Clay XI: §eSLOW", icon("Clay")),
                new Row("§f1x Cobblestone XII: §cFULL", icon("Cobblestone")));
        return sample;
    }

    private static ItemStack icon(String name) {
        if (icons == null) {
            Map<String, Item> m = new HashMap<>();
            m.put("Blaze", Items.BLAZE_ROD); m.put("Cave Spider", Items.SPIDER_EYE); m.put("Creeper", Items.GUNPOWDER);
            m.put("Enderman", Items.ENDER_PEARL); m.put("Ghast", Items.GHAST_TEAR); m.put("Magma Cube", Items.MAGMA_CREAM);
            m.put("Skeleton", Items.BONE); m.put("Slime", Items.SLIME_BALL); m.put("Spider", Items.STRING);
            m.put("Zombie", Items.ROTTEN_FLESH); m.put("Cactus", Items.CACTUS); m.put("Carrot", Items.CARROT);
            m.put("Chicken", Items.CHICKEN); m.put("Cocoa Beans", Items.COCOA_BEANS); m.put("Cow", Items.BEEF);
            m.put("Melon", Items.MELON_SLICE); m.put("Mushroom", Items.RED_MUSHROOM); m.put("Nether Wart", Items.NETHER_WART);
            m.put("Pig", Items.PORKCHOP); m.put("Potato", Items.POTATO); m.put("Pumpkin", Items.PUMPKIN);
            m.put("Rabbit", Items.RABBIT); m.put("Sheep", Items.WOOL.white()); m.put("Sugar Cane", Items.SUGAR_CANE);
            m.put("Wheat", Items.WHEAT); m.put("Clay", Items.CLAY); m.put("Fishing", Items.FISHING_ROD);
            m.put("Lily Pad", Items.LILY_PAD); m.put("Coal", Items.COAL); m.put("Cobblestone", Items.COBBLESTONE);
            m.put("Diamond", Items.DIAMOND); m.put("Emerald", Items.EMERALD); m.put("End Stone", Items.END_STONE);
            m.put("Glowstone", Items.GLOWSTONE_DUST); m.put("Gold", Items.GOLD_INGOT); m.put("Gravel", Items.GRAVEL);
            m.put("Hard Stone", Items.STONE); m.put("Ice", Items.ICE); m.put("Iron", Items.IRON_INGOT);
            m.put("Lapis", Items.LAPIS_LAZULI); m.put("Mithril", Items.PRISMARINE_CRYSTALS); m.put("Mycelium", Items.MYCELIUM);
            m.put("Obsidian", Items.OBSIDIAN); m.put("Quartz", Items.QUARTZ); m.put("Red Sand", Items.RED_SAND);
            m.put("Redstone", Items.REDSTONE); m.put("Sand", Items.SAND); m.put("Snow", Items.SNOWBALL);
            m.put("Inferno", Items.BLAZE_SPAWN_EGG); m.put("Revenant", Items.ZOMBIE_SPAWN_EGG); m.put("Tarantula", Items.SPIDER_SPAWN_EGG);
            m.put("Vampire", Items.REDSTONE); m.put("Voidling", Items.ENDERMAN_SPAWN_EGG); m.put("Acacia", Items.ACACIA_LOG);
            m.put("Birch", Items.BIRCH_LOG); m.put("Dark Oak", Items.DARK_OAK_LOG); m.put("Flower", Items.POPPY);
            m.put("Jungle", Items.JUNGLE_LOG); m.put("Oak", Items.OAK_LOG); m.put("Spruce", Items.SPRUCE_LOG);
            Map<String, ItemStack> s = new HashMap<>();
            for (Map.Entry<String, Item> e : m.entrySet()) s.put(e.getKey(), new ItemStack(e.getValue()));
            icons = s;
        }
        return icons.getOrDefault(name, ItemStack.EMPTY);
    }

    private static void tick() {
        if (timer++ % 20 != 0) return;
        if (!SkyblockUtils.isInArea("Private Island")) {
            rows = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        List<String> tab = TabListUtils.getOrderedLines();
        List<Row> out = new ArrayList<>();
        boolean in = false;
        for (String raw : tab) {
            String l = StringUtil.stripTrim(raw);
            if (!in) {
                Matcher h = HEADER.matcher(l);
                if (h.matches()) {
                    in = true;
                    out.add(new Row("§3§lMinions" + (h.group(1).isEmpty() ? "" : " §7" + h.group(1)), ItemStack.EMPTY));
                }
                continue;
            }
            if (l.isEmpty()) break;
            if (l.startsWith("...")) {
                out.add(new Row("§7" + l, ItemStack.EMPTY));
                continue;
            }
            Matcher m = MINION.matcher(l);
            if (!m.matches()) break;
            String status = m.group(4);
            if (c.minionWidgetOnlyProblems && status.equals("ACTIVE")) continue;
            String col = status.equals("ACTIVE") ? "§a" : status.equals("SLOW") ? "§e" : "§c";
            out.add(new Row("§f" + m.group(1) + "x " + m.group(2) + " " + m.group(3) + ": " + col + status,
                    c.minionWidgetIcons ? icon(m.group(2)) : ItemStack.EMPTY));
        }
        rows = out;
        width = rowsWidth(out);
    }

    private static boolean hasIcons(List<Row> rs) {
        for (Row r : rs) if (!r.icon.isEmpty()) return true;
        return false;
    }

    private static int rowsWidth(List<Row> rs) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (Row r : rs) w = Math.max(w, mc.font.width(r.text));
        return w + 8 + (hasIcons(rs) ? ROW + 2 : 0);
    }

    private static int height(int n) {
        return n * ROW + 4;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<Row> rs, int w) {
        if (rs.isEmpty()) return;
        boolean ic = hasIcons(rs);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, height(rs.size()));
        for (int i = 0; i < rs.size(); i++) {
            Row r = rs.get(i);
            int ry = 2 + i * ROW;
            int tx = 4;
            if (ic) {
                if (!r.icon.isEmpty()) {
                    g.pose().pushMatrix();
                    g.pose().translate(4, ry);
                    g.pose().scale(0.75f, 0.75f);
                    g.item(r.icon, 0, 0);
                    g.pose().popMatrix();
                }
                tx += ROW + 2;
            }
            g.text(mc.font, r.text, tx, ry + 2, 0xFFFFFFFF, true);
        }
        g.pose().popMatrix();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || rows.isEmpty()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), rows, width);
    }
}
