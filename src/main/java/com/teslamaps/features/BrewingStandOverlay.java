package com.teslamaps.features;

// Ported from SkyHanni's BrewingStandOverlay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BrewingStandOverlay {

    private record Label(int slot, int dx, Component text) {}

    private static final int[] SLOTS = {13, 21, 42};

    private static List<Label> labels = List.of();
    private static int ticks = 0;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BrewingStandOverlay").section("Inventory")
                .when(() -> cfg().brewingStandOverlay)
                .tick(BrewingStandOverlay::tick);
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)
                || !StringUtil.stripTrim(cs.getTitle().getString()).equals("Brewing Stand")) {
            if (!labels.isEmpty()) labels = List.of();
            return;
        }
        List<Label> out = new ArrayList<>();
        List<Slot> slots = cs.getMenu().slots;
        for (int i : SLOTS) {
            if (i >= slots.size()) continue;
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            Component name = stack.getHoverName();
            if (name.getString().contains(" or ")) continue;
            out.add(new Label(i, (i == 21 ? 55 : 0) + 3, name));
        }
        labels = out;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (labels.isEmpty() || !cfg().brewingStandOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        for (Label l : labels) {
            if (l.slot >= slots.size()) continue;
            Slot slot = slots.get(l.slot);
            ctx.text(mc.font, l.text, acc.getX() + slot.x + 17 + l.dx, acc.getY() + slot.y + 4, 0xFFFFFFFF, true);
        }
    }
}
