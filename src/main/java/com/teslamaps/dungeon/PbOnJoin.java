package com.teslamaps.dungeon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.api.HypixelApi;
import com.teslamaps.profileviewer.data.DungeonData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When a player joins the party, looks up their dungeon personal bests (Catacombs level + best
 * F7/M7 S+ times) via the Hypixel API and prints them to chat. Requires a Hypixel API key in config.
 */
public class PbOnJoin {
    private static final Pattern JOIN = Pattern.compile("\\b(\\w{1,16}) joined the party\\.?$");

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().pbOnJoin) return;
        Matcher m = JOIN.matcher(message.trim());
        if (!m.find()) return;
        lookup(m.group(1));
    }

    private static void lookup(String name) {
        HypixelApi.nameToUuid(name)
                .thenCompose(uuid -> uuid == null ? CompletableFuture.completedFuture(null) : HypixelApi.getProfiles(uuid))
                .thenAccept(resp -> {
                    if (resp == null || !resp.has("profiles")) return;
                    JsonObject member = selectedMember(resp.getAsJsonObject("profiles"));
                    if (member == null) return;
                    DungeonData dd = new DungeonData(member);
                    String line = format(name, dd);
                    if (line != null) Minecraft.getInstance().execute(() -> send(line));
                })
                .exceptionally(e -> null);
    }

    private static JsonObject selectedMember(JsonObject profiles) {
        JsonObject fallback = null;
        for (Map.Entry<String, JsonElement> e : profiles.entrySet()) {
            JsonObject p = e.getValue().getAsJsonObject();
            JsonObject data = p.has("data") ? p.getAsJsonObject("data") : null;
            if (data == null) continue;
            if (fallback == null) fallback = data;
            if (p.has("current") && p.get("current").getAsBoolean()) return data;
        }
        return fallback;
    }

    private static String format(String name, DungeonData dd) {
        StringBuilder sb = new StringBuilder("§b[PB] §e").append(name).append("§7: §fCata ").append(dd.getCatacombsLevel());
        appendFloor(sb, "§7 | §fF7 S+ ", dd.getNormalFloors().get("7"));
        appendFloor(sb, "§7 | §fM7 S+ ", dd.getMasterFloors().get("7"));
        appendFloor(sb, "§7 | §fM6 S+ ", dd.getMasterFloors().get("6"));
        return sb.toString();
    }

    private static void appendFloor(StringBuilder sb, String prefix, DungeonData.FloorData floor) {
        if (floor != null && floor.getFastestSPlusTime() > 0) {
            sb.append(prefix).append("§a").append(floor.getFormattedSPlusTime());
        }
    }

    private static void send(String line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(line));
    }
}
