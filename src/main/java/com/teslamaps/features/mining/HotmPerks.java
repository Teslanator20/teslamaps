package com.teslamaps.features.mining;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Heart of the Mountain: enabled / disabled perks tinted behind the icon, same as the HotF helper. */
public class HotmPerks {

    private static final Pattern NOT_UNLOCKED = Pattern.compile("Requires.*|.*Mountain!|Click to unlock!");
    private static final int NONE = 0, ENABLED = 1, DISABLED = 2;

    private static Feature feature;
    private static Screen cachedScreen;
    private static boolean cachedIn;
    private static final java.util.Map<ItemLore, Integer> stateCache = new java.util.IdentityHashMap<>();

    public static void register() {
        feature = Feature.of("HotmPerks").section("Mining").when(() -> TeslaMapsConfig.get().hotmHighlight)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("HotM Perk Highlight", () -> c.hotmHighlight, v -> c.hotmHighlight = v)
                            .desc("Tints enabled and disabled perks in the Heart of the Mountain menu.");
                    b.color("  Enabled Color", () -> c.colorHotmEnabled, v -> c.colorHotmEnabled = v);
                    b.color("  Disabled Color", () -> c.colorHotmDisabled, v -> c.colorHotmDisabled = v);
                    HotmPowderGoal.config(b);
                });
    }

    private static boolean inMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != cachedScreen) {
            cachedScreen = mc.screen;
            stateCache.clear();
            cachedIn = mc.screen instanceof AbstractContainerScreen<?> cs
                    && StringUtil.stripTrim(cs.getTitle().getString()).startsWith("Heart of the Mountain");
        }
        return cachedIn;
    }

    public static void drawHighlight(GuiGraphicsExtractor ctx, Slot slot) {
        if (feature == null || !TeslaMapsConfig.get().hotmHighlight || !inMenu() || !feature.active()) return;
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory) return;
        ItemStack item = slot.getItem();
        if (item.isEmpty()) return;
        ItemLore raw = item.get(DataComponents.LORE);
        if (raw == null) return;

        Integer state = stateCache.get(raw);
        if (state == null) {
            state = state(item);
            stateCache.put(raw, state);
        }
        if (state == NONE) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String hex = state == ENABLED ? c.colorHotmEnabled : c.colorHotmDisabled;
        ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFF000000 | (TeslaMapsConfig.parseColor(hex) & 0xFFFFFF));
    }

    private static int state(ItemStack item) {
        String name = StringUtil.stripTrim(item.getHoverName().getString());
        if (name.startsWith("Heart of the Mountain") || name.startsWith("Reset Heart of the Mountain")) return NONE;
        List<String> lore = ItemUtil.loreLines(item);
        if (lore.isEmpty()) return NONE;
        if (NOT_UNLOCKED.matcher(lore.get(lore.size() - 1).trim()).matches()) return NONE;
        boolean enabled = false, disabled = false;
        for (String line : lore) {
            String l = line.trim();
            if (l.equals("ENABLED") || l.equals("SELECTED")) enabled = true;
            else if (l.equals("DISABLED")) disabled = true;
        }
        if (enabled) return ENABLED;
        if (disabled) return DISABLED;
        return NONE;
    }
}
