package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/PracticeScreen.kt

import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.pack.layouts.LayoutPack;
import com.teslamaps.adm.sim.run.LagPreset;
import com.teslamaps.adm.sim.run.LayoutSource;
import com.teslamaps.adm.sim.run.PracticePlan;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.RunConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PracticeScreen extends Screen {
    private enum Tab {
        PUZZLES("Puzzles", "The ten puzzles, each in a state a recorded run had.", Set.of(RoomType.PUZZLE)),
        ROOMS("Rooms", "The normal rooms (and the rare and yellow ones): mobs and secrets, as recorded.", Set.of(RoomType.NORMAL, RoomType.RARE, RoomType.YELLOW)),
        OTHER("Trap & Fairy", "The trap room and the fairy room.", Set.of(RoomType.TRAP, RoomType.FAIRY));

        final String label;
        final String about;
        final Set<RoomType> types;

        Tab(String label, String about, Set<RoomType> types) {
            this.label = label;
            this.about = about;
            this.types = types;
        }
    }

    private final Screen parent;
    private Tab tab = lastTab;
    private String query = "";
    private int page = 0;
    private List<PracticePlan.Choice> choices = null;
    private String message = null;
    private String said = null;

    public PracticeScreen(Screen parent) {
        super(Component.literal("Practice"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int w = Math.min(width - 20, 440);
        int y = 28;
        StringWidget head = new StringWidget(title, font);
        head.setPosition(cx - font.width(title) / 2, 10);
        addRenderableWidget(head);

        List<PracticePlan.Choice> all = choices != null ? choices : load();
        if (all == null) {
            String text = message != null ? message : "Loading the data packs...";
            StringWidget sw = new StringWidget(Component.literal("§7" + text), font);
            sw.setPosition(cx - font.width(text) / 2, height / 2 - 20);
            addRenderableWidget(sw);
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(cx - 28, height / 2, 56, 20).build());
            return;
        }

        Tab[] tabs = Tab.values();
        int tw = (w - 8) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int n = 0;
            for (PracticePlan.Choice c : all) if (t.types.contains(c.type)) n++;
            addRenderableWidget(Button.builder(Component.literal(t == tab ? "§e§n" + t.label + " (" + n + ")" : t.label + " (" + n + ")"), b -> { tab = t; lastTab = t; page = 0; rebuildWidgets(); })
                    .tooltip(Tooltip.create(Component.literal(t.about))).bounds(cx - w / 2 + i * (tw + 4), y, tw, 20).build());
        }
        y += 24;
        EditBox box = new EditBox(font, cx - w / 2, y, w - 44, 20, Component.literal("Search"));
        box.setMaxLength(40);
        box.setHint(Component.literal("§8Search a room"));
        box.setValue(query);
        box.setResponder(it -> { query = it; page = 0; });
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal("Find"), b -> rebuildWidgets()).bounds(cx + w / 2 - 40, y, 40, 20).build());
        y += 26;

        List<PracticePlan.Choice> view = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (PracticePlan.Choice c : all) {
            if (tab.types.contains(c.type) && (query.isBlank() || c.name.toLowerCase(Locale.ROOT).contains(q))) view.add(c);
        }
        int cols = w >= 400 ? 4 : 3;
        int rowsFit = Math.max((height - y - 40) / ROW, 2);
        int pageSize = cols * rowsFit;
        int pages = Math.max(1, (view.size() + pageSize - 1) / pageSize);
        page = Math.clamp(page, 0, pages - 1);
        if (view.isEmpty()) {
            StringWidget sw = new StringWidget(Component.literal("§7Nothing matches."), font);
            sw.setPosition(cx - font.width("Nothing matches.") / 2, y + 20);
            addRenderableWidget(sw);
        }
        int bw = (w - (cols - 1) * 4) / cols;
        int from = Math.min(view.size(), page * pageSize);
        List<PracticePlan.Choice> shown = view.subList(from, Math.min(view.size(), from + pageSize));
        for (int i = 0; i < shown.size(); i++) {
            PracticePlan.Choice c = shown.get(i);
            int bx = cx - w / 2 + (i % cols) * (bw + 4);
            int by = y + (i / cols) * ROW;
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(c.name, bw - 8)), b -> start(c))
                    .tooltip(Tooltip.create(Component.literal(c.name + ": " + c.shape + " " + c.type.name().toLowerCase(Locale.ROOT) + " room, in " + c.runs + " recorded run" + (c.runs == 1 ? "" : "s") + ". Click to practise it (a recorded run's build and state, drawn at random).")))
                    .bounds(bx, by, bw, 20).build());
        }
        int by = height - 30;
        if (said != null) {
            String t = font.plainSubstrByWidth(said, w);
            StringWidget sw = new StringWidget(Component.literal("§e" + t), font);
            sw.setPosition(cx - font.width(t) / 2, by - 12);
            addRenderableWidget(sw);
        }
        Button prev = Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(cx - w / 2, by, 24, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);
        String label = "Page " + (page + 1) + " of " + pages + ", " + view.size() + " rooms";
        StringWidget pl = new StringWidget(Component.literal("§7" + label), font);
        pl.setPosition(cx - font.width(label) / 2, by + 6);
        addRenderableWidget(pl);
        Button nextB = Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(cx + w / 2 - 24, by, 24, 20).build();
        nextB.active = page < pages - 1;
        addRenderableWidget(nextB);
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(cx + w / 2 - 24 - 62, by, 56, 20).build());
    }

    private void start(PracticePlan.Choice c) {
        Minecraft mc = Minecraft.getInstance();
        RunConfig config = new RunConfig("F7", new LayoutSource.Practice(c.name), System.nanoTime(), false, 1, LagPreset.OFF, false, 1.0, null, null, null);
        RunStarter.start(config, line -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§8[§6ADS§8] §7" + line));
            mc.execute(() -> { if (mc.gui.screen() == this) { said = line; rebuildWidgets(); } });
        });
    }

    private List<PracticePlan.Choice> load() {
        PackSet set = Packs.current;
        if (set == null) {
            Packs.loadAsync().thenAccept(s -> Minecraft.getInstance().execute(() -> { if (Minecraft.getInstance().gui.screen() == this) rebuildWidgets(); }));
            return null;
        }
        LayoutPack pack = set.layouts;
        if (pack == null) {
            String joined = String.join(" ", set.userLines());
            message = joined.isEmpty() ? "The layout pool is not there." : joined;
            return null;
        }
        List<PracticePlan.Choice> c = PracticePlan.choices(pack);
        choices = c;
        return c;
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    private static final int ROW = 22;
    private static Tab lastTab = Tab.PUZZLES;
}
