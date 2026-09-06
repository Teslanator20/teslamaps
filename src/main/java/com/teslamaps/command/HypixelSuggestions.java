package com.teslamaps.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Tab completion for Hypixel's own commands. Hypixel sends no suggestions for
 * {@code /warp} or {@code /call}, so we register client-side commands of the same
 * name that offer the list and then forward the line to the server untouched.
 * <p>
 * Forwarding is what keeps this safe: whatever the player types still reaches
 * Hypixel verbatim, so a name we do not know about — or a future one — behaves
 * exactly as it did before.
 */
public class HypixelSuggestions {

    private static final List<String> WARPS = load("warps.txt");
    private static final List<String> CONTACTS = load("contacts.txt");

    private static List<String> load(String file) {
        List<String> out = new ArrayList<>();
        try (InputStream in = HypixelSuggestions.class.getResourceAsStream(
                "/assets/teslamaps/suggestions/" + file)) {
            if (in == null) return out;
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
        } catch (IOException e) {
            TeslaMaps.LOGGER.warn("Could not read suggestion list {}", file, e);
        }
        return out;
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        forwarding(dispatcher, "warp", WARPS);
        forwarding(dispatcher, "call", CONTACTS);
        // No client command for /gfs: Hypixel declares that name itself, and registering
        // over it broke the merge into the server dispatcher, which killed the /warp and
        // /call suggestions too. The queue runs off ALLOW_COMMAND instead.
    }


    private static void forwarding(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                   String name, List<String> options) {
        dispatcher.register(ClientCommands.literal(name)
                .executes(context -> send(name))
                .then(ClientCommands.argument("argument", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggest(options, builder))
                        .executes(context -> send(name + " "
                                + StringArgumentType.getString(context, "argument")))));
    }

    private static CompletableFuture<Suggestions> suggest(List<String> options, SuggestionsBuilder builder) {
        // Off-island the lists would only be noise on servers with their own /warp.
        boolean allowed = SkyblockUtils.isInSkyblock()
                || net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
        if (!TeslaMapsConfig.get().commandSuggestions || !allowed) return builder.buildFuture();
        String written = builder.getRemaining().toLowerCase(Locale.ROOT);

        // Once the typed word IS one of the options, suggest nothing. While the popup
        // is open Enter applies the highlighted entry instead of sending the command,
        // so a name that suggests itself ("/warp canyon") could never be submitted.
        for (String option : options) {
            if (option.equalsIgnoreCase(written)) return builder.buildFuture();
        }

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(written)) builder.suggest(option);
        }
        return builder.buildFuture();
    }

    /**
     * Sends the command as a raw packet on purpose. {@code sendCommand} is what the
     * Fabric command API hooks to run client commands — forwarding through it would
     * land straight back in this executor and recurse until the client dies.
     */
    private static int send(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundChatCommandPacket(command));
        }
        return 1;
    }
}
