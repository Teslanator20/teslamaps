package com.teslamaps.features;

// Ported from Shizo's kuudra/general/partyfinder/PartyFinder.kt + PfUtils.kt
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.api.DungeonData;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class KuudraPartyFinder {

    private static final String[] TIERS = {"none", "hot", "burning", "fiery", "infernal"};
    private static final String[] TIER_NAMES = {"Basic", "Hot", "Burning", "Fiery", "Infernal"};
    private static final String[] HEARTS = {"KUUDRAS_HEART", "KUUDRAS_LUNG", "KUUDRAS_KIDNEY", "KUUDRAS_STOMACH",
            "KUUDRAS_JAW_BONE", "KUUDRAS_MANDIBLE", "KUUDRAS_HEART_OF_FIRE", "KUUDRAS_GELATINOUS_EYE"};
    private static final String[] TUX = {"ELEGANT_TUXEDO_BOOTS", "ELEGANT_TUXEDO_LEGGINGS", "ELEGANT_TUXEDO_CHESTPLATE"};
    private static final String[] TERROR = {"FIERY_TERROR_", "INFERNAL_TERROR_"};
    private static final String[] ARMOR = {"helmet", "chestplate", "leggings", "boots"};

    private static final Set<String> KICKED = new HashSet<>();

    private KuudraPartyFinder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(ClientCommands.literal("tmap").then(command("kuudra")));
            if (d.getRoot().getChild("kuudra") == null) d.register(command("kuudra"));
        });
        Feature.of("KuudraRunsReply").section("Party").when(() -> cfg().kuudraRunsReply)
                .chat(KuudraPartyFinder::onChat);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommands.literal(name).then(ClientCommands.argument("player", StringArgumentType.word())
                .executes(c -> {
                    String p = StringArgumentType.getString(c, "player");
                    msg(Component.literal("§a[TeslaMaps] §7Fetching Kuudra stats for §6" + p + "§7..."));
                    fetch(p, member -> {
                        if (member == null) msg(Component.literal("§a[TeslaMaps] §cNo profile found for §6" + p));
                        else msg(card(p, member, false));
                    });
                    return 1;
                }));
    }

    /** PbOnJoin, on a non-dungeon party finder join. */
    public static void onJoin(String name) {
        var p = Minecraft.getInstance().player;
        if (p == null || p.getGameProfile().name().equalsIgnoreCase(name)) return;
        TeslaMapsConfig c = cfg();
        if (c.kuudraPfSound) Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f)));
        if (c.kuudraPfAutoKick && c.kuudraPfKickCache && KICKED.contains(name.toLowerCase(Locale.ROOT))) {
            Announce.command("party kick " + name);
            msg(Component.literal("§a[TeslaMaps] §7Kicked §6" + name + " §7(kicked before)"));
            return;
        }
        fetch(name, member -> {
            if (member == null) {
                msg(Component.literal("§a[TeslaMaps] §cNo profile found for §6" + name));
                return;
            }
            msg(card(name, member, cfg().kuudraPfKickLine));
            if (cfg().kuudraPfAutoKick) autoKick(name, member);
        });
    }

    private static void autoKick(String name, JsonObject member) {
        TeslaMapsConfig c = cfg();
        List<String> reasons = new ArrayList<>();
        JsonObject inv = obj(member, "inventory");
        if (inv != null && inv.has("inv_contents")) {
            long mp = mp(member);
            if (mp < c.kuudraPfMinMp) reasons.add("MP " + mp + "/" + c.kuudraPfMinMp);
            long t5 = num(obj(obj(member, "nether_island_player_data"), "kuudra_completed_tiers"), "infernal");
            if (t5 < c.kuudraPfMinT5) reasons.add("T5 runs " + t5 + "/" + c.kuudraPfMinT5);
            if (c.kuudraPfReqGdrag && !petTypes(member).contains("GOLDEN_DRAGON")) reasons.add("no Golden Dragon");
            if (c.kuudraPfReqHeart && find(talismans(inv), HEARTS).isEmpty()) reasons.add("no Kuudra's Heart");
            if (c.kuudraPfReqTux) {
                List<InventoryDecoder.Slot> tux = find(allItems(inv), TUX);
                if (tux.size() < 3) reasons.add("Tuxedo " + tux.size() + "/3");
                else if (tux.stream().noneMatch(s -> loreHas(s, "Habanero Tactics V"))) reasons.add("no Habanero Tactics V Tux");
            }
        } else if (c.kuudraPfApiOffKick) {
            reasons.add("inventory API off");
        }
        if (reasons.isEmpty()) return;
        String why = String.join(", ", reasons);
        if (c.kuudraPfInform) Announce.party("Kicked " + name + " for: " + why);
        Announce.command("party kick " + name);
        if (c.kuudraPfKickCache) KICKED.add(name.toLowerCase(Locale.ROOT));
        msg(Component.literal("§a[TeslaMaps] §cKicking §6" + name + "§c: §7" + why));
    }

    public static void clearKickCache() {
        KICKED.clear();
    }

    private static void onChat(String raw) {
        PlayerChat.Msg m = PlayerChat.current();
        if (m == null || (m.channel() != PlayerChat.Channel.PARTY && m.channel() != PlayerChat.Channel.DM_FROM)) return;
        if (!StringUtil.stripTrim(m.message()).equalsIgnoreCase(".runs")) return;
        String sender = m.author();
        boolean party = m.channel() == PlayerChat.Channel.PARTY;
        fetch(sender, member -> {
            if (member == null) return;
            long t5 = num(obj(obj(member, "nether_island_player_data"), "kuudra_completed_tiers"), "infernal");
            Announce.command((party ? "pc " : "msg " + sender + " ") + sender + ": " + t5 + " T5 runs");
        });
    }

    /** Callback runs on the main thread, member null when nothing was found. */
    private static void fetch(String name, Consumer<JsonObject> done) {
        HypixelApi.nameToUuid(name)
                .thenCompose(uuid -> uuid == null ? CompletableFuture.completedFuture(null) : HypixelApi.getProfiles(uuid))
                .thenAccept(resp -> {
                    JsonObject member = resp == null || !resp.has("profiles") ? null : selected(resp.getAsJsonObject("profiles"));
                    Minecraft.getInstance().execute(() -> done.accept(member));
                })
                .exceptionally(e -> null);
    }

    private static JsonObject selected(JsonObject profiles) {
        JsonObject fallback = null;
        for (Map.Entry<String, JsonElement> e : profiles.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject p = e.getValue().getAsJsonObject();
            JsonObject data = obj(p, "data");
            if (data == null) continue;
            if (fallback == null) fallback = data;
            if (p.has("current") && p.get("current").getAsBoolean()) return data;
        }
        return fallback;
    }

    private static Component card(String name, JsonObject member, boolean kickLine) {
        JsonObject inv = obj(member, "inventory");
        boolean api = inv != null && inv.has("inv_contents");
        long sbLevel = num(obj(member, "leveling"), "experience") / 100;
        long purse = num(obj(member, "currencies"), "coin_purse");

        MutableComponent out = hover("§3§m▬▬▬▬▬▬▬▬§r §b§l[ §3" + name + " §b§l] §3§m▬▬▬▬▬▬▬▬",
                "§b§lUser Info\n\n§8▪ §3Username: §b" + name + "\n§8▪ §3SB Level: " + sbColor(sbLevel) + sbLevel
                        + "\n§8▪ §3Purse: §e" + big(purse)).copy();

        JsonObject tiers = obj(obj(member, "nether_island_player_data"), "kuudra_completed_tiers");
        long total = 0;
        StringBuilder runsTip = new StringBuilder("§b§lRuns Breakdown\n");
        for (int i = 0; i < TIERS.length; i++) {
            long r = num(tiers, TIERS[i]);
            total += r;
            runsTip.append("\n§3").append(TIER_NAMES[i]).append(": §f").append(r);
        }
        runsTip.append("\n\n§3§lTotal: §f").append(total);
        long t5 = num(tiers, "infernal");
        out.append("\n");
        out.append(hover("§8▪ §3Runs: " + runsColor(t5) + t5 + " §7(" + runsColor(total) + total + "§7)", runsTip.toString()));

        JsonObject bag = obj(member, "accessory_bag_storage");
        long mp = mp(member);
        StringBuilder mpTip = new StringBuilder("§b§lMP Breakdown\n\n§8▪ §3Power: §f");
        mpTip.append(bag != null && bag.has("selected_power") ? cap(bag.get("selected_power").getAsString()) : "None");
        mpTip.append("\n§8▪ §3Tunings:");
        JsonObject tunings = obj(obj(bag, "tuning"), "slot_0");
        if (tunings != null) for (Map.Entry<String, JsonElement> t : tunings.entrySet())
            if (t.getValue().isJsonPrimitive()) mpTip.append("\n §7").append(cap(t.getKey())).append(": §f").append(t.getValue().getAsString());
        out.append("\n");
        out.append(hover("§8▪ §3Magical Power: " + mpColor(mp) + big(mp), mpTip.toString()));

        DungeonData dd = new DungeonData(member);
        out.append(String.format(Locale.ROOT, "\n§8▪ §3Cata: §6%.2f", dd.getCatacombsLevel() + dd.getCatacombsProgress()));

        if (api) {
            List<InventoryDecoder.Slot> items = allItems(inv);
            out.append(itemLine("Hyperion", find(items, "HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE"), 1));
            out.append(itemLine("Terminator", find(items, "TERMINATOR"), 2));
            out.append(itemLine("Ragnarock Axe", find(items, "RAGNAROCK_AXE"), 1));
            List<InventoryDecoder.Slot> hearts = find(talismans(inv), HEARTS);
            out.append(itemLine(hearts.isEmpty() || hearts.get(0).name == null ? "Kuudra's Heart" : hearts.get(0).name, hearts, 1));
            out.append("\n§8▪ §3Meta: ");
            out.append(inline("Fiery+ Terror", "Terror", find(items, TERROR)));
            out.append(" §8| §3Util: ");
            out.append(inline("Elegant Tuxedo", "Tux", find(items, TUX)));
            out.append(" §8| ");
            out.append(inline("Bonemerang", "Bone", find(items, "BONE_BOOMERANG")));
            List<InventoryDecoder.Slot> armor = InventoryDecoder.decode(inv, "inv_armor");
            for (int i = 0; i < 4; i++) {
                InventoryDecoder.Slot s = armor.size() >= 4 ? armor.get(3 - i) : null;
                out.append("\n");
                if (s == null || s.isEmpty() || s.name == null) out.append("§8  ▸ §cNo " + ARMOR[i]);
                else out.append(hover("§8  ▸ §b" + s.name.replace("✿ ", ""), tip(s)));
            }
        } else {
            out.append("\n§8(inventory API off)");
        }

        List<String> pets = petTypes(member);
        boolean gdrag = pets.contains("GOLDEN_DRAGON");
        String active = activePet(member);
        out.append("\n");
        out.append(hover("§8▪ §3Pet: " + (gdrag ? "§a✔ Golden Dragon" : "§c✖ No GDrag"),
                "§b§lPets Info\n\n§8▪ §3Owns GDrag: " + (gdrag ? "§aYes" : "§cNo") + "\n§8▪ §3Active Pet: §f" + (active == null ? "None" : active)));

        if (kickLine) {
            out.append("\n");
            out.append(Component.literal("§c[✖] Kick " + name).withStyle(st -> st
                    .withClickEvent(new ClickEvent.RunCommand("/party kick " + name))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§c/party kick " + name)))));
        }
        out.append("\n§3§m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        return out;
    }

    private static Component itemLine(String name, List<InventoryDecoder.Slot> items, int need) {
        String status = items.size() >= need ? "§a✔" : items.isEmpty() ? "§c✖" : "§e" + items.size() + "/" + need;
        String t = items.isEmpty() ? "§cPlayer does not have " + StringUtil.strip(name) + "." : tips(items);
        return hover("\n§8▪ §b" + name + "§r: " + status, t);
    }

    private static Component inline(String full, String shortName, List<InventoryDecoder.Slot> items) {
        return hover(items.isEmpty() ? "§c✖ " + shortName : "§a✔ " + shortName,
                items.isEmpty() ? "§cPlayer does not have " + full + "." : tips(items));
    }

    private static String tips(List<InventoryDecoder.Slot> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size() && i < 4; i++) {
            if (i > 0) sb.append("\n§b§m--------------------\n");
            sb.append(tip(items.get(i)));
        }
        return sb.toString();
    }

    private static String tip(InventoryDecoder.Slot s) {
        StringBuilder sb = new StringBuilder(s.name == null ? "" : s.name);
        for (String l : s.lore) sb.append('\n').append(l);
        return sb.toString();
    }

    private static List<InventoryDecoder.Slot> allItems(JsonObject inv) {
        List<InventoryDecoder.Slot> out = new ArrayList<>();
        add(out, InventoryDecoder.decode(inv, "inv_contents"));
        add(out, InventoryDecoder.decode(inv, "inv_armor"));
        add(out, InventoryDecoder.decode(inv, "ender_chest_contents"));
        add(out, InventoryDecoder.decode(inv, "wardrobe_contents"));
        add(out, InventoryDecoder.decode(inv, "equipment_contents"));
        JsonObject packs = obj(inv, "backpack_contents");
        if (packs != null) for (String k : packs.keySet()) add(out, InventoryDecoder.decode(packs, k));
        return out;
    }

    private static List<InventoryDecoder.Slot> talismans(JsonObject inv) {
        List<InventoryDecoder.Slot> out = new ArrayList<>();
        add(out, InventoryDecoder.decode(inv, "bag_contents.talisman_bag"));
        return out;
    }

    private static void add(List<InventoryDecoder.Slot> out, List<InventoryDecoder.Slot> in) {
        for (InventoryDecoder.Slot s : in) if (s != null && s.skyblockId != null) out.add(s);
    }

    private static List<InventoryDecoder.Slot> find(List<InventoryDecoder.Slot> items, String... ids) {
        List<InventoryDecoder.Slot> out = new ArrayList<>();
        for (InventoryDecoder.Slot s : items) for (String id : ids) if (s.skyblockId.contains(id)) { out.add(s); break; }
        return out;
    }

    private static boolean loreHas(InventoryDecoder.Slot s, String text) {
        for (String l : s.lore) if (StringUtil.strip(l).contains(text)) return true;
        return false;
    }

    private static long mp(JsonObject member) {
        JsonObject bag = obj(member, "accessory_bag_storage");
        long mp = num(bag, "highest_magical_power");
        if (mp > 0) return mp;
        JsonObject tunings = obj(obj(bag, "tuning"), "slot_0");
        long pts = 0;
        if (tunings != null) for (Map.Entry<String, JsonElement> t : tunings.entrySet())
            if (t.getValue().isJsonPrimitive()) pts += t.getValue().getAsLong();
        return pts * 10;
    }

    private static List<String> petTypes(JsonObject member) {
        List<String> out = new ArrayList<>();
        JsonObject pd = obj(member, "pets_data");
        if (pd == null || !pd.has("pets") || !pd.get("pets").isJsonArray()) return out;
        for (JsonElement e : pd.getAsJsonArray("pets"))
            if (e.isJsonObject() && e.getAsJsonObject().has("type"))
                out.add(e.getAsJsonObject().get("type").getAsString().toUpperCase(Locale.ROOT));
        return out;
    }

    private static String activePet(JsonObject member) {
        JsonObject pd = obj(member, "pets_data");
        if (pd == null || !pd.has("pets") || !pd.get("pets").isJsonArray()) return null;
        for (JsonElement e : pd.getAsJsonArray("pets")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            if (o.has("active") && o.get("active").getAsBoolean() && o.has("type")) return cap(o.get("type").getAsString());
        }
        return null;
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
        for (String w : s.replace('_', ' ').split(" ")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static String big(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    private static String sbColor(long level) {
        String[] c = {"§7", "§f", "§e", "§a", "§2", "§b", "§3", "§9", "§d", "§5", "§6", "§c"};
        int i = (int) (level / 40);
        return i < c.length ? c[i] : "§4";
    }

    private static String mpColor(long mp) {
        return mp < 1350 ? "§4" : mp < 1600 ? "§e" : mp < 1750 ? "§2" : "§3";
    }

    private static String runsColor(long runs) {
        return runs < 500 ? "§4" : runs < 1000 ? "§e" : runs < 5000 ? "§2" : "§3";
    }

    private static void msg(Component c) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(c); });
    }
}
