package com.teslamaps.features;

// Ported from Skysoft's CrownValueOverlay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrownValueOverlay {

    private static final Pattern VALUE = Pattern.compile("^👑(\\d[\\d,]*) Crowns?$");
    private static final String CROWN = "👑";
    private static final Map<List<String>, String> CACHE = new IdentityHashMap<>();

    private static Feature feature;
    private static boolean inRavengard;
    private static int ticks;

    private CrownValueOverlay() {}

    public static void register() {
        feature = Feature.of("CrownValueOverlay")
                .when(() -> TeslaMapsConfig.get().ravengardCrownValues)
                .tick(CrownValueOverlay::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Ravengard Crown Values", () -> c.ravengardCrownValues, v -> c.ravengardCrownValues = v)
                            .desc("In Ravengard, the crown value from an item's lore sits small in the slot's top left corner.");
                });
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        inRavengard = SkyblockUtils.isOnHypixel() && !SkyblockUtils.isInSkyblock();
        if (Minecraft.getInstance().screen == null && !CACHE.isEmpty()) CACHE.clear();
    }

    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if (!inRavengard || !TeslaMapsConfig.get().ravengardCrownValues || !feature.active()) return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;
        List<String> lore = ItemUtil.loreLines(stack);
        if (lore.isEmpty()) return;
        String value = CACHE.get(lore);
        if (value == null) {
            if (CACHE.size() > 512) CACHE.clear();
            value = "";
            for (String l : lore) {
                Matcher m = VALUE.matcher(l.trim());
                if (m.matches()) { value = m.group(1); break; }
            }
            CACHE.put(lore, value);
        }
        if (value.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        float scale = Math.min(0.5f, 15f / Math.max(1, font.width(CROWN + value)));
        ctx.pose().pushMatrix();
        ctx.pose().translate(slot.x, slot.y);
        ctx.pose().scale(scale, scale);
        ctx.text(font, CROWN, 0, 0, 0xFFFFFFFF, true);
        ctx.text(font, value, font.width(CROWN), 0, 0xFFFFCE47, true);
        ctx.pose().popMatrix();
    }
}
