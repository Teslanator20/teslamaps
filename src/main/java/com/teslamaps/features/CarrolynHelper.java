package com.teslamaps.features;

// After SkyHanni's CarrolynHelper.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.npc.NpcData;
import com.teslamaps.features.npc.NpcDatabase;
import com.teslamaps.features.npc.NpcFinder;
import com.teslamaps.features.npc.NpcWarps;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class CarrolynHelper {

    private static Feature feature;
    private static long lastUse;

    private CarrolynHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("CarrolynHelper").section("Garden").when(() -> cfg().carrolynHelper)
                .config("Garden", b -> b.toggle("Carrolyn Helper", () -> cfg().carrolynHelper, v -> cfg().carrolynHelper = v)
                        .desc("Items for Carrolyn's 3,000 quest get a hint in the tooltip. Right click one in your hand to track Carrolyn "
                                + "(NPC Finder must be on), off the Crimson Isle a chat button warps there first.")
                        .tags("carrolyn", "npc", "crimson"));
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!cfg().carrolynHelper || feature == null || !isCarrolynItem(stack) || !feature.active()) return;
            lines.add(Component.empty());
            lines.add(Component.literal("§eRight click in hand to navigate to Carrolyn!"));
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || !cfg().carrolynHelper || feature == null) return InteractionResult.PASS;
            if (!isCarrolynItem(player.getItemInHand(hand)) || !feature.active()) return InteractionResult.PASS;
            long now = System.currentTimeMillis();
            if (now - lastUse < 1000) return InteractionResult.PASS;
            lastUse = now;
            navigate(false);
            return InteractionResult.PASS;
        });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("carrolyn").executes(c -> {
                    Minecraft.getInstance().execute(() -> navigate(true));
                    return 1;
                }))));
    }

    private static boolean isCarrolynItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !SkyblockUtils.isInSkyblock()) return false;
        for (String l : ItemUtil.loreLines(stack)) {
            if (l.contains("Bring 3,000 of these to Carrolyn")) return true;
        }
        return false;
    }

    private static void navigate(boolean warp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        NpcData npc = null;
        for (NpcData n : NpcDatabase.getUnique()) {
            if (n.hasPosition() && n.name().equals("Carrolyn")) { npc = n; break; }
        }
        if (npc == null) return;
        List<NpcData> positions = NpcDatabase.getPositions(npc.name(), npc.island());
        if (NpcFinder.isOnTargetIsland(npc)) {
            NpcFinder.setTargets(positions);
            say(Component.literal("§b[TeslaMaps] §7Tracking §fCarrolyn"));
        } else if (warp) {
            NpcFinder.setTargets(positions);
            NpcWarps.warp(NpcWarps.warpFor(npc), true);
            say(Component.literal("§b[TeslaMaps] §7Warping to §fCarrolyn §7on §f" + npc.island()));
        } else {
            say(Component.literal("§b[TeslaMaps] §7Carrolyn is on the " + npc.island() + ". ")
                    .append(Component.literal("§a[Warp there]")
                            .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/tmap carrolyn")))));
        }
        if (!cfg().npcFinder) say(Component.literal("§b[TeslaMaps] §cTurn on NPC Finder to see the tracer"));
    }

    private static void say(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(c);
    }
}
