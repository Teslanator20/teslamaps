package com.teslamaps.features.npc;

// Ported from bax's VisitorLogbook.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/** Visitor's Logbook: a key over a visitor tracks it with the NPC Finder, or warps to its island. */
public final class VisitorLogbook {

    private static final String TITLE = "Visitor's Logbook";

    private static Feature feature;

    private VisitorLogbook() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("VisitorLogbook").section("Render").when(() -> cfg().npcFinder && cfg().visitorLogbookNav);
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (lines.isEmpty() || !inLogbook() || !active()) return;
            if (find(StringUtil.stripTrim(stack.getHoverName().getString())) == null) return;
            lines.add(Component.empty());
            lines.add(Component.literal("§bPress " + KeyNames.display(cfg().visitorLogbookKey) + " to navigate"));
        });
    }

    private static boolean active() {
        return feature != null && feature.active() && SkyblockUtils.isInSkyblock();
    }

    private static boolean inLogbook() {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).equals(TITLE);
    }

    private static NpcData find(String name) {
        if (name.isEmpty()) return null;
        for (NpcData n : NpcDatabase.getUnique()) {
            if (n.hasPosition() && n.name().equalsIgnoreCase(name)) return n;
        }
        return null;
    }

    /** From ContainerKeyMixin. True = key used. */
    public static boolean onKey(int key, Slot hovered) {
        if (key == -1 || key != cfg().visitorLogbookKey || hovered == null || !hovered.hasItem()) return false;
        if (hovered.container instanceof Inventory || !inLogbook() || !active()) return false;
        NpcData npc = find(StringUtil.stripTrim(hovered.getItem().getHoverName().getString()));
        if (npc == null) return false;
        Minecraft mc = Minecraft.getInstance();
        List<NpcData> positions = NpcDatabase.getPositions(npc.name(), npc.island());
        NpcFinder.setTargets(positions);
        String suffix = " (" + positions.size() + " location" + (positions.size() > 1 ? "s" : "") + ")";
        if (NpcFinder.isOnTargetIsland(npc)) {
            msg("§7Tracking §f" + npc.name() + "§7" + suffix);
        } else {
            NpcWarps.warp(NpcWarps.warpFor(npc), true);
            msg("§7Warping to §f" + npc.name() + "§7 on §f" + npc.island() + "§7" + suffix);
        }
        mc.setScreen(null);
        return true;
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
