package com.teslamaps.features.pets;

// After Skysoft's PetExpShareTooltip.kt + PetStorageExpSharing.kt, pet colouring after SkyblockExtras' "Show Exp-Shared Pets"
import com.teslamaps.api.ElectionApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Exp Sharing menu: disabled slots marked; Pets menu: exp-shared pets tinted. */
public final class ExpShareSlots {

    private static final int[] SLOTS = {30, 31, 32};

    private static Feature feature;
    private static AbstractContainerScreen<?> lastScreen;
    private static boolean inExpShare, inPets;
    private static int ticks;
    private static int tint;

    private ExpShareSlots() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ExpShareSlots").section("Skills")
                .when(() -> cfg().expShareDisabledSlots || cfg().expSharePetHighlight)
                .tick(ExpShareSlots::tick)
                .config("Skills", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Mark Disabled Exp Share Slots", () -> c.expShareDisabledSlots, v -> c.expShareDisabledSlots = v)
                            .desc("Exp Sharing menu: slots 2 and 3 only work while Diana's \"Sharing is Caring\" perk is active. "
                                    + "Without it they get a red tint and a tooltip line")
                            .tags("exp share", "sharing is caring", "diana", "pet");
                    b.toggle("Highlight Exp-Shared Pets", () -> c.expSharePetHighlight, v -> c.expSharePetHighlight = v)
                            .desc("Pets menu: the pets in your Exp Share slots get a coloured background. Open the Exp Sharing menu once to read them")
                            .tags("exp share", "pet", "highlight");
                    b.color("  Color", () -> c.expSharePetColor, v -> c.expSharePetColor = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!inExpShare || !cfg().expShareDisabledSlots || lines.isEmpty() || feature == null || !feature.active()) return;
            Slot slot = slotOf(stack);
            if (slot == null || !disabled(slot)) return;
            lines.add(Component.empty());
            lines.add(Component.literal("§cThis Exp Share slot is disabled."));
            lines.add(Component.literal("§7Diana's §dSharing is Caring §7perk is not active."));
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            inExpShare = inPets = false;
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            String t = StringUtil.stripTrim(cs.getTitle().getString());
            inExpShare = t.equals("Exp Sharing");
            inPets = t.startsWith("Pets") || t.contains(") Pets");
            ticks = 0;
        }
        try {
            tint = 0x60000000 | (Integer.parseUnsignedInt(cfg().expSharePetColor, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            tint = 0x605555FF;
        }
        if (!inExpShare || ticks++ % 10 != 5) return;
        List<String> read = new ArrayList<>(3);
        for (int s : SLOTS) {
            if (s >= cs.getMenu().slots.size()) return;
            ItemStack st = cs.getMenu().getSlot(s).getItem();
            if (st.isEmpty()) return;
            String uuid = PetSlotIcons.info(st).uuid();
            read.add(uuid == null ? "" : uuid);
        }
        if (!read.equals(cfg().expSharePetUuids)) {
            cfg().expSharePetUuids = read;
            TeslaMapsConfig.save();
        }
    }

    private static boolean perkKnownOff() {
        return ElectionApi.isLoaded() && !ElectionApi.hasPerk("Sharing is Caring");
    }

    private static boolean disabled(Slot slot) {
        if (slot.container instanceof Inventory) return false;
        return (slot.index == SLOTS[1] || slot.index == SLOTS[2]) && perkKnownOff();
    }

    private static Slot slotOf(ItemStack stack) {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> cs)) return null;
        for (int s : SLOTS) {
            if (s < cs.getMenu().slots.size() && cs.getMenu().getSlot(s).getItem() == stack) return cs.getMenu().getSlot(s);
        }
        return null;
    }

    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if ((!inExpShare && !inPets) || feature == null || !feature.active()) return;
        TeslaMapsConfig c = cfg();
        if (inExpShare) {
            if (c.expShareDisabledSlots && disabled(slot)) ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x70FF3333);
            return;
        }
        if (!c.expSharePetHighlight || c.expSharePetUuids.isEmpty() || slot.container instanceof Inventory) return;
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return;
        String uuid = PetSlotIcons.info(st).uuid();
        if (uuid == null) return;
        List<String> list = c.expSharePetUuids;
        for (int i = 0; i < list.size(); i++) {
            if (!uuid.equals(list.get(i))) continue;
            boolean off = i > 0 && perkKnownOff();
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, off ? 0x40888888 : tint);
            return;
        }
    }
}
