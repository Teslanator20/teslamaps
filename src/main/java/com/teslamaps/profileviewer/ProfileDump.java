package com.teslamaps.profileviewer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import com.teslamaps.api.HypixelApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the raw profile json to disk. Hypixel renames fields often enough that guessing
 * them from the outside is a waste of time.
 */
public class ProfileDump {
    public static void dump(String player) {
        HypixelApi.nameToUuid(player).thenCompose(uuid ->
                uuid == null ? java.util.concurrent.CompletableFuture.completedFuture(null) : HypixelApi.getProfiles(uuid)
        ).thenAccept(response -> Minecraft.getInstance().execute(() -> {
            if (response == null) {
                send("§cCould not resolve " + player);
                return;
            }
            try {
                Path dir = FabricLoader.getInstance().getConfigDir().resolve("teslamaps");
                Files.createDirectories(dir);
                Path file = dir.resolve("pv_dump_" + player.toLowerCase() + ".json");
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(response));
                send("§7Wrote §f" + file.getFileName() + "§7 to config/teslamaps");

                // museum and garden come from separate routes, dump them too
                String profileId = firstProfileId(response);
                if (profileId != null) {
                    dumpExtra(dir, player, "museum", HypixelApi.getMuseum(profileId));
                    dumpExtra(dir, player, "garden", HypixelApi.getGarden(profileId));
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to dump profile", e);
                send("§cDump failed: " + e.getMessage());
            }
        }));
    }

    private static String firstProfileId(JsonObject response) {
        if (!response.has("profiles")) return null;
        JsonObject profiles = response.getAsJsonObject("profiles");
        String fallback = null;
        for (String key : profiles.keySet()) {
            JsonObject entry = profiles.getAsJsonObject(key);
            String id = entry.has("profile_id") ? entry.get("profile_id").getAsString() : null;
            if (id == null) continue;
            if (fallback == null) fallback = id;
            if (entry.has("current") && entry.get("current").getAsBoolean()) return id;
        }
        return fallback;
    }

    private static void dumpExtra(Path dir, String player, String kind,
                                  java.util.concurrent.CompletableFuture<JsonObject> future) {
        future.thenAccept(data -> Minecraft.getInstance().execute(() -> {
            try {
                Path file = dir.resolve("pv_dump_" + player.toLowerCase() + "_" + kind + ".json");
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(data));
                send("§7Wrote §f" + file.getFileName());
            } catch (Exception e) {
                send("§c" + kind + " dump failed: " + e.getMessage());
            }
        }));
    }

    private static void send(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        }
    }
}
