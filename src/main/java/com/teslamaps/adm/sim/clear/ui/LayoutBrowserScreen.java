package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/LayoutBrowserScreen.kt

import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.pack.layouts.LayoutPack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LayoutBrowserScreen extends Screen {
    private final RunStartScreen parent;
    private String query = lastQuery;
    private LayoutBrowserModel.Sort sort = lastSort;
    private boolean poolOnly = lastPoolOnly;
    private int page = 0;
    private List<LayoutRow> rows = null;
    private String message = null;

    public LayoutBrowserScreen(RunStartScreen parent) {
        super(Component.literal("Recorded layouts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        int cx = width / 2;
        int pageSize = Math.clamp((height - 120) / ROW, 3, 12);
        int w = Math.min(width - 20, 380);
        int y = 28;
        StringWidget head = new StringWidget(title, font);
        head.setPosition(cx - font.width(title) / 2, 10);
        addRenderableWidget(head);

        List<LayoutRow> all = rows != null ? rows : loadRows();
        if (all == null) {
            String text = message != null ? message : "Loading the layout pool...";
            int yy = height / 2 - 20;
            for (String line : wrap(text, w)) {
                StringWidget sw = new StringWidget(Component.literal("§c" + line), font);
                sw.setPosition(cx - font.width(line) / 2, yy);
                addRenderableWidget(sw);
                yy += 11;
            }
            addRenderableWidget(Button.builder(Component.literal("Reload"), b -> { rows = null; message = null; rebuildWidgets(); })
                    .tooltip(tip("Reads the data pack folder again.")).bounds(cx - 60, yy + 8, 56, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(cx + 4, yy + 8, 56, 20).build());
            return;
        }

        EditBox box = new EditBox(font, cx - w / 2, y, w - 188, 20, Component.literal("Search"));
        box.setMaxLength(60);
        box.setHint(Component.literal("§8Search: a puzzle, a room, a date"));
        box.setValue(query);
        box.setResponder(it -> { query = it; lastQuery = it; page = 0; });
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal("Find"), b -> rebuildWidgets()).tooltip(tip("Applies the search.")).bounds(cx + w / 2 - 184, y, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sort: " + sort.label), b -> { sort = sort.next(); lastSort = sort; page = 0; rebuildWidgets(); })
                .tooltip(tip(sort.about + " Click: the next sort.")).bounds(cx + w / 2 - 142, y, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal(poolOnly ? "§aComplete only" : "All"), b -> { poolOnly = !poolOnly; lastPoolOnly = poolOnly; page = 0; rebuildWidgets(); })
                .tooltip(tip("On: only layouts recorded completely (a random run can pick these). Off: every recorded layout, including ones that may not build or play well.")).bounds(cx + w / 2 - 56, y, 56, 20).build());
        y += ROW + 2;

        List<LayoutRow> view = LayoutBrowserModel.view(all, query, sort, poolOnly);
        int pages = LayoutBrowserModel.pageCount(view.size(), pageSize);
        page = Math.clamp(page, 0, pages - 1);
        if (view.isEmpty()) {
            StringWidget sw = new StringWidget(Component.literal("§7No layout matches."), font);
            sw.setPosition(cx - font.width("No layout matches.") / 2, y + 20);
            addRenderableWidget(sw);
        }
        for (LayoutRow r : LayoutBrowserModel.page(view, page, pageSize)) {
            String text = font.plainSubstrByWidth(r.line(), w - 12);
            String chosen = parentChoice();
            addRenderableWidget(Button.builder(Component.literal(r.id.equals(chosen) ? "§a§n" + text : text), b -> { parent.chose(r.id); mc.setScreen(parent); })
                    .tooltip(tip(detail(r))).bounds(cx - w / 2, y, w, 20).build());
            y += ROW;
        }
        int by = height - 30;
        Button prev = Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(cx - w / 2, by, 24, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);
        StringWidget pl = new StringWidget(Component.literal("§7Page " + (page + 1) + " of " + pages + ", " + view.size() + " layouts"), font);
        pl.setPosition(cx - font.width("Page " + (page + 1) + " of " + pages + ", " + view.size() + " layouts") / 2, by + 6);
        addRenderableWidget(pl);
        Button nextB = Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(cx + w / 2 - 24, by, 24, 20).build();
        nextB.active = page < pages - 1;
        addRenderableWidget(nextB);
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(cx + w / 2 - 24 - 62, by, 56, 20).build());
    }

    private String parentChoice() { return RunStartScreen.currentChoice(); }

    private String detail(LayoutRow r) {
        List<String> out = new ArrayList<>();
        out.add(r.id);
        String puzzles = String.join(", ", r.puzzles);
        out.add("Puzzles: " + (puzzles.isEmpty() ? "none" : puzzles) + ".");
        if (!r.party.isEmpty()) out.add("Recorded party: " + String.join(", ", r.party) + ".");
        out.add("Recorded time: " + r.time() + ".");
        if (!r.inPool) out.add("Not recorded completely: it may not build or may play oddly.");
        return String.join(" ", out);
    }

    private List<LayoutRow> loadRows() {
        PackSet set = Packs.current;
        if (set == null) {
            message = null;
            Packs.loadAsync().thenAccept(s -> Minecraft.getInstance().execute(() -> { if (Minecraft.getInstance().screen == this) rebuildWidgets(); }));
            return null;
        }
        LayoutPack pack = set.layouts;
        if (pack == null) {
            String joined = String.join(" ", set.userLines());
            message = joined.isEmpty() ? "The layout pool is not there." : joined;
            return null;
        }
        List<LayoutRow> r = LayoutBrowserModel.rows(pack);
        rows = r;
        return r;
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    private Tooltip tip(String text) { return Tooltip.create(Component.literal(text)); }

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

    private static final int ROW = 22;
    private static String lastQuery = "";
    private static LayoutBrowserModel.Sort lastSort = LayoutBrowserModel.Sort.NEWEST;
    private static boolean lastPoolOnly = true;
}
