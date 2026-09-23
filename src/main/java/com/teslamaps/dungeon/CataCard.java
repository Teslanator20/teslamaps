package com.teslamaps.dungeon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.api.DungeonData;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// Ported from Odin's CataCommand.kt
public final class CataCard {

    private static final String[] CLASSES = {"healer", "mage", "berserk", "archer", "tank"};
    private static final String[] CLASS_COLORS = {"§d", "§b", "§c", "§6", "§a"};
    private static final String[] ARMOR_ICONS = {"⛑", "🛡", "👖", "👢"};

    private CataCard() {}

    /** Any thread. {@code kickLine} adds the clickable "kick" row (party finder joins). */
    public static void show(String name, boolean kickLine, boolean announceFetch) {
        if (announceFetch) chat(Component.literal("§a[TeslaMaps] §7Fetching dungeon stats for §6" + name + "§7..."));
        HypixelApi.nameToUuid(name)
                .thenCompose(uuid -> uuid == null ? CompletableFuture.completedFuture(null) : HypixelApi.getProfiles(uuid))
                .thenAccept(resp -> {
                    if (resp == null || !resp.has("profiles")) {
                        if (announceFetch) {
                            String why = resp != null && resp.has("error") ? resp.get("error").getAsString() : "player not found";
                            chat(Component.literal("§a[TeslaMaps] §c" + why));
                        }
                        return;
                    }
                    JsonObject member = selectedMember(resp.getAsJsonObject("profiles"));
                    if (member == null) return;
                    Component card = build(name, member, kickLine);
                    chat(card);
                })
                .exceptionally(e -> null);
    }

