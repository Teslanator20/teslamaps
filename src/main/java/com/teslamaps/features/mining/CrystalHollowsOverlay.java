package com.teslamaps.features.mining;

// After NotEnoughUpdates' CrystalHollowOverlay; chat formats from SkyHanni's CrystalNucleusChatFilter
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.StorageCache;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Automaton and Divan parts plus the five Nucleus crystals: done, carried, stored or missing. */
public final class CrystalHollowsOverlay {

    private static final String[] ROBOT = {"Electron Transmitter", "FTX 3070", "Robotron Reflector",
            "Superlite Motor", "Control Switch", "Synthetic Heart"};
    private static final String[] DIVAN = {"Scavenged Lapis Sword", "Scavenged Golden Hammer",
            "Scavenged Diamond Axe", "Scavenged Emerald Hammer"};
    private static final String[] CRYSTALS = {"Jade", "Amber", "Amethyst", "Sapphire", "Topaz"};
    private static final String[] CRYSTAL_COLORS = {"§a", "§6", "§5", "§b", "§e"};

    private static final int NOT_FOUND = 0, FOUND = 1, PLACED = 2;
    private static final int LINE = 10;

    private static final Pattern CRYSTAL_NAME = Pattern.compile("(Jade|Amber|Amethyst|Sapphire|Topaz) Crystal");
    private static final Pattern PLACED_LINE = Pattern.compile("✦ You placed the (Jade|Amber|Amethyst|Sapphire|Topaz) Crystal!");
    private static final Pattern ROBOT_THANKS = Pattern.compile("\\[NPC] Professor Robot: Thanks for bringing me the (.+?)! Bring me .*");
    private static final Pattern KEEPER_RETURN = Pattern.compile("\\[NPC] Keeper of \\w+: Excellent! You have returned the (.+?) to its rightful place!");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static long crystalFoundAt;
    private static boolean robotList;
    private static long robotListAt;
    private static final Set<String> robotListed = new HashSet<>();

