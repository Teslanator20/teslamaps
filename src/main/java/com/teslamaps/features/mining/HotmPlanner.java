package com.teslamaps.features.mining;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.Eval;
import com.teslamaps.profileviewer.data.PerkRegistry;
import com.teslamaps.profileviewer.screen.PerkTreeView;
import com.teslamaps.profileviewer.ui.Kit;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.screen.ScreenFit;
import com.teslamaps.screen.TeslaButton;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plan HotM / HotF perk levels without buying them: current levels come from the menu,
 * target levels are clicked in a tree, and the powder / whisper cost is summed per kind.
 */
public final class HotmPlanner {

    private static final Pattern LEVEL = Pattern.compile("Level (\\d+)(?:/\\d+)?.*");
    private static Screen lastScreen;
    private static int ticks;

    private HotmPlanner() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HotmPlanner").section("Mining").when(() -> cfg().hotmPlanner)
                .tick(HotmPlanner::tick)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("HotM / HotF Planner", () -> c.hotmPlanner, v -> c.hotmPlanner = v)
                            .desc("Plan target levels for every Heart of the Mountain / Forest perk and see the powder and whisper cost. Open /hotm or /hotf once so your levels are known. /tmap planner [hotf]")
                            .tags("hotm", "hotf", "planner", "powder", "whispers", "perk", "cost");
                    b.button("  Open Planner", () -> open(false));
                });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("planner")
                        .executes(c -> command(false))
                        .then(ClientCommands.literal("hotm").executes(c -> command(false)))
                        .then(ClientCommands.literal("hotf").executes(c -> command(true))))));
    }

    private static int command(boolean forest) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().hotmPlanner) {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Turn on §fHotM / HotF Planner §7(Mining) first"));
            return 0;
        }
        mc.schedule(() -> open(forest));
        return 1;
    }

    private static void open(boolean forest) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new PlannerScreen(mc.gui.screen(), forest));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != lastScreen) {
            lastScreen = mc.gui.screen();
            ticks = 0;
        }
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || ++ticks % 10 != 0) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        boolean forest = title.startsWith("Heart of the Forest");
        if (!forest && !title.startsWith("Heart of the Mountain")) return;
        Map<String, PerkRegistry.Perk> byName = new HashMap<>();
        for (PerkRegistry.Perk p : forest ? PerkRegistry.hotf() : PerkRegistry.hotm()) {
            if (p.name() != null && p.max() != null && p.cost() != null) byName.put(p.name(), p);
        }
        Map<String, Integer> base = forest ? cfg().hotfPlanBase : cfg().hotmPlanBase;
        boolean changed = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            PerkRegistry.Perk perk = byName.get(StringUtil.stripTrim(stack.getHoverName().getString()));
            if (perk == null) continue;
            int level = 0;
            for (String line : ItemUtil.loreLines(stack)) {
                Matcher m = LEVEL.matcher(StringUtil.stripTrim(line));
                if (m.matches()) {
                    level = Integer.parseInt(m.group(1));
                    break;
                }
            }
            Integer old = base.put(perk.id(), level);
            if (old == null || old != level) changed = true;
        }
        if (changed) TeslaMapsConfig.save();
    }

    /** Cost to go from {@code from} to {@code to}; level 1 comes from the token, not powder. */
    static long cost(PerkRegistry.Perk perk, int from, int to) {
        long total = 0;
        Map<String, Double> vars = new HashMap<>();
        for (int l = Math.max(from, 1); l < to; l++) {
            vars.put("level", (double) l);
            total += (long) Math.floor(Eval.eval(perk.cost(), vars));
        }
        return total;
    }

    static String kindName(String kind) {
        return switch (kind) {
            case "MITHRIL" -> "§2Mithril Powder";
            case "GEMSTONE" -> "§dGemstone Powder";
            case "GLACITE" -> "§bGlacite Powder";
            case "FOREST" -> "§aForest Whispers";
            case "DESERT" -> "§6Desert Whispers";
            default -> "§7" + kind.charAt(0) + kind.substring(1).toLowerCase();
        };
    }

    private static final class PlannerScreen extends Screen {
        private static final int CELL = 18;
        private final Screen parent;
        private boolean forest;
        private List<PerkRegistry.Perk> perks;
        private int minX, maxY, treeX, treeY, treeW, treeH, panelX, panelY, panelW, panelH;
        private final Map<String, Long> totals = new LinkedHashMap<>();
        private final Map<String, Integer> shown = new HashMap<>();
        private boolean dirty = true;
        private PerkRegistry.Perk tipPerk;
        private int tipBase = -1, tipTarget = -1;
        private List<Component> tip = List.of();

        PlannerScreen(Screen parent, boolean forest) {
            super(Component.literal("HotM Planner"));
            this.parent = parent;
            this.forest = forest;
        }

        private Map<String, Integer> base() { return forest ? cfg().hotfPlanBase : cfg().hotmPlanBase; }
        private Map<String, Integer> target() { return forest ? cfg().hotfPlanTarget : cfg().hotmPlanTarget; }

        @Override
        protected void init() {
            perks = forest ? PerkRegistry.hotf() : PerkRegistry.hotm();
            minX = Integer.MAX_VALUE;
            maxY = 0;
            for (PerkRegistry.Perk p : perks) {
                minX = Math.min(minX, p.x());
                maxY = Math.max(maxY, p.y());
            }
            treeW = PerkTreeView.width(perks);
            treeH = PerkTreeView.height(perks);
            panelW = treeW + 190;
            panelH = Math.max(treeH, 150) + 70;
            panelX = (width - panelW) / 2;
            panelY = (height - panelH) / 2;
            treeX = panelX + 12;
            treeY = panelY + 30;
            int by = panelY + panelH - 30;
            addRenderableWidget(new TeslaButton(panelX + 12, by, 90, 20, forest ? "§fShow HotM" : "§fShow HotF", () -> {
                forest = !forest;
                dirty = true;
                tipPerk = null;
                rebuildWidgets();
            }));
            addRenderableWidget(new TeslaButton(panelX + 108, by, 90, 20, "§fReset Plan", () -> {
                target().clear();
                dirty = true;
                tipPerk = null;
            }));
            addRenderableWidget(new TeslaButton(panelX + 204, by, 90, 20, "§fBought It", () -> {
                base().putAll(target());
                target().clear();
                dirty = true;
                tipPerk = null;
            }));
            addRenderableWidget(new TeslaButton(panelX + panelW - 72, by, 60, 20, "§fDone", this::onClose));
            ScreenFit.clamp(this);
        }

        private int baseOf(PerkRegistry.Perk p) {
            return base().getOrDefault(p.id(), 0);
        }

        private int targetOf(PerkRegistry.Perk p) {
            return Math.max(baseOf(p), target().getOrDefault(p.id(), 0));
        }

        private void recompute() {
            dirty = false;
            totals.clear();
            shown.clear();
            for (PerkRegistry.Perk p : perks) {
                if (p.id() == null) continue;
                if (p.max() == null || p.cost() == null || p.costKind() == null) {
                    shown.put(p.id(), 1);
                    continue;
                }
                int b = baseOf(p), t = targetOf(p);
                shown.put(p.id(), t);
                if (t > b) totals.merge(p.costKind(), cost(p, b, t), Long::sum);
            }
        }

        private PerkRegistry.Perk at(double mx, double my) {
            if (mx < treeX || my < treeY) return null;
            int col = (int) ((mx - treeX) / CELL), drawRow = (int) ((my - treeY) / CELL);
            if ((mx - treeX) % CELL >= 16 || (my - treeY) % CELL >= 16) return null;
            int row = maxY - drawRow;
            for (PerkRegistry.Perk p : perks) {
                if (p.x() - minX == col && p.y() == row && p.max() != null && p.cost() != null) return p;
            }
            return null;
        }

        private void change(PerkRegistry.Perk p, int delta) {
            int b = baseOf(p);
            int t = Math.max(b, Math.min(p.max(), targetOf(p) + delta));
            if (t > b) target().put(p.id(), t);
            else target().remove(p.id());
            dirty = true;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            PerkRegistry.Perk p = at(event.x(), event.y());
            if (p != null) {
                int step = event.hasShiftDown() ? 10 : 1;
                if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) change(p, step);
                else if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) change(p, -step);
                else if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE) change(p, p.max());
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double sx, double sy) {
            PerkRegistry.Perk p = at(mx, my);
            if (p != null && sy != 0) {
                change(p, sy > 0 ? 1 : -1);
                return true;
            }
            return super.mouseScrolled(mx, my, sx, sy);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            if (dirty) recompute();
            g.fill(0, 0, width, height, 0xB0000000);
            HudStyle.screenPanel(g, panelX, panelY, panelW, panelH, 8);
            g.text(font, forest ? "§f§lHeart of the Forest Planner" : "§f§lHeart of the Mountain Planner", panelX + 12, panelY + 12, 0xFFFFFFFF);

            PerkTreeView.render(g, perks, shown, 10, forest ? PerkTreeView.FORAGING : PerkTreeView.MINING,
                    Set.of(), treeX, treeY, mouseX, mouseY);
            for (PerkRegistry.Perk p : perks) {
                if (p.id() == null || p.max() == null || p.cost() == null) continue;
                if (targetOf(p) <= baseOf(p)) continue;
                int sx = treeX + (p.x() - minX) * CELL, sy = treeY + (maxY - p.y()) * CELL;
                g.fill(sx - 1, sy - 1, sx + 17, sy, 0xFFFFD700);
                g.fill(sx - 1, sy + 16, sx + 17, sy + 17, 0xFFFFD700);
                g.fill(sx - 1, sy, sx, sy + 16, 0xFFFFD700);
                g.fill(sx + 16, sy, sx + 17, sy + 16, 0xFFFFD700);
            }

            int rx = treeX + treeW + 14, ry = treeY;
            if (base().isEmpty()) {
                g.text(font, "§cLevels unknown", rx, ry, 0xFFFFFFFF);
                g.text(font, forest ? "§7Open /hotf once" : "§7Open /hotm once", rx, ry + 10, 0xFFFFFFFF);
                ry += 26;
            }
            g.text(font, "§e§lPlanned cost", rx, ry, 0xFFFFFFFF);
            ry += 12;
            if (totals.isEmpty()) {
                g.text(font, "§7nothing planned", rx, ry, 0xFFFFFFFF);
                ry += 10;
            }
            for (Map.Entry<String, Long> e : totals.entrySet()) {
                g.text(font, kindName(e.getKey()) + "§7:", rx, ry, 0xFFFFFFFF);
                g.text(font, "§f" + Kit.dots(e.getValue()), rx + 8, ry + 10, 0xFFFFFFFF);
                ry += 22;
            }
            ry += 6;
            g.text(font, "§8Left +1  Right -1", rx, ry, 0xFFFFFFFF);
            g.text(font, "§8Shift x10  Middle max", rx, ry + 10, 0xFFFFFFFF);
            g.text(font, "§8Scroll +/-1", rx, ry + 20, 0xFFFFFFFF);

            super.extractRenderState(g, mouseX, mouseY, delta);

            PerkRegistry.Perk hover = at(mouseX, mouseY);
            if (hover != null) {
                int b = baseOf(hover), t = targetOf(hover);
                if (hover != tipPerk || b != tipBase || t != tipTarget) {
                    tipPerk = hover;
                    tipBase = b;
                    tipTarget = t;
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal("§a" + hover.name()));
                    lines.add(Component.literal("§7Current: §f" + b + "§7/§8" + hover.max()));
                    lines.add(Component.literal("§7Planned: " + (t > b ? "§e" : "§f") + t + "§7/§8" + hover.max()));
                    if (t > b) {
                        lines.add(Component.literal(""));
                        lines.add(Component.literal("§7Cost: §f" + Kit.dots(cost(hover, b, t)) + " " + kindName(hover.costKind())));
                    }
                    if (b < hover.max()) {
                        lines.add(Component.literal("§7To max: §f" + Kit.dots(cost(hover, b, hover.max()))));
                    }
                    tip = lines;
                }
                g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
            }
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

        @Override
        public void onClose() {
            TeslaMapsConfig.save();
            Minecraft.getInstance().gui.setScreen(parent);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}
