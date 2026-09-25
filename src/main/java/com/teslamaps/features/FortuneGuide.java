package com.teslamaps.features;

// After SkyHanni's /ff (GardenApi.kt): its guide moved to EliteSkyblock, this prints our own breakdown plus that link
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class FortuneGuide {

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private FortuneGuide() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FortuneGuide")
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Farming Fortune Guide");
                    b.toggle("/ff Fortune Guide", () -> c.fortuneGuide, v -> c.fortuneGuide = v)
                            .desc("/ff or /tmap ff: your fortune from the tab list, held tool and armor, the latest fortune per crop, "
                                    + "and a link to your EliteSkyblock fortune page with the cheapest upgrades.");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(ClientCommands.literal("ff").executes(c -> run(true)));
            d.register(ClientCommands.literal("tmap").then(ClientCommands.literal("ff").executes(c -> run(false))));
        });
    }

    private static int run(boolean forward) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().fortuneGuide) {
            if (forward) {
                if (mc.getConnection() != null) mc.getConnection().send(new ServerboundChatCommandPacket("ff"));
            } else {
                msg("§7Enable §f/ff Fortune Guide §7in Garden settings.");
            }
            return 1;
        }
        if (mc.player == null) return 1;
        if (!SkyblockUtils.isInSkyblock()) {
            msg("§cJoin SkyBlock to open the fortune guide!");
            return 1;
        }
        StringBuilder b = new StringBuilder("§6§lFarming Fortune Guide");
        boolean anyTab = false;
        for (String raw : TabListUtils.getTabListLines()) {
            String l = StringUtil.stripTrim(raw);
            int i = l.indexOf(" Fortune: ");
            if (i <= 0) continue;
            String stat = l.substring(0, i);
            if (!stat.equals("Farming") && CropStats.byName(stat) == null) continue;
            b.append("\n §7").append(stat).append(" Fortune: §6").append(l.substring(i + 10));
            anyTab = true;
        }
        if (!anyTab) b.append("\n §cNo fortune in the tab list - enable the Stats widget in /tab");

        b.append("\n§eHeld tool:");
        appendItem(b, mc.player.getMainHandItem());
        b.append("\n§eArmor:");
        for (EquipmentSlot slot : ARMOR) appendItem(b, mc.player.getItemBySlot(slot));

        b.append("\n§eLatest fortune per crop:");
        boolean anyCrop = false;
        for (CropStats.Crop crop : CropStats.CROPS) {
            Double ff = CropStats.latestFortune(crop);
            if (ff == null) continue;
            anyCrop = true;
            Double bps = CropStats.latestBps(crop);
            b.append("\n §7").append(crop.name()).append(": §6").append(CropStats.fmt(Math.ceil(ff))).append(" ☘")
                    .append(bps == null ? "" : " §8(" + CropStats.fmt(bps) + " bps)");
        }
        if (!anyCrop) b.append("\n §7None yet - farm a crop for a few seconds");
        msg(b.toString());

        String name = mc.player.getGameProfile().name();
        String profile = SbProfile.name();
        String url = "https://eliteskyblock.com/@" + name + "/"
                + (profile == null || profile.isEmpty() ? "" : URLEncoder.encode(profile, StandardCharsets.UTF_8) + "/") + "fortune#fortune";
        MutableComponent link = Component.literal("§b[TeslaMaps] §6Click §bhere §6for your fortune progress and cheapest upgrades on EliteSkyblock")
                .withStyle(s -> s.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7" + url))));
        mc.player.sendSystemMessage(link);
        return 1;
    }

    private static void appendItem(StringBuilder b, ItemStack stack) {
        if (stack.isEmpty()) {
            b.append("\n §8-");
            return;
        }
        String line = null;
        for (String raw : ItemUtil.loreLines(stack)) {
            String l = StringUtil.stripTrim(raw);
            if (l.startsWith("Farming Fortune: ")) {
                line = l.substring(17);
                break;
            }
        }
        b.append("\n §f").append(StringUtil.strip(stack.getHoverName().getString()))
                .append(line == null ? " §8(no fortune)" : " §6" + line);
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + s));
    }
}