    static JsonObject selectedMember(JsonObject profiles) {
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

    private static Component build(String name, JsonObject member, boolean kickLine) {
        DungeonData dd = new DungeonData(member);
        MutableComponent out = Component.literal("§d§m           §r §b" + name + " §d§m           §r\n");

        int runs = 0;
        for (int f = 1; f <= 7; f++) {
            runs += completions(dd.getNormalFloors().get(String.valueOf(f)));
            runs += completions(dd.getMasterFloors().get(String.valueOf(f)));
        }
        int secrets = dd.getSecretsFound();
        double avg = runs > 0 ? (double) secrets / runs : 0;
        JsonObject kills = obj(obj(member, "player_stats"), "kills");
        long blood = num(kills, "watcher_summon_undead") + num(kills, "master_watcher_summon_undead");

        out.append(hover("§7Cata: §e" + fmt1(dd.getCatacombsLevel() + dd.getCatacombsProgress()),
                "§7Catacombs Level\n§7XP: §b" + big(dd.getCatacombsXp())));
        out.append(hover(" §8| §7Secrets: §e" + big(secrets) + " §8(§b" + fmt1(avg) + "§8)",
                "§7Total Secrets: §e" + big(secrets) + "\n§7Total Runs: §b" + runs + "\n§7Average: §a" + fmt2(avg)));
        out.append(hover(" §8| §7Blood: §c" + big(blood), "§7Blood Mobs Killed: §c" + big(blood)));
        out.append("\n§7Classes: ");

        double sum = 0, totalXp = 0;
        for (int i = 0; i < CLASSES.length; i++) {
            DungeonData.ClassData cd = dd.getClasses().get(CLASSES[i]);
            double level = cd == null ? 0 : cd.getLevel() + cd.getProgress();
            double xp = cd == null ? 0 : cd.getXp();
            sum += level;
            totalXp += xp;
            String col = CLASS_COLORS[i];
            out.append(hover(col + (int) level, col + cap(CLASSES[i]) + " Level\n§7XP: §b" + big(xp)));
            if (i < CLASSES.length - 1) out.append("§8/");
        }
        out.append(hover(" §8(§7Avg: §a" + fmt1(sum / CLASSES.length) + "§8)",
                "§7Class Average\n§7Total Class XP: §b" + big(totalXp)));

        out.append("\n§7Floors: ");
        out.append(hover("§6Normal", floorHover(dd.getNormalFloors(), "§6§lNormal Floors", "§eF")));
        out.append(" §8| ");
        out.append(hover("§cMaster", floorHover(dd.getMasterFloors(), "§c§lMaster Floors", "§cM")));
        out.append(" §8| ");
        JsonObject bag = obj(member, "accessory_bag_storage");
        JsonObject tunings = obj(obj(bag, "tuning"), "slot_0");
        StringBuilder tuneHover = new StringBuilder("§bTunings");
        long tuningPoints = 0;
        if (tunings != null) {
            for (Map.Entry<String, JsonElement> t : tunings.entrySet()) {
                if (!t.getValue().isJsonPrimitive()) continue;
                tuningPoints += t.getValue().getAsLong();
                tuneHover.append("\n§7- §e").append(cap(t.getKey().replace('_', ' '))).append("§7: ").append(t.getValue().getAsString());
            }
        }
        long mp = num(bag, "highest_magical_power");
        if (mp == 0) mp = tuningPoints * 10;
        out.append(hover("§7MP: §d" + big(mp), tuneHover.toString()));

        JsonObject inv = obj(member, "inventory");
        List<InventoryDecoder.Slot> armor = InventoryDecoder.decode(inv, "inv_armor");
        if (armor.size() >= 4) {
            out.append("\n§7Armor: ");
            for (int i = 0; i < 4; i++) {
                InventoryDecoder.Slot s = armor.get(3 - i);
                String h = s == null || s.isEmpty() ? "§8Empty Slot"
                        : (s.name == null ? "" : s.name) + (s.lore.isEmpty() ? "" : "\n" + String.join("\n", s.lore));
                out.append(hover(s == null || s.isEmpty() ? "§8" + ARMOR_ICONS[i] : ARMOR_ICONS[i], h));
                if (i < 3) out.append(" §8| ");
            }
        }

        List<String[]> missing = new ArrayList<>();
        if (inv != null) {
            List<String> ids = allIds(inv);
            if (!hasAny(ids, "HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE")) missing.add(new String[]{"§5Wither Blade", "§5Blade"});
            if (!hasAny(ids, "TERMINATOR")) missing.add(new String[]{"§cTerminator", "§cTerm"});
        }
        List<String> pets = petTypes(member);
        if (!pets.contains("GOLDEN_DRAGON")) missing.add(new String[]{"§6Golden Dragon", "§6GDrag"});
        if (!pets.contains("ENDER_DRAGON")) missing.add(new String[]{"§5Ender Dragon", "§5EDrag"});
        if (!missing.isEmpty()) {
            out.append("\n§7Missing: ");
            for (int i = 0; i < missing.size(); i++) {
                out.append(hover("§c✖ " + missing.get(i)[1], "§cMissing " + missing.get(i)[0]));
                if (i < missing.size() - 1) out.append(" §8| ");
            }
        }
        if (inv == null) out.append("\n§8(inventory API off)");

        if (kickLine) {
            out.append("\n");
            out.append(Component.literal("                 §cClick To Kick §e" + name).withStyle(st -> st
                    .withClickEvent(new ClickEvent.RunCommand("/party kick " + name))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§c/party kick " + name)))));
        }
        out.append("\n§d§m                           ");
        return out;
    }

    private static int completions(DungeonData.FloorData f) {
        return f == null ? 0 : f.getCompletions();
    }

    private static String floorHover(Map<String, DungeonData.FloorData> floors, String title, String prefix) {
        StringBuilder sb = new StringBuilder(title);
        for (int f = 1; f <= 7; f++) {
            DungeonData.FloorData d = floors.get(String.valueOf(f));
            String time = d == null ? "§8None"
                    : d.getFastestSPlusTime() > 0 ? "§a" + d.getFormattedSPlusTime()
                    : d.getFastestTime() > 0 ? "§7" + d.getFormattedFastestTime() : "§8None";
            sb.append('\n').append(prefix).append(f).append(": ").append(time)
                    .append(" §8(§b").append(completions(d)).append("§8)");
        }
        return sb.toString();
    }

    private static List<String> allIds(JsonObject inv) {
        List<String> ids = new ArrayList<>();
        addIds(ids, InventoryDecoder.decode(inv, "inv_contents"));
        addIds(ids, InventoryDecoder.decode(inv, "ender_chest_contents"));
        JsonObject packs = obj(inv, "backpack_contents");
        if (packs != null) for (String k : packs.keySet()) addIds(ids, InventoryDecoder.decode(packs, k));
        return ids;
    }

    private static void addIds(List<String> ids, List<InventoryDecoder.Slot> slots) {
        for (InventoryDecoder.Slot s : slots) if (s != null && s.skyblockId != null) ids.add(s.skyblockId);
    }

    private static boolean hasAny(List<String> ids, String... wanted) {
        for (String id : ids) for (String w : wanted) if (id.contains(w)) return true;
        return false;
    }

    private static List<String> petTypes(JsonObject member) {
        List<String> out = new ArrayList<>();
        JsonObject petsData = obj(member, "pets_data");
        if (petsData == null || !petsData.has("pets") || !petsData.get("pets").isJsonArray()) return out;
        for (JsonElement e : petsData.getAsJsonArray("pets")) {
            if (e.isJsonObject() && e.getAsJsonObject().has("type"))
                out.add(e.getAsJsonObject().get("type").getAsString().toUpperCase(Locale.ROOT));
        }
        return out;
    }

    private static Component hover(String text, String hover) {
        return Component.literal(text).withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    private static long num(JsonObject o, String key) {
        try {
            return o != null && o.has(key) ? (long) o.get(key).getAsDouble() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String cap(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static String big(double v) {
        return String.format(Locale.ROOT, "%,d", (long) v);
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static void chat(Component c) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(c); });
    }
}
