package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/RunStartScreen.kt

import com.teslamaps.adm.sim.P3Sim;
import com.teslamaps.adm.sim.clear.bots.BotStrategies;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.run.LagPreset;
import com.teslamaps.adm.sim.run.LayoutSource;
import com.teslamaps.adm.sim.run.RunConfig;
import com.teslamaps.adm.sim.run.plugin.PartyStrategy;
import com.teslamaps.config.TeslaMapsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RunStartScreen extends Screen {
    private RunDefaults defaults = UiSettings.defaults();
    private EditBox seedBox;
    private final List<String> problems = new ArrayList<>();

    public RunStartScreen() {
        super(Component.literal(P3Sim.FULL_NAME + ": full run"));
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        int cx = width / 2;
        List<PartyStrategy> offered = BotStrategies.available();
        boolean showStrategy = StrategyChoice.hasPresets(offered);
        int rows = showStrategy ? 9 : 8;
        int y = Math.max(24, height / 2 - (rows * ROW) / 2 - 10);
        StringWidget head = new StringWidget(title, font);
        head.setPosition(cx - font.width(title) / 2, y - 18);
        addRenderableWidget(head);

        label("Floor", y);
        addRenderableWidget(Button.builder(Component.literal("§a§nF7"), b -> {}).tooltip(tip("Floor 7. Floor 7 only for now; the master mode floor is not built."))
                .bounds(cx - 20, y, 40, 20).build());
        y += ROW;

        label("Layout", y);
        int x0 = cx - 3 * 52 / 2 - 4;
        source("Random", Mode.RANDOM, x0, y, "A random recorded layout. The seed is shown when the run starts, so the same run can be repeated.");
        source("Seed", Mode.SEED, x0 + 52, y, "The layout a seed number picks; the same seed gives the same layout and the same run.");
        source("Recorded", Mode.RECORDED, x0 + 104, y, "A layout from a run that was recorded: pick it from the browser by its rooms, puzzles and recorded time.");
        y += ROW;
        switch (mode) {
            case SEED -> {
                seedBox = new EditBox(font, cx - 60, y, 80, 20, Component.literal("Seed"));
                seedBox.setMaxLength(18);
                seedBox.setHint(Component.literal("§8Seed"));
                seedBox.setValue(seedText);
                seedBox.setResponder(it -> seedText = it);
                addRenderableWidget(seedBox);
                addRenderableWidget(Button.builder(Component.literal("New"), b -> { seedText = Long.toString(UiModule.drawSeed()); rebuildWidgets(); })
                        .tooltip(tip("A fresh random seed.")).bounds(cx + 24, y, 36, 20).build());
            }
            case RECORDED -> {
                String chosen = chosenId;
                addRenderableWidget(Button.builder(Component.literal(chosen == null ? "Choose a layout..." : "§a" + LayoutBrowserModel.title(chosen)), b -> mc.setScreen(new LayoutBrowserScreen(this)))
                        .tooltip(tip(chosen == null ? "Opens the browser of recorded layouts." : chosen + ". Click: opens the browser to choose another."))
                        .bounds(cx - 90, y, 180, 20).build());
            }
            case RANDOM -> {}
        }
        y += ROW;

        label("Party", y);
        int px = cx - 5 * 22 / 2 - 30;
        for (int n = 1; n <= 5; n++) {
            int size = n;
            boolean on = (defaults.bots ? defaults.partySize : 1) == n;
            addRenderableWidget(Button.builder(Component.literal(pick("" + n, on)), b -> {
                RunDefaults d = defaults;
                defaults = new RunDefaults(size > 1, size, d.lag, d.noDeaths, d.mobHpFromPack, d.strategy);
                save();
                rebuildWidgets();
            }).tooltip(tip(n == 1 ? "You alone: no bots, the party list is just you." : n + " players: you and " + (n - 1) + " bot" + (n > 2 ? "s" : "") + " (the classes nobody plays).")).bounds(px + (n - 1) * 22, y, 20, 20).build());
        }
        addRenderableWidget(toggle("Bots", defaults.bots, 52, cx + 32, y, "Teammate bots walk the dungeon and their rooms check off on the measured schedule. Off is the same as a party of 1.", () -> {
            RunDefaults d = defaults;
            boolean on = !d.bots;
            defaults = new RunDefaults(on, on && d.partySize < 2 ? 5 : d.partySize, d.lag, d.noDeaths, d.mobHpFromPack, d.strategy);
        }));
        y += ROW;

        if (showStrategy) {
            label("Strategy", y);
            String choice = defaults.strategy;
            Button sb = Button.builder(Component.literal("§a" + StrategyChoice.label(choice, offered)), b -> {
                RunDefaults d = defaults;
                defaults = new RunDefaults(d.bots, d.partySize, d.lag, d.noDeaths, d.mobHpFromPack, StrategyChoice.next(choice, offered));
                save();
                rebuildWidgets();
            }).tooltip(tip(StrategyChoice.about(choice, offered))).bounds(cx - 90, y, 180, 20).build();
            sb.active = defaults.bots;
            addRenderableWidget(sb);
            y += ROW;
        }

        label("Lag", y);
        int lagW = 50;
        LagPreset[] presets = LagPreset.values();
        int lx = cx - presets.length * (lagW + 2) / 2;
        for (int i = 0; i < presets.length; i++) {
            LagPreset p = presets[i];
            addRenderableWidget(Button.builder(Component.literal(pick(p.label, defaults.lag == p)), b -> {
                RunDefaults d = defaults;
                defaults = new RunDefaults(d.bots, d.partySize, p, d.noDeaths, d.mobHpFromPack, d.strategy);
                save();
                rebuildWidgets();
            }).tooltip(tip(p == LagPreset.OFF ? "No lag layer: the server never stalls." : p.label + ": about " + p.lossPerMinute + " ticks lost per minute, as the server stalls and catches up." + (p == LagPreset.HIGH_SLOT ? " That is an instance slot above 200." : "")))
                    .bounds(lx + i * (lagW + 2), y, lagW, 20).build());
        }
        y += ROW;

        label("Rules", y);
        addRenderableWidget(toggle("No deaths", defaults.noDeaths, 90, cx - 92, y, "You cannot die in the clear: your health stops at its last point instead of the death and the revive.", () -> {
            RunDefaults d = defaults;
            defaults = new RunDefaults(d.bots, d.partySize, d.lag, !d.noDeaths, d.mobHpFromPack, d.strategy);
        }));
        addRenderableWidget(toggle("Mob HP per run", defaults.mobHpFromPack, 90, cx + 2, y, "On: each run draws its mob HP scale from the recorded per-run table. Off: every mob has its base HP.", () -> {
            RunDefaults d = defaults;
            defaults = new RunDefaults(d.bots, d.partySize, d.lag, d.noDeaths, !d.mobHpFromPack, d.strategy);
        }));
        y += ROW + 4;

        addRenderableWidget(Button.builder(Component.literal("§aStart run"), b -> start())
                .tooltip(tip("Builds the dungeon and starts the countdown. This opens a fresh ADS world, which takes a few seconds."))
                .bounds(cx - 60, y, 120, 20).build());
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(cx - 30, y, 60, 20).build());
        y += ROW + 4;

        for (String p : problems) for (String line : wrap(p, Math.min(width - 40, 360))) {
            StringWidget sw = new StringWidget(Component.literal("§c" + line), font);
            sw.setPosition(cx - font.width(line) / 2, y);
            addRenderableWidget(sw);
            y += 11;
        }
    }

    private void start() {
        Minecraft mc = Minecraft.getInstance();
        problems.clear();
        LayoutSource layout;
        switch (mode) {
            case RANDOM -> layout = LayoutSource.Random.INSTANCE;
            case SEED -> {
                Long n;
                try { n = Long.parseLong(seedText.trim()); } catch (NumberFormatException e) { n = null; }
                if (n == null) { problems.add("Type the seed as a number, or press New."); rebuildWidgets(); return; }
                layout = new LayoutSource.Seed(n);
            }
            default -> {
                String id = chosenId;
                if (id == null) { problems.add("Choose a recorded layout first."); rebuildWidgets(); return; }
                layout = new LayoutSource.Recorded(id);
            }
        }
        long seed = layout instanceof LayoutSource.Seed s ? s.seed : UiModule.drawSeed();
        save();
        RunConfig config = defaults.config(layout, seed);
        List<String> said = new ArrayList<>();
        RunStarter.start(config, line -> {
            said.add(line);
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§8[§6ADS§8] §7" + line));
            mc.execute(() -> {
                if (mc.screen == this) { problems.clear(); problems.addAll(said); rebuildWidgets(); }
            });
        });
    }

    private enum Mode { RANDOM, SEED, RECORDED }

    private void source(String name, Mode m, int x, int y, String about) {
        addRenderableWidget(Button.builder(Component.literal(pick(name, mode == m)), b -> { mode = m; rebuildWidgets(); }).tooltip(tip(about)).bounds(x, y, 50, 20).build());
    }

    private Button toggle(String name, boolean on, int w, int x, int y, String about, Runnable flip) {
        return Button.builder(Component.literal(on ? "§a" + name : name), b -> { flip.run(); save(); rebuildWidgets(); }).tooltip(tip(about)).bounds(x, y, w, 20).build();
    }

    private void label(String t, int y) {
        Component c = Component.literal("§e" + t);
        StringWidget sw = new StringWidget(c, font);
        sw.setPosition(width / 2 - LABEL_X - font.width(c) / 2, y + 6);
        addRenderableWidget(sw);
    }

    private void save() { UiSettings.store(defaults); }

    public void chose(String id) {
        chosenId = id;
        mode = Mode.RECORDED;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        super.removed();
        save();
        TeslaMapsConfig.save();
    }

    @Override
    public void added() {
        super.added();
        Minecraft mc = Minecraft.getInstance();
        if (Packs.current == null) Packs.loadAsync().thenAccept(set -> mc.execute(() -> {
            if (mc.screen == this && !set.canBuildDungeon()) { problems.clear(); problems.addAll(set.userLines()); rebuildWidgets(); }
        }));
        else {
            PackSet cur = Packs.current;
            if (cur != null && !cur.canBuildDungeon() && problems.isEmpty()) problems.addAll(cur.userLines());
        }
    }

    private Tooltip tip(String text) { return Tooltip.create(Component.literal(text)); }

    private String pick(String name, boolean on) { return on ? "§a§n" + name : name; }

    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        String cur = "";
        for (String w : text.split(" ", -1)) {
            String next = cur.isEmpty() ? w : cur + " " + w;
            if (!cur.isEmpty() && font.width(next) > maxWidth) { out.add(cur); cur = w; } else cur = next;
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    private static final int ROW = 24;
    private static final int LABEL_X = 130;
    private static Mode mode = Mode.RANDOM;
    private static String seedText = "";
    private static String chosenId = null;

    public static String currentChoice() { return chosenId; }
}
