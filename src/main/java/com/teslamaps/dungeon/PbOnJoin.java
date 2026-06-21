/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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

public class PbOnJoin {
    private static final Pattern JOIN = Pattern.compile("\\b(\\w{1,16}) joined the party\\.?$");

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().pbOnJoin) return;
        message = message.replaceAll("(?i)§[0-9A-FK-OR]", ""); // strip legacy color codes
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
