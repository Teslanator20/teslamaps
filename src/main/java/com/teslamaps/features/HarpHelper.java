package com.teslamaps.features;

// Ported from SkyHanni's HarpFeatures.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public class HarpHelper {

    private static final int FIRST_BUTTON = 37;
    private static final int CLOSE_SLOT = 40;
    private static final String[] LABELS = new String[7];

    private static Feature feature;
    private static long lastClick;
    private static long menuOpenedAt;
    private static boolean scaled;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HarpHelper").section("Inventory").when(() -> cfg().harpHelper)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Harp");
                    b.toggle("Harp Helper", () -> c.harpHelper, v -> c.harpHelper = v)
                            .desc("Melody's Harp: one key per note column (200 ms lock), key names on the buttons.");
                    for (int i = 0; i < 7; i++) {
                        final int n = i;
                        b.keybind("  Key " + (i + 1), () -> c.harpKeys[n], v -> c.harpKeys[n] = v);
                    }
                    b.toggle("  Key Names On Buttons", () -> c.harpKeyLabels, v -> c.harpKeyLabels = v);
                    b.toggle("  Auto GUI Scale", () -> c.harpGuiScale, v -> c.harpGuiScale = v)
                            .desc("Largest GUI scale while the harp is open, restored on close.");
                    b.toggle("  Quick Restart", () -> c.harpQuickRestart, v -> c.harpQuickRestart = v)
                            .desc("Clicking Close within 2s of the Melody menu opening restarts the selected song.");
                    b.toggle("  Hide Note Tooltips", () -> c.harpHideTooltips, v -> c.harpHideTooltips = v);
                });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(HarpHelper::unscale));
    }

    private static boolean on() {
        return feature != null && feature.active();
    }

    private static String title(Screen s) {
        return s instanceof AbstractContainerScreen<?> cs ? StringUtil.stripTrim(cs.getTitle().getString()) : "";
    }

    private static boolean isHarp(String t) { return t.startsWith("Harp -"); }
    private static boolean isMenu(String t) { return t.equals("Melody"); }

    /** From setScreen TAIL. */
    public static void onScreenSet(Screen screen) {
        String t = title(screen);
        boolean harpish = on() && (isHarp(t) || isMenu(t));
        if (harpish && isMenu(t)) menuOpenedAt = System.currentTimeMillis();
        boolean want = harpish && cfg().harpGuiScale;
        if (want == scaled) return;
        scaled = want;
        Minecraft.getInstance().resizeGui();
    }

    private static void unscale() {
        if (!scaled) return;
        scaled = false;
        Minecraft.getInstance().resizeGui();
    }

    /** 0 = auto (largest) while the harp is open, -1 = leave it. */
    public static int guiScaleOverride() {
        return scaled ? 0 : -1;
    }

    public static boolean onKey(AbstractContainerScreen<?> cs, int key) {
        if (key == -1 || !on() || !isHarp(title(cs))) return false;
        int[] keys = cfg().harpKeys;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != key) continue;
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode == null || mc.player == null) return true;
            long now = System.currentTimeMillis();
            if (now - lastClick < 200) return true;
            lastClick = now;
            int slot = FIRST_BUTTON + i;
            if (slot >= cs.getMenu().slots.size()) return true;
            mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, 2, ContainerInput.CLONE, mc.player);
            return true;
        }
        return false;
    }

    /** Close button in the Melody menu right after it opened = play the selected song again. */
    public static boolean onSlotClick(AbstractContainerScreen<?> cs, Slot slot, int button, ContainerInput type) {
        if (slot == null || slot.index != CLOSE_SLOT || !on() || !cfg().harpQuickRestart) return false;
        if (!isMenu(title(cs)) || System.currentTimeMillis() - menuOpenedAt > 2000) return false;
        List<Slot> slots = cs.getMenu().slots;
        for (Slot s : slots) {
            if (s.container instanceof Inventory || s.getItem().isEmpty()) continue;
            for (String l : ItemUtil.loreLines(s.getItem())) {
                if (!StringUtil.stripTrim(l).equals("Song is selected!")) continue;
                Minecraft mc = Minecraft.getInstance();
                if (mc.gameMode == null || mc.player == null) return false;
                mc.gameMode.handleContainerInput(cs.getMenu().containerId, s.index, button, type, mc.player);
                return true;
            }
        }
        return false;
    }

    public static boolean hideTooltip(AbstractContainerScreen<?> cs, Slot hovered) {
        return hovered != null && !(hovered.container instanceof Inventory)
                && on() && cfg().harpHideTooltips && isHarp(title(cs));
    }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !on() || !cfg().harpKeyLabels) return;
        if (!isHarp(title(cs))) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        int[] keys = cfg().harpKeys;
        for (int i = 0; i < 7; i++) {
            int idx = FIRST_BUTTON + i;
            if (idx >= slots.size()) break;
            Slot s = slots.get(idx);
            if (s.getItem().isEmpty()) continue;
            String label = LABELS[i];
            if (label == null || !label.startsWith(keys[i] + ":")) {
                String name = keys[i] == -1 ? "" : KeyNames.display(keys[i]);
                label = LABELS[i] = keys[i] + ":" + (name.length() > 3 ? name.substring(0, 3) : name);
            }
            String text = label.substring(label.indexOf(':') + 1);
            if (text.isEmpty()) continue;
            int x = acc.getX() + s.x + 17 - mc.font.width(text);
            int y = acc.getY() + s.y + 9;
            ctx.text(mc.font, text, x, y, 0xFFFFFFFF, true);
        }
    }
}
