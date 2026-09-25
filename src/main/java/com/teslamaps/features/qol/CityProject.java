package com.teslamaps.features.qol;

// Ported from SkyHanni's CityProjectFeatures.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CityProject {

    private static final Pattern AMOUNT = Pattern.compile("^(.+?) x([\\d,]+)$");
    private static final Pattern AMOUNT_FIRST = Pattern.compile("^([\\d,]+) ([\\w' ]+)$");
    private static final long REPEAT = 30_000L;
    private static final int ROW = 10;

    private static int ticks;
    private static long lastReminder;
    private static List<String> panel = List.of();
    private static int panelWidth;
    private static List<Slot> ready = List.of();
    private static Feature feature;
    private static Object shownOn;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("CityProject").section("Timers")
                .when(() -> cfg().cityProjectReminder || cfg().cityProjectMaterials || cfg().cityProjectHighlight)
                .tick(CityProject::tick)
                .config("Timers", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("City Project Reminder", () -> c.cityProjectReminder, v -> c.cityProjectReminder = v)
                            .desc("Reminds you every 30s once you can contribute to the City Project again, click to warp to Elizabeth. Open the project menu once to read the time.");
                    b.toggle("  Materials Panel", () -> c.cityProjectMaterials, v -> c.cityProjectMaterials = v)
                            .desc("Materials still needed for the open components, with prices, next to the project menu.");
                    b.toggle("  Highlight Contributable", () -> c.cityProjectHighlight, v -> c.cityProjectHighlight = v)
                            .desc("Yellow frame on components you can contribute to right now.");
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs && inProject(cs)) {
            read(cs);
            return;
        }
        if (!panel.isEmpty()) panel = List.of();
        if (!ready.isEmpty()) ready = List.of();
        if (!cfg().cityProjectReminder || mc.screen != null) return;
        long now = System.currentTimeMillis();
        long next = QolStore.get().cityNext;
        if (next <= 0 || next > now || now - lastReminder < REPEAT) return;
        if (!SkyblockUtils.isInSkyblock() || DungeonManager.isInDungeon()) return;
        lastReminder = now;
        QolStore.chatWarp("§eDaily City Project Reminder!", "elizabeth");
    }

    private static boolean inProject(AbstractContainerScreen<?> cs) {
        var slots = cs.getMenu().slots;
        if (slots.size() < 5) return false;
        List<String> lore = ItemUtil.loreLines(slots.get(4).getItem());
        return !lore.isEmpty() && lore.get(0).trim().equals("City Project");
    }

    private static void read(AbstractContainerScreen<?> cs) {
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        Map<String, Long> mats = new LinkedHashMap<>();
        List<Slot> canContribute = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(st);
            if (lore.isEmpty()) continue;
            String last = lore.get(lore.size() - 1).trim();
            if (last.equals("Click to contribute!")) canContribute.add(slot);
            boolean completed = last.equals("Project is being built!") || last.equals("Project is released!");
            if (completed) continue;
            for (String l : lore) {
                String t = l.trim();
                if (!t.startsWith("Contribute again: ")) continue;
                String time = t.substring(18);
                long d = time.contains("Soon!") ? 5_000L : QolStore.parseDuration(time);
                if (d < 3_600_000L) d += 60_000L;
                next = Math.min(next, now + d);
            }
            if (!StringUtil.stripTrim(st.getHoverName().getString()).equals("Contribute this component!")) continue;
            next = now;
            readCosts(lore, mats);
        }
        QolStore.Data data = QolStore.get();
        long stored = next == Long.MAX_VALUE ? 0 : next;
        if (Math.abs(stored - data.cityNext) > 60_000L) {
            data.cityNext = stored;
            QolStore.save();
        }
        shownOn = cs;
        ready = cfg().cityProjectHighlight ? canContribute : List.of();
        panel = cfg().cityProjectMaterials ? buildPanel(mats) : List.of();
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : panel) w = Math.max(w, font.width(s));
        panelWidth = w;
    }

    private static void readCosts(List<String> lore, Map<String, Long> mats) {
        int header = -1;
        for (int i = 0; i < lore.size(); i++) if (lore.get(i).trim().startsWith("Cost")) header = i;
        if (header < 0) return;
        for (int i = header + 1; i < lore.size(); i++) {
            String t = lore.get(i).trim();
            if (t.isEmpty()) break;
            String name = t;
            long amount = 1;
            Matcher m = AMOUNT.matcher(t);
            Matcher f = AMOUNT_FIRST.matcher(t);
            if (m.matches()) { name = m.group(1).trim(); amount = Long.parseLong(m.group(2).replace(",", "")); }
            else if (f.matches()) { name = f.group(2).trim(); amount = Long.parseLong(f.group(1).replace(",", "")); }
            if (name.endsWith("Bits") || name.endsWith("Coins")) continue;
            mats.merge(name, amount, Long::sum);
        }
    }

    private static List<String> buildPanel(Map<String, Long> mats) {
        List<String> out = new ArrayList<>();
        out.add("§7City Project Materials");
        if (mats.isEmpty()) {
            out.add("§cNo materials to contribute.");
            return out;
        }
        double total = 0;
        for (Map.Entry<String, Long> e : mats.entrySet()) {
            String id = PriceManager.idForName(e.getKey());
            double price = id == null ? 0 : PriceManager.getPrice(id) * e.getValue();
            total += price;
            out.add(" §7- §f" + e.getKey() + " §ex" + e.getValue()
                    + (price > 0 ? " §7(§6" + NumberUtil.formatSuffixed(price) + "§7)" : ""));
        }
        if (total > 0) out.add("§7Total: §6" + NumberUtil.formatSuffixed(total));
        return out;
    }

    public static void render(GuiGraphicsExtractor g) {
        if (feature == null || !feature.active()) return;
        List<Slot> r = ready;
        List<String> l = panel;
        if (r.isEmpty() && l.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != shownOn || !(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot s : r) {
            int x = acc.getX() + s.x, y = acc.getY() + s.y;
            g.fill(x, y, x + 16, y + 1, 0xFFFFFF55);
            g.fill(x, y + 15, x + 16, y + 16, 0xFFFFFF55);
            g.fill(x, y, x + 1, y + 16, 0xFFFFFF55);
            g.fill(x + 15, y, x + 16, y + 16, 0xFFFFFF55);
        }
        if (l.isEmpty()) return;
        int x = acc.getX() + acc.getImageWidth() + 6, y = acc.getY() + 4;
        HudStyle.panel(g, x - 3, y - 3, panelWidth + 6, l.size() * ROW + 4);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), x, y + i * ROW, 0xFFFFFFFF, true);
    }
}
