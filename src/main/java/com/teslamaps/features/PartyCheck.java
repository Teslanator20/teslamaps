package com.teslamaps.features;

// Commands after SkyblockExtras' /pcheck, /gxp, /ginfo
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.PbOnJoin;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.TickScheduler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PartyCheck {

    private static final String P = "§b[TeslaMaps] ";
    private static final int GXP_TOP = 15;

    private PartyCheck() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PartyCheck");
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(ClientCommands.literal("tmap")
                    .then(pcheck(ClientCommands.literal("pcheck")))
                    .then(gxp(ClientCommands.literal("gxp")))
                    .then(ginfo(ClientCommands.literal("ginfo"))));
            if (!cfg().partyCheckCommands) return;
            d.register(pcheck(ClientCommands.literal("pcheck")));
            d.register(gxp(ClientCommands.literal("gxp")));
            d.register(ginfo(ClientCommands.literal("ginfo")));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> pcheck(LiteralArgumentBuilder<FabricClientCommandSource> b) {
        return b.executes(c -> partyCheck(c.getSource()));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> gxp(LiteralArgumentBuilder<FabricClientCommandSource> b) {
        return b.executes(c -> guildExp(c.getSource(), self()))
                .then(ClientCommands.argument("player", StringArgumentType.word())
                        .executes(c -> guildExp(c.getSource(), StringArgumentType.getString(c, "player"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> ginfo(LiteralArgumentBuilder<FabricClientCommandSource> b) {
        return b.executes(c -> guildInfo(c.getSource(), self()))
                .then(ClientCommands.argument("player", StringArgumentType.word())
                        .executes(c -> guildInfo(c.getSource(), StringArgumentType.getString(c, "player"))));
    }

    private static boolean off(FabricClientCommandSource src) {
        if (cfg().partyCheckCommands) return false;
        src.sendFeedback(Component.literal(P + "§7Turn on §fParty > Party Check Commands §7first"));
        return true;
    }

    private static String self() {
        return Minecraft.getInstance().getUser().getName();
    }

    private static int partyCheck(FabricClientCommandSource src) {
        if (off(src)) return 0;
        List<String> names = new ArrayList<>();
        for (String m : PartyTracker.members()) if (!m.equalsIgnoreCase(self())) names.add(m);
        if (names.isEmpty()) {
            src.sendFeedback(Component.literal(P + "§7No party members known - run §f/p list §7and try again"));
            return 1;
        }
        src.sendFeedback(Component.literal(P + "§7Checking " + names.size() + " party member" + (names.size() == 1 ? "" : "s") + "..."));
        // staggered, the pv backend chokes on a burst
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            TickScheduler.schedule(i * 10, () -> PbOnJoin.check(n));
        }
        return 1;
    }

    private static CompletableFuture<JsonObject> guildOf(String name) {
        return HypixelApi.nameToUuid(name)
                .thenCompose(uuid -> uuid == null ? CompletableFuture.completedFuture(null) : HypixelApi.getGuild(uuid));
    }

    private static void say(String line) {
        Minecraft.getInstance().execute(() -> {
            var p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(Component.literal(line));
        });
    }

    private static int guildInfo(FabricClientCommandSource src, String name) {
        if (off(src)) return 0;
        src.sendFeedback(Component.literal(P + "§7Looking up the guild of §f" + name + "§7..."));
        guildOf(name).thenAccept(g -> {
            if (g == null) { say(P + "§cGuild lookup failed"); return; }
            if (!g.has("name")) { say(P + "§f" + name + " §7is not in a guild"); return; }
            StringBuilder sb = new StringBuilder(P).append("§6").append(str(g, "name"));
            if (g.has("tag") && !g.get("tag").isJsonNull()) sb.append(" §7[").append(str(g, "tag")).append("§7]");
            JsonElement members = g.get("members");
            int count = members != null && members.isJsonArray() ? members.getAsJsonArray().size() : 0;
            sb.append("\n §7Members: §f").append(count);
            if (g.has("exp")) sb.append("\n §7Guild EXP: §f").append(String.format("%,d", g.get("exp").getAsLong()));
            if (g.has("created")) sb.append("\n §7Created: §f").append(date(g.get("created").getAsLong()));
            JsonObject me = member(g, name);
            if (me != null) {
                sb.append("\n §7").append(name).append(": §f").append(str(me, "rank"));
                if (me.has("joined")) sb.append(" §7since §f").append(date(me.get("joined").getAsLong()));
                sb.append(" §7| weekly GXP §f").append(String.format("%,d", weekly(me)));
            }
            say(sb.toString());
        }).exceptionally(e -> { say(P + "§cGuild lookup failed"); return null; });
        return 1;
    }

    private static int guildExp(FabricClientCommandSource src, String name) {
        if (off(src)) return 0;
        src.sendFeedback(Component.literal(P + "§7Loading guild EXP..."));
        guildOf(name).thenAccept(g -> {
            if (g == null) { say(P + "§cGuild lookup failed"); return; }
            if (!g.has("members") || !g.get("members").isJsonArray()) { say(P + "§f" + name + " §7is not in a guild"); return; }
            List<JsonObject> list = new ArrayList<>();
            for (JsonElement e : g.getAsJsonArray("members")) if (e.isJsonObject()) list.add(e.getAsJsonObject());
            list.sort((a, b) -> Long.compare(weekly(b), weekly(a)));
            StringBuilder sb = new StringBuilder(P).append("§6").append(str(g, "name")).append(" §7- weekly GXP (top ")
                    .append(Math.min(GXP_TOP, list.size())).append(" of ").append(list.size()).append(")");
            for (int i = 0; i < list.size() && i < GXP_TOP; i++) {
                JsonObject m = list.get(i);
                String n = HypixelApi.uuidToName(str(m, "uuid"));
                sb.append("\n §7").append(i + 1).append(". §f").append(n != null ? n : str(m, "uuid").substring(0, 8))
                        .append(" §8(").append(str(m, "rank")).append(") §a").append(String.format("%,d", weekly(m)));
            }
            JsonObject me = member(g, name);
            if (me != null) {
                int rank = list.indexOf(me) + 1;
                sb.append("\n §7").append(name).append(": §e#").append(rank).append(" §a").append(String.format("%,d", weekly(me)));
            }
            say(sb.toString());
        }).exceptionally(e -> { say(P + "§cGuild lookup failed"); return null; });
        return 1;
    }

    private static JsonObject member(JsonObject g, String name) {
        String uuid = HypixelApi.nameToUuid(name).join();
        if (uuid == null || !g.has("members")) return null;
        String plain = uuid.replace("-", "");
        for (JsonElement e : g.getAsJsonArray("members")) {
            if (e.isJsonObject() && str(e.getAsJsonObject(), "uuid").replace("-", "").equalsIgnoreCase(plain)) return e.getAsJsonObject();
        }
        return null;
    }

    private static long weekly(JsonObject m) {
        long sum = 0;
        JsonElement h = m.get("expHistory");
        if (h != null && h.isJsonObject()) for (var e : h.getAsJsonObject().entrySet()) sum += e.getValue().getAsLong();
        return sum;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "?" : e.getAsString();
    }

    private static String date(long ms) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }
}
