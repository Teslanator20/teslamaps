package com.teslamaps.features;

// Ported from SkyHanni's features/misc/ChargeBottleNotification.kt
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class ChargeBottleNotice {

    private static final String[] FULL = {"THUNDER_IN_A_BOTTLE", "STORM_IN_A_BOTTLE", "HURRICANE_IN_A_BOTTLE"};

    private static long lastCheck;

    private ChargeBottleNotice() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ChargeBottleNotice").section("Fishing").when(() -> cfg().chargeBottleNotice)
                .tick(ChargeBottleNotice::tick)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Charge Bottles");
                    b.toggle("Full Bottle Warning", () -> c.chargeBottleNotice, v -> c.chargeBottleNotice = v)
                            .desc("Every 10 s while fishing: chat note when a Thunder, Storm or Hurricane in a Bottle is full and no empty one is in your inventory")
                            .tags("thunder", "storm", "hurricane", "bottle", "jawbus", "fishing");
                });
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        boolean fishing = FishingHelpers.fishingRecently();
        if (now - lastCheck < 10_000) return;
        lastCheck = now;
        if (!fishing || !SkyblockUtils.isInSkyblock()) return;
        Minecraft mc = Minecraft.getInstance();
        Inventory inv = mc.player.getInventory();
        List<String> full = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = ItemUtil.skyblockId(st);
            if (id.endsWith("_IN_A_BOTTLE_EMPTY")) return;
            for (String f : FULL) {
                if (!id.equals(f)) continue;
                String name = StringUtil.strip(st.getHoverName().getString()).trim();
                if (!full.contains(name)) full.add(name);
            }
        }
        if (full.isEmpty()) return;
        String list = full.size() == 1 ? full.get(0)
                : String.join(", ", full.subList(0, full.size() - 1)) + " and " + full.get(full.size() - 1);
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §eYou are currently fishing, but §6" + list
                        + (full.size() == 1 ? " §eis full " : " §eare full "))
                .append(Component.literal("§c[Disable]").withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand("/tmap chargebottle disable"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Turn this warning off"))))));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("chargebottle")
                .then(ClientCommands.literal("disable").executes(c -> {
                    cfg().chargeBottleNotice = false;
                    TeslaMapsConfig.save();
                    c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fFull bottle warning §cdisabled"));
                    return 1;
                }));
    }
}
