package com.teslamaps.features;

// Ported from NotEnoughUpdates' TablistTutorial.kt: tasks = the widgets our HUDs are missing right now
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** Marks in /tab which region and widget to click for every tab widget a HUD is waiting for. */
public final class TabWidgetGuide {

    private record Mark(int x, int y, int color, String label) {}

    private static final List<String> missing = new ArrayList<>();
    private static List<Mark> marks = List.of();
    private static List<String> panel = List.of();
    private static String area;
    private static int ticks;

    private TabWidgetGuide() {}

    public static void register() {
        com.teslamaps.registry.Feature.of("TabWidgetGuide")
                .when(() -> TeslaMapsConfig.get().tabWidgetGuide)
                .tick(TabWidgetGuide::tick);
    }

    /** Label and command button, added under the Missing Tab Widget Hint toggle. */
    public static void config(com.teslamaps.screen.config.ConfigBuilder b, TeslaMapsConfig c) {
        b.toggle("  Setup Guide In /tab", () -> c.tabWidgetGuide, v -> c.tabWidgetGuide = v)
                .desc("While a HUD waits for a widget (Pet, Bestiary, Pickaxe Ability), /tab marks the area and the widget to click and lists what is still missing.")
                .tags("tab", "widget", "tutorial", "setup");
        b.button("    Open /tab", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return;
            mc.setScreen(null);
            mc.getConnection().sendCommand("tab");
        });
    }

    private static void collectMissing() {
        missing.clear();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.petOverlay && com.teslamaps.features.pets.PetTracker.widgetMissing()) missing.add("Pet");
        if (BestiaryTracker.tabWidgetMissing()) missing.add("Bestiary");
        if (c.pickaxeAbilityBar && com.teslamaps.features.mining.PickaxeAbility.tabWidgetMissing()) missing.add("Pickaxe Ability");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            marks = List.of();
            panel = List.of();
            if (++ticks % 20 == 0) collectMissing();
            return;
        }
        if (++ticks % 5 != 0) return;
        String title = StringUtil.strip(cs.getTitle().getString()).trim();
        boolean main = title.equals("Tablist Widgets");
        String region = regionName(title);
        if ((!main && region == null) || missing.isEmpty()) {
            marks = List.of();
            panel = List.of();
            return;
        }
        List<Mark> out = new ArrayList<>();
        List<Slot> slots = cs.getMenu().slots;
        if (main) {
            for (Slot s : slots) {
                if (s.index != 4 || s.getItem().isEmpty()) continue;
                List<String> lore = ItemUtil.loreLines(s.getItem());
                if (lore.isEmpty()) continue;
                String last = lore.getLast().trim();
                if (last.startsWith("Click to edit ") && last.endsWith(" settings!"))
                    area = last.substring("Click to edit ".length(), last.length() - " settings!".length()).trim();
            }
            for (Slot s : slots) {
                if (s.getItem().isEmpty()) continue;
                String r = regionName(StringUtil.strip(s.getItem().getHoverName().getString()).trim());
                if (r != null && r.equals(area)) out.add(new Mark(s.x, s.y, 0xFF55FF55, "Click"));
            }
        } else if (area != null && !region.equals(area)) {
            for (Slot s : slots) if (s.index == 48 && !s.getItem().isEmpty()) out.add(new Mark(s.x, s.y, 0xFFFF5555, "Back"));
        } else {
            for (Slot s : slots) {
                if (s.getItem().isEmpty()) continue;
                String name = StringUtil.strip(s.getItem().getHoverName().getString()).trim();
                if (!name.endsWith(" Widget") || name.length() < 9) continue;
                boolean off = name.startsWith("✖");
                boolean on = name.startsWith("✔");
                if (!off && !on) continue;
                String widget = name.substring(1, name.length() - " Widget".length()).trim();
                if (!missing.contains(widget)) continue;
                out.add(off ? new Mark(s.x, s.y, 0xFF55FF55, "Click")
                        : new Mark(s.x, s.y, 0xFFFFFF55, "On - raise priority"));
            }
        }
        marks = out;
        List<String> lines = new ArrayList<>();
        lines.add("§eTab Widget Setup");
        lines.add(area != null ? "§7Area: §f" + area : "§7Pick the area you are in");
        for (String w : missing) lines.add("§c✖ §f" + w);
        panel = lines;
    }

    private static String regionName(String label) {
        if (!label.startsWith("Widgets")) return null;
        String r = label.substring("Widgets".length()).trim();
        for (String p : new String[]{"in ", "on ", "the "}) if (r.startsWith(p)) r = r.substring(p.length());
        return r.equals("Private Islands") ? "Private Island" : r;
    }

    /** From ContainerOverlays. */
    public static void render(GuiGraphicsExtractor ctx) {
        if (panel.isEmpty() || !TeslaMapsConfig.get().tabWidgetGuide) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Mark m : marks) {
            int x = acc.getX() + m.x(), y = acc.getY() + m.y();
            ctx.fill(x, y, x + 16, y + 16, (m.color() & 0x00FFFFFF) | 0x66000000);
            ctx.fill(x - 1, y - 1, x + 17, y, m.color());
            ctx.fill(x - 1, y + 16, x + 17, y + 17, m.color());
            ctx.fill(x - 1, y, x, y + 16, m.color());
            ctx.fill(x + 16, y, x + 17, y + 16, m.color());
            ctx.text(mc.font, m.label(), x + 8 - mc.font.width(m.label()) / 2, y - 10, m.color(), true);
        }
        int w = 0;
        for (String l : panel) w = Math.max(w, mc.font.width(l));
        int px = acc.getX() - w - 12, py = acc.getY();
        if (px < 4) px = acc.getX() + acc.getImageWidth() + 8;
        HudStyle.panel(ctx, px - 4, py - 4, w + 8, panel.size() * 10 + 6);
        for (int i = 0; i < panel.size(); i++) ctx.text(mc.font, panel.get(i), px, py + i * 10, 0xFFFFFFFF, true);
    }
}
