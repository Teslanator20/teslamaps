package com.teslamaps.features;

// Ported from SkyHanni's PestFinder.kt and GardenPlotApi.renderPlot
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Infested plots as a clickable panel, plot walls and labels in the world, and a teleport to the nearest one. */
public final class PestFinder {

    private static final int PAD = 5;
    private static final int LINE = 10;
    private static final int MIN_Y = 66;
    private static final int MAX_Y = 102;

    private record Infested(int slot, String name, String label, double midX, double midZ, Vec3 labelPos) {}

    private static final List<Infested> infested = new ArrayList<>();
    private static final Map<String, Integer> counts = new HashMap<>();
    private static final List<String> lines = new ArrayList<>();
    private static final List<Vec3>[] walls = new List[25];
    private static final float[][] wallWidths = new float[25][];
    private static int width = 60;
    private static int ticks;
    private static long lastHeld;
    private static long lastKey;
    private static boolean keyDown;
    private static int playerSlot = -1;

    private PestFinder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        HudEditElement.of("pestFinder", "Pest Finder").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().pestFinder)
                .size(() -> width, () -> height())
                .preview(PestFinder::draw);
        Feature.of("PestFinder").section("Garden")
                .when(() -> cfg().pestFinder || cfg().pestFinderBorders || cfg().pestFinderKey != -1 || cfg().pestFinderNoPestTitle)
                .tick(PestFinder::tick)
                .serverChat(PestFinder::onChat)
                .world(PestFinder::renderWorld)
                .hudForeground("pestfinder", PestFinder::render);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("tpinfested").executes(c -> {
                    teleportNearest();
                    return 1;
                }))));
    }

    // --- data ---

    private static void onChat(String raw) {
        if (!SkyblockUtils.isInGarden()) return;
        String text = StringUtil.strip(raw);
        if (cfg().pestFinderNoPestTitle && text.startsWith("There are not any Pests on your Garden right now")) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal("§eNo pests!"));
            mc.gui.setSubtitle(Component.empty());
            return;
        }
        PestSpawnAlerts.Spawn spawn = PestSpawnAlerts.parse(text);
        if (spawn == null) return;
        for (String plot : spawn.plots()) {
            if (spawn.amount() < 0) counts.putIfAbsent(plot, 0);
            else counts.merge(plot, spawn.amount(), Integer::sum);
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInGarden()) {
            infested.clear();
            counts.clear();
            keyDown = false;
            return;
        }
        pollKey(mc);
        if (holdsTool(mc.player.getMainHandItem())) lastHeld = System.currentTimeMillis();
        if (++ticks < 10) return;
        ticks = 0;
        rebuild(mc);
    }

    private static void rebuild(Minecraft mc) {
        infested.clear();
        List<String> numbers = PestData.infestedPlots();
        String here = PestData.currentPlot();
        int herePests = PestData.pestsOnCurrentPlot();
        java.util.Set<String> alive = new java.util.HashSet<>();
        for (String n : numbers) {
            int number;
            try { number = Integer.parseInt(n.trim()); } catch (NumberFormatException e) { continue; }
            int slot = GardenPlots.slotOf(number);
            if (slot < 0) continue;
            GardenPlots.Plot plot = GardenPlots.get(slot);
            String name = plot != null && plot.name != null ? plot.name : Integer.toString(number);
            alive.add(name);
            if (here != null && here.equalsIgnoreCase(name)) counts.put(name, herePests);
            Integer count = counts.get(name);
            String amount = count == null || count <= 0 ? "?" : count.toString();
            String label = "§e" + amount + " §c" + ("1".equals(amount) ? "pest" : "pests") + " §7in §bPlot " + name;
            int col = slot % 5, row = slot / 5;
            infested.add(new Infested(slot, name, label, (col - 2) * 96.0, (row - 2) * 96.0,
                    new Vec3((col - 2) * 96.0, mc.player.getEyeY(), (row - 2) * 96.0)));
        }
        counts.keySet().removeIf(k -> !alive.contains(k) && !k.equals("The Barn"));
        Vec3 pos = mc.player.position();
        playerSlot = slotAt(pos.x, pos.z);

        lines.clear();
        lines.add("§6Total pests: §e" + PestData.alive() + "§6/§e8");
        for (Infested i : infested) lines.add(i.label);
        if (infested.isEmpty() && PestData.alive() > 0) {
            lines.add("§e" + PestData.alive() + " §6bugged pests!");
            lines.add("§cOpen /desk or enable the Pests widget");
        }
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        width = w + PAD * 2;
    }

    private static int slotAt(double x, double z) {
        if (Math.abs(x) > 240 || Math.abs(z) > 240) return -1;
        int col = (int) Math.floor((x + 240) / 96), row = (int) Math.floor((z + 240) / 96);
        return Math.min(4, row) * 5 + Math.min(4, col);
    }

    private static boolean holdsTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = ItemUtil.skyblockId(stack);
        if (id != null && (id.contains("VACUUM") || id.contains("LASSO"))) return true;
        List<String> lore = ItemUtil.loreLines(stack);
        if (lore.isEmpty()) return false;
        String last = StringUtil.stripTrim(lore.get(lore.size() - 1));
        return last.endsWith("VACUUM") || last.endsWith("LASSO");
    }

    private static boolean shown() {
        return !cfg().pestFinderHeldOnly || System.currentTimeMillis() - lastHeld < 5000;
    }

    // --- teleport ---

    private static void pollKey(Minecraft mc) {
        int key = cfg().pestFinderKey;
        if (key == -1 || mc.screen != null) { keyDown = false; return; }
        boolean down = KeyNames.isDown(key);
        if (down && !keyDown && System.currentTimeMillis() - lastKey > 2000) {
            lastKey = System.currentTimeMillis();
            teleportNearest();
        }
        keyDown = down;
    }

    public static void teleportNearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        if (!SkyblockUtils.isInGarden()) {
            msg("§cOnly works on the Garden");
            return;
        }
        if (infested.isEmpty()) rebuild(mc);
        Vec3 pos = mc.player.position();
        Infested best = null;
        double bestDist = Double.MAX_VALUE;
        for (Infested i : infested) {
            double dx = i.midX - pos.x, dz = i.midZ - pos.z;
            double d = dx * dx + dz * dz;
            if (d < bestDist) { bestDist = d; best = i; }
        }
        if (best == null) {
            msg("§cNo infested plots found");
            return;
        }
        if (best.slot == slotAt(pos.x, pos.z)) {
            msg("§7Already in an infested plot");
            return;
        }
        mc.getConnection().sendCommand("plottp " + best.name);
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }

    // --- world ---

    private static void renderWorld(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        if (!c.pestFinderBorders || infested.isEmpty() || !SkyblockUtils.isInGarden() || !shown()) return;
        for (Infested i : infested) {
            boolean own = i.slot == playerSlot;
            ESPRenderer.drawPolyline(pose, wall(i.slot), wallWidths[i.slot], own ? 0xFFFF5555 : 0xFFFFAA00, cam, false);
            if (!own && c.pestFinderLabels) {
                ESPRenderer.drawText(pose, i.label, i.labelPos, 1.5f, cam, true, true);
            }
        }
    }

    /** Wall lines of one plot as a polyline whose linking segments have width 0. */
    private static List<Vec3> wall(int slot) {
        List<Vec3> pts = walls[slot];
        if (pts != null) return pts;
        pts = new ArrayList<>();
        List<Float> w = new ArrayList<>();
        int col = slot % 5, row = slot / 5;
        double minX = (col - 2) * 96 - 48, minZ = (row - 2) * 96 - 48;
        double maxX = minX + 96, maxZ = minZ + 96;
        for (int s = 0; s <= 96; s += 8) {
            seg(pts, w, minX + s, MIN_Y, minZ, minX + s, MAX_Y, minZ, s % 96 == 0 ? 3f : 2f);
            seg(pts, w, minX + s, MIN_Y, maxZ, minX + s, MAX_Y, maxZ, s % 96 == 0 ? 3f : 2f);
            if (s == 0 || s == 96) continue;
            seg(pts, w, minX, MIN_Y, minZ + s, minX, MAX_Y, minZ + s, 2f);
            seg(pts, w, maxX, MIN_Y, minZ + s, maxX, MAX_Y, minZ + s, 2f);
        }
        for (int y = MIN_Y; y <= MAX_Y; y += 6) {
            seg(pts, w, minX, y, minZ, maxX, y, minZ, 2f);
            seg(pts, w, maxX, y, minZ, maxX, y, maxZ, 2f);
            seg(pts, w, maxX, y, maxZ, minX, y, maxZ, 2f);
            seg(pts, w, minX, y, maxZ, minX, y, minZ, 2f);
        }
        float[] widths = new float[w.size()];
        for (int k = 0; k < widths.length; k++) widths[k] = w.get(k);
        walls[slot] = pts;
        wallWidths[slot] = widths;
        return pts;
    }

    private static void seg(List<Vec3> pts, List<Float> w, double x1, double y1, double z1, double x2, double y2, double z2, float width) {
        pts.add(new Vec3(x1, y1, z1));
        w.add(0f);
        pts.add(new Vec3(x2, y2, z2));
        w.add(width);
    }

    // --- hud ---

    private static int height() {
        return Math.max(1, lines.size()) * LINE + PAD * 2 - 1;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        TeslaMapsConfig c = cfg();
        if (!c.pestFinder || !SkyblockUtils.isInGarden() || !shown()) return;
        draw(g, Minecraft.getInstance(), c.pestFinderX, c.pestFinderY, c.pestFinderScale);
    }

    public static boolean visible() {
        TeslaMapsConfig c = cfg();
        return c.pestFinder && c.section("Garden") && SkyblockUtils.isInGarden() && shown();
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        List<String> show = lines.isEmpty() ? List.of("§6Total pests: §e3§6/§e8", "§e3 §cpests §7in §bPlot 12") : lines;
        int w = lines.isEmpty() ? 110 : width;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, show.size() * LINE + PAD * 2 - 1, 4);
        for (int i = 0; i < show.size(); i++) g.text(mc.font, show.get(i), PAD, PAD + i * LINE, 0xFFFFFFFF);
        g.pose().popMatrix();
    }

    public static int width(float scale) { return (int) (width * scale); }

    public static int height(float scale) { return (int) (height() * scale); }

    /** Click on a plot row (inventory open) teleports there. */
    public static void onClick(double mouseX, double mouseY, int button) {
        TeslaMapsConfig c = cfg();
        int row = (int) Math.floor(((mouseY - c.pestFinderY) / c.pestFinderScale - PAD) / LINE) - 1;
        if (row < 0 || row >= infested.size()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand("plottp " + infested.get(row).name);
    }
}