    private CrystalHollowsOverlay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("crystalHollowsOverlay", "Crystal Hollows Parts").section("Mining")
                .ctx(HudEditElement.HudCtx.MINING)
                .enabled(() -> cfg().crystalHollowsOverlay)
                .size(() -> Math.max(120, width), () -> Math.max(1, lines.size()) * LINE)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty() ? List.of(
                        "§5§lCrystal Hollows", "§3Automaton: §a✔ FTX 3070 §e● Control Switch §c✖ Synthetic Heart",
                        "§3Crystals: §a✔ Jade §e● Amber §c✖ Topaz") : lines));
        Feature.of("CrystalHollowsOverlay").section("Mining").when(() -> cfg().crystalHollowsOverlay)
                .tick(CrystalHollowsOverlay::tick)
                .serverChat(CrystalHollowsOverlay::onChat)
                .hudHiddenInInventory("crystalhollowsoverlay", CrystalHollowsOverlay::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Crystal Nucleus");
                    b.toggle("Parts & Crystals Overlay", () -> c.crystalHollowsOverlay, v -> c.crystalHollowsOverlay = v)
                            .desc("Automaton and Divan parts (✔ handed in, ● carried, ◆ in storage, ✖ missing) and which crystals are found or placed")
                            .tags("nucleus", "robot", "divan", "automaton", "crystal", "precursor");
                    b.toggle("  Only In Crystal Hollows", () -> c.crystalHollowsOverlayOnlyHollows, v -> c.crystalHollowsOverlayOnlyHollows = v);
                    b.button("  Reset Parts & Crystals", () -> {
                        c.chCrystalStates.clear();
                        c.chPartsDelivered.clear();
                        TeslaMapsConfig.save();
                        ticks = 39;
                    });
                    b.info("Learns from chat: hand-ins, crystal found/placed, Nucleus loot resets it");
                });
    }

    private static boolean inHollows() {
        return com.teslamaps.utils.SkyblockUtils.getArea().equalsIgnoreCase("Crystal Hollows");
    }

    private static void onChat(String raw) {
        String t = StringUtil.strip(raw).trim();
        TeslaMapsConfig c = cfg();
        boolean changed = false;
        if (t.startsWith("✦ CRYSTAL FOUND")) crystalFoundAt = System.currentTimeMillis();
        Matcher m = CRYSTAL_NAME.matcher(t);
        if (m.matches() && System.currentTimeMillis() - crystalFoundAt < 3000) {
            if (c.chCrystalStates.getOrDefault(m.group(1), NOT_FOUND) < FOUND) c.chCrystalStates.put(m.group(1), FOUND);
            changed = true;
        }
        m = PLACED_LINE.matcher(t);
        if (m.matches()) {
            c.chCrystalStates.put(m.group(1), PLACED);
            changed = true;
        }
        if (t.contains("Crystal Loot Bundle") && t.startsWith("You")) {
            c.chCrystalStates.clear();
            changed = true;
        }
        m = ROBOT_THANKS.matcher(t);
        if (m.matches()) changed |= deliver(m.group(1));
        if (t.startsWith("[NPC] Professor Robot: You've brought me all") || t.contains("This will work just fine.")) {
            for (String p : ROBOT) changed |= deliver(p);
        }
        m = KEEPER_RETURN.matcher(t);
        if (m.matches()) changed |= deliver(m.group(1));
        if (t.startsWith("[NPC] Keeper of ") && t.contains("You found all of the items!")) {
            for (String p : DIVAN) changed |= deliver(p);
        }
        if (t.startsWith("[NPC] Professor Robot: That's not one of the components I need!")) {
            robotList = true;
            robotListAt = System.currentTimeMillis();
            robotListed.clear();
        } else if (robotList) {
            for (String p : ROBOT) if (t.equals(p)) robotListed.add(p);
        }
        if (changed) {
            TeslaMapsConfig.save();
            ticks = 39;
        }
    }

    private static boolean deliver(String part) {
        if (cfg().chPartsDelivered.contains(part)) return false;
        cfg().chPartsDelivered.add(part);
        return true;
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        // the robot lists what is still missing, everything else is handed in
        if (robotList && System.currentTimeMillis() - robotListAt > 1500) {
            robotList = false;
            if (!robotListed.isEmpty()) {
                for (String p : ROBOT) {
                    if (robotListed.contains(p)) c.chPartsDelivered.remove(p);
                    else deliver(p);
                }
                TeslaMapsConfig.save();
            }
        }
        if (++ticks % 40 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        Set<String> carried = new HashSet<>(), stored = new HashSet<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) note(inv.getItem(i), carried);
        for (int i = 1; i <= 9; i++) noteStored("epage_" + i, stored);
        for (int i = 1; i <= 18; i++) noteStored("backpack_" + i, stored);

        List<String> out = new ArrayList<>();
        out.add("§5§lCrystal Hollows");
        out.add(partsLine("§3Automaton: ", ROBOT, carried, stored));
        out.add(partsLine("§3Divan: ", DIVAN, carried, stored));
        StringBuilder sb = new StringBuilder("§3Crystals: ");
        for (int i = 0; i < CRYSTALS.length; i++) {
            int s = c.chCrystalStates.getOrDefault(CRYSTALS[i], NOT_FOUND);
            sb.append(s == PLACED ? "§a✔ " : s == FOUND ? "§e● " : "§c✖ ").append(CRYSTAL_COLORS[i]).append(CRYSTALS[i]).append(' ');
        }
        out.add(sb.toString().trim());
        int w = 0;
        for (String l : out) w = Math.max(w, mc.font.width(l));
        width = w;
        lines = out;
    }

    private static String partsLine(String head, String[] parts, Set<String> carried, Set<String> stored) {
        StringBuilder sb = new StringBuilder(head);
        for (String p : parts) {
            String mark = cfg().chPartsDelivered.contains(p) ? "§a✔ "
                    : carried.contains(p) ? "§e● " : stored.contains(p) ? "§6◆ " : "§c✖ ";
            sb.append(mark).append("§f").append(short_(p)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String short_(String p) {
        return p.startsWith("Scavenged ") ? p.substring(10) : p;
    }

    private static void noteStored(String key, Set<String> out) {
        var items = StorageCache.items(key);
        if (items == null) return;
        for (ItemStack s : items.values()) note(s, out);
    }

    private static void note(ItemStack s, Set<String> out) {
        if (s == null || s.isEmpty()) return;
        String name = StringUtil.strip(s.getHoverName().getString()).trim();
        for (String p : ROBOT) if (name.equals(p)) out.add(p);
        for (String p : DIVAN) if (name.equals(p)) out.add(p);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty()) return;
        if (cfg().crystalHollowsOverlayOnlyHollows && !inHollows()) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        int w = 0;
        for (String s : l) w = Math.max(w, mc.font.width(s));
        draw(g, mc, x, y, scale, l, w);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * LINE + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * LINE, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
