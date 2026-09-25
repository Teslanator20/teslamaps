package com.teslamaps.features;

// Ported from Skysoft's HideSillyButtons.kt / HiddenMenuLayout.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HideSillyButtons {

    private static final int PAUSE_WIDTH = 204, GAP = 4;

    private static final String[] KEYS = {"menu.online", "options.accessibility", "options.language", "gui.friends.open",
            "menu.playerReporting", "menu.sendFeedback", "menu.reportBugs", "menu.server_links", "gui.advancements",
            "gui.stats", "menu.singleplayer"};
    private static final String[] NAMES = {"Minecraft Realms", "Accessibility Settings", "Change Language", "Friends",
            "Player Reporting", "Give Feedback", "Report Bugs", "Server Links", "Advancements",
            "Statistics", "Singleplayer"};

    public static void register() {
        Identifier phase = Identifier.fromNamespaceAndPath("teslamaps", "hide_silly_buttons");
        ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, phase);
        ScreenEvents.AFTER_INIT.register(phase, (mc, screen, w, h) -> {
            if (!TeslaMapsConfig.get().hideSillyButtons) return;
            if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) return;
            try {
                apply(screen);
            } catch (Exception e) {
                com.teslamaps.TeslaMaps.LOGGER.warn("[TeslaMaps] hide silly buttons: {}", e.toString());
            }
        });

        Feature.of("HideSillyButtons").config("Menu & Skybox", b -> {
            TeslaMapsConfig c = b.config();
            b.label("Menu Buttons");
            b.toggle("Hide Silly Buttons", () -> c.hideSillyButtons, v -> c.hideSillyButtons = v)
                    .desc("Removes the picked buttons from the title and pause menu and re-centres the rest.")
                    .tags("pause", "menu", "realms", "feedback", "buttons");
            b.searchList("  Hidden Buttons", () -> List.of(NAMES),
                    name -> c.hideSillyButtonList.contains(keyOf(name)),
                    name -> { String k = keyOf(name); if (!c.hideSillyButtonList.remove(k)) c.hideSillyButtonList.add(k); });
        });
    }

    private static String keyOf(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return KEYS[i];
        return name;
    }

    private static String key(AbstractWidget w) {
        return w.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }

    private static void apply(Screen screen) {
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        int mid = screen.width / 2;
        List<AbstractWidget> menu = new ArrayList<>();
        for (AbstractWidget w : widgets) {
            if (!w.visible || w.getHeight() != Button.DEFAULT_HEIGHT || w.getY() < 0 || w.getY() >= screen.height) continue;
            if (w.getX() > mid + 110 || w.getX() + w.getWidth() < mid - 110) continue;
            menu.add(w);
        }
        if (!widgets.removeIf(HideSillyButtons::shouldHide)) return;
        if (!menu.isEmpty()) compact(screen, widgets, menu);
        if (screen.getFocused() != null && !screen.children().contains(screen.getFocused())) screen.setFocused(null);
    }

    private static boolean shouldHide(AbstractWidget w) {
        String k = key(w);
        if (k == null) return false;
        List<String> hidden = TeslaMapsConfig.get().hideSillyButtonList;
        if (k.equals("menu.feedback")) return hidden.contains("menu.sendFeedback") && hidden.contains("menu.reportBugs");
        if (!hidden.contains(k)) return false;
        if (k.equals("options.accessibility") || k.equals("options.language")) return w.getWidth() == w.getHeight();
        return true;
    }

    private static void compact(Screen screen, List<AbstractWidget> widgets, List<AbstractWidget> original) {
        int width = screen instanceof PauseScreen ? PAUSE_WIDTH : Button.BIG_WIDTH;
        Map<Integer, List<AbstractWidget>> byY = new TreeMap<>();
        int minY = Integer.MAX_VALUE;
        for (AbstractWidget w : original) {
            minY = Math.min(minY, w.getY());
            if (widgets.contains(w)) byY.computeIfAbsent(w.getY(), k -> new ArrayList<>()).add(w);
        }
        List<List<AbstractWidget>> rows = new ArrayList<>(byY.values());
        if (rows.isEmpty()) return;
        for (List<AbstractWidget> row : rows) row.sort((a, b) -> Integer.compare(a.getX(), b.getX()));
        int total = GAP * (rows.size() - 1);
        for (List<AbstractWidget> row : rows) total += rowHeight(row);
        int y = Math.max((screen.height - total) / 2, minY);
        for (List<AbstractWidget> row : rows) {
            if (row.size() == 1 && row.get(0).getWidth() > row.get(0).getHeight()) row.get(0).setWidth(width);
            int rowWidth = GAP * (row.size() - 1);
            for (AbstractWidget w : row) rowWidth += w.getWidth();
            int x = screen.width / 2 - rowWidth / 2;
            for (AbstractWidget w : row) {
                w.setPosition(x, y);
                x += w.getWidth() + GAP;
            }
            y += rowHeight(row) + GAP;
        }
    }

    private static int rowHeight(List<AbstractWidget> row) {
        int h = 0;
        for (AbstractWidget w : row) h = Math.max(h, w.getHeight());
        return h;
    }
}
