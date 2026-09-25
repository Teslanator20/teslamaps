package com.teslamaps.dungeon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.api.DungeonData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PbOnJoin {
    private static final Pattern JOIN = Pattern.compile("^Party Finder > (\\w{1,16}) joined the (?:dungeon )?group!");
    private static final Pattern JOIN_CLASS = Pattern.compile("\\((\\w+) Level (\\d+)\\)\\s*$");
    private static final String[] WEAPONS = {"HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE", "TERMINATOR", "DARK_CLAYMORE",
            "GIANTS_SWORD", "SOUL_WHIP", "AXE_OF_THE_SHREDDED", "FLOWER_OF_TRUTH", "LAST_BREATH", "JUJU_SHORTBOW",
            "SHADOW_FURY", "LIVID_DAGGER", "RAGNAROCK_AXE", "WITHER_CLOAK", "SPIRIT_SCEPTRE", "BONZO_STAFF", "ICE_SPRAY_WAND"};

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().pbOnJoin) return;
        String clean = com.teslamaps.utils.StringUtil.strip(message).trim();
        Matcher m = JOIN.matcher(clean);
        if (!m.find()) return;
        if (TeslaMapsConfig.get().pbOnJoinKuudra && !clean.contains("joined the dungeon group")) {
            com.teslamaps.features.KuudraPartyFinder.onJoin(m.group(1));
            return;
        }
        if (TeslaMapsConfig.get().pbOnJoinCard) {
            var p = Minecraft.getInstance().player;
            if (p == null || !p.getGameProfile().name().equalsIgnoreCase(m.group(1))) CataCard.show(m.group(1), true, false);
            return;
        }
        String cls = null;
        if (TeslaMapsConfig.get().pbOnJoinExtra) {
            Matcher c = JOIN_CLASS.matcher(clean);
            if (c.find()) cls = DungeonInfoHud.classColor(c.group(1)) + c.group(1) + " " + c.group(2);
        }
        lookup(m.group(1), cls);
    }

    private static void lookup(String name, String cls) {
        HypixelApi.nameToUuid(name)
                .thenCompose(uuid -> uuid == null ? CompletableFuture.completedFuture(null) : HypixelApi.getProfiles(uuid))
                .thenAccept(resp -> {
                    if (resp == null || !resp.has("profiles")) return;
                    JsonObject member = selectedMember(resp.getAsJsonObject("profiles"));
                    if (member == null) return;
                    DungeonData dd = new DungeonData(member);
                    String line = format(name, dd);
                    if (line == null) return;
                    if (!TeslaMapsConfig.get().pbOnJoinExtra) {
                        Minecraft.getInstance().execute(() -> send(line));
                        return;
                    }
                    Component c = extra(line, cls, member);
                    Minecraft.getInstance().execute(() -> {
                        var p = Minecraft.getInstance().player;
                        if (p != null) p.sendSystemMessage(c);
                    });
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

    // Odin BetterPartyFinder / SkyblockExtras: class of the join line + gear overview on hover
    private static Component extra(String line, String cls, JsonObject member) {
        net.minecraft.network.chat.MutableComponent out = Component.literal(line);
        if (cls != null) out.append("§7 | " + cls);
        StringBuilder tip = new StringBuilder("§6§lGear");
        JsonObject inv = member.has("inventory") && member.get("inventory").isJsonObject() ? member.getAsJsonObject("inventory") : null;
        if (inv == null) {
            tip.append("\n§8(inventory API off)");
        } else {
            List<InventoryDecoder.Slot> armor = InventoryDecoder.decode(inv, "inv_armor");
            tip.append("\n§7Armor:");
            for (int i = armor.size() - 1; i >= 0; i--) {
                InventoryDecoder.Slot a = armor.get(i);
                tip.append("\n ").append(a == null || a.isEmpty() || a.name == null ? "§8Empty" : a.name);
            }
            List<String> found = new ArrayList<>();
            for (InventoryDecoder.Slot sl : InventoryDecoder.decode(inv, "inv_contents")) {
                if (sl == null || sl.skyblockId == null || sl.name == null) continue;
                for (String w : WEAPONS) {
                    if (sl.skyblockId.equals(w) && !found.contains(sl.name)) { found.add(sl.name); break; }
                }
            }
            tip.append("\n§7Weapons:");
            if (found.isEmpty()) tip.append("\n §8none found");
            for (String f : found) tip.append("\n ").append(f);
        }
        String pet = activePet(member);
        tip.append("\n§7Pet: ").append(pet == null ? "§8none" : pet);
        String t = tip.toString();
        out.append(Component.literal(" §8[§eGear§8]").withStyle(st -> st.withHoverEvent(
                new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(t)))));
        return out;
    }

    private static String activePet(JsonObject member) {
        try {
            JsonObject pd = member.getAsJsonObject("pets_data");
            if (pd == null) return null;
            for (JsonElement e : pd.getAsJsonArray("pets")) {
                JsonObject o = e.getAsJsonObject();
                if (!o.has("active") || !o.get("active").getAsBoolean()) continue;
                String tier = o.has("tier") ? o.get("tier").getAsString() : "";
                String type = o.get("type").getAsString().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
                StringBuilder sb = new StringBuilder(tierColor(tier));
                for (String w : type.split(" ")) {
                    if (w.isEmpty()) continue;
                    sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
                }
                if (o.has("heldItem") && !o.get("heldItem").isJsonNull())
                    sb.append("§7(").append(o.get("heldItem").getAsString()).append(")");
                return sb.toString().trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String tierColor(String tier) {
        return switch (tier) {
            case "MYTHIC" -> "§d";
            case "LEGENDARY" -> "§6";
            case "EPIC" -> "§5";
            case "RARE" -> "§9";
            case "UNCOMMON" -> "§a";
            default -> "§f";
        };
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
