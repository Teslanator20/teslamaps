package com.teslamaps.features.garden;

// After GanKura's VisitorHandler.colorFor
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/** Accept Offer green when Hypixel says nothing is missing (sacks included), red otherwise. */
public final class VisitorOffer {

    private static Feature feature;
    private static int slot = -1, color;
    private static int ticks;

    private VisitorOffer() {}

    public static void register() {
        feature = Feature.of("VisitorOfferColor").section("Garden")
                .when(() -> TeslaMapsConfig.get().visitorOfferColor)
                .tick(VisitorOffer::tick);
    }

    private static void tick() {
        if (++ticks % 4 != 0) return;
        slot = -1;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !SkyblockUtils.isInGarden()) return;
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s.container instanceof Inventory || !s.hasItem()) continue;
            if (!StringUtil.stripTrim(s.getItem().getHoverName().getString()).equals("Accept Offer")) continue;
            boolean missing = false;
            for (String l : ItemUtil.loreLines(s.getItem())) {
                if (l.trim().startsWith("Missing items")) { missing = true; break; }
            }
            slot = i;
            color = missing ? 0xFFFF5555 : 0xFF55FF55;
            return;
        }
    }

    /** From ContainerOverlays. */
    public static void render(GuiGraphicsExtractor g) {
        if (slot < 0 || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        List<Slot> slots = cs.getMenu().slots;
        if (slot >= slots.size()) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        Slot s = slots.get(slot);
        int x = acc.getX() + s.x, y = acc.getY() + s.y;
        int rgb = color & 0xFFFFFF;
        g.fill(x, y, x + 16, y + 16, 0x66000000 | rgb);
        g.fill(x, y, x + 16, y + 1, color);
        g.fill(x, y + 15, x + 16, y + 16, color);
        g.fill(x, y, x + 1, y + 16, color);
        g.fill(x + 15, y, x + 16, y + 16, color);
    }
}
