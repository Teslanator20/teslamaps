package com.teslamaps.features.crimson;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

/** /tmap crimson vanqshare | represet | matriarchdone */
public final class CrimsonCommands {

    private CrimsonCommands() {}

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("crimson")
                .then(ClientCommands.literal("vanqshare").executes(c -> {
                    Minecraft.getInstance().execute(VanquisherAlert::share);
                    return 1;
                }))
                .then(ClientCommands.literal("represet").executes(c -> {
                    Minecraft.getInstance().execute(ReputationHelper::reset);
                    return 1;
                }))
                .then(ClientCommands.literal("matriarchdone").executes(c -> {
                    Minecraft.getInstance().execute(MatriarchHelper::markDone);
                    return 1;
                }));
    }
}
