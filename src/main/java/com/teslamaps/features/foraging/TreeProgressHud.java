package com.teslamaps.features.foraging;

// Ported from SkyHanni's TreeProgressDisplay, own-tree check from Skyblocker's TreeBreakProgressHud
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Progress of the closest custom tree from its "<TYPE> TREE n%" nametag. */
public final class TreeProgressHud {

    private static final Pattern PROGRESS = Pattern.compile("(\\w+) TREE (\\d+)%");
    private static final String SAMPLE = "§aFig Tree §f45%";

    private static String line;
    private static int ticks;
    private static HudEditElement hud;

    private TreeProgressHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("treeProgressHud", "Tree Progress").ctx(HudEditElement.HudCtx.FORAGING).section("Foraging")
                .enabled(() -> cfg().treeProgressHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 4f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("TreeProgressHud").section("Foraging").when(() -> cfg().treeProgressHud)
                .tick(TreeProgressHud::tick)
                .hudHiddenInInventory("treeprogress", TreeProgressHud::render)
                .config("Foraging", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Tree Progress HUD", () -> c.treeProgressHud, v -> c.treeProgressHud = v)
                            .desc("Break progress of the closest Fig / Mangrove / Helix tree. Position in /tmap gui")
                            .tags("tree", "progress", "percent");
                    b.toggle("  Compact", () -> c.treeProgressCompact, v -> c.treeProgressCompact = v).desc("Fig 45%");
                    b.toggle("  Only With Axe In Hand", () -> c.treeProgressAxeOnly, v -> c.treeProgressAxeOnly = v);
                    b.toggle("  Only Own Trees", () -> c.treeProgressOwnOnly, v -> c.treeProgressOwnOnly = v)
                            .desc("Only trees you or a group of players are working on");
                });
    }

    private static void tick() {
        if (ticks++ % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!Foraging.onForagingIsland() || (cfg().treeProgressAxeOnly && !holdingAxe(mc))) {
            line = null;
            return;
        }
        ArmorStand best = null;
        Matcher bestM = null;
        double bd = Double.MAX_VALUE;
        List<ArmorStand> stands = EntityCache.namedArmorStands();
        for (ArmorStand as : stands) {
            String n = StringUtil.stripTrim(as.getCustomName().getString());
            if (n.indexOf('%') < 0 || !n.contains(" TREE ")) continue;
            Matcher m = PROGRESS.matcher(n);
            if (!m.find()) continue;
            double d = as.distanceToSqr(mc.player);
            if (d >= bd) continue;
            best = as;
            bestM = m;
            bd = d;
        }
        if (best == null || bd > 48 * 48 || (cfg().treeProgressOwnOnly && !own(best, stands, mc))) {
            line = null;
            return;
        }
        String t = bestM.group(1);
        String type = t.charAt(0) + t.substring(1).toLowerCase();
        line = "§a" + type + (cfg().treeProgressCompact ? " §f" : " Tree §f") + bestM.group(2) + "%";
    }

    private static boolean own(ArmorStand tree, List<ArmorStand> stands, Minecraft mc) {
        String me = mc.player.getName().getString();
        for (ArmorStand as : stands) {
            if (Math.abs(as.getX() - tree.getX()) >= 0.1 || Math.abs(as.getZ() - tree.getZ()) >= 0.1
                    || Math.abs(as.getY() - tree.getY()) >= 2) continue;
            String n = as.getCustomName().getString();
            if (n.contains(me) || n.contains(" players")) return true;
        }
        return false;
    }

    private static boolean holdingAxe(Minecraft mc) {
        ItemStack s = mc.player.getMainHandItem();
        if (s.isEmpty()) return false;
        if (s.is(ItemTags.AXES)) return true;
        List<String> lore = ItemUtil.loreLines(s);
        return !lore.isEmpty() && StringUtil.stripTrim(lore.get(lore.size() - 1)).endsWith(" AXE");
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        Minecraft mc = Minecraft.getInstance();
        if (l == null || hud == null || mc.options.hideGui) return;
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
