package com.teslamaps.features;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.api.DungeonData;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.SkillData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.ui.Kit;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// Ported from NEU's commands/misc/PeekCommand.kt and SkyblockExtras' /purselb /skillslb
public final class PlayerPeek {
    private static final String[] SLAYER_KEYS = {"zombie", "spider", "wolf", "enderman", "blaze", "vampire"};
    private static final String[] SLAYER_SHORT = {"§2Z", "§8T", "§7S", "§5E", "§6B", "§cV"};
    private static final String[] SLAYER_NAMES = {"Revenant", "Tarantula", "Sven", "Voidgloom", "Inferno", "Riftstalker"};
    private static final long[] XP_ZOMBIE = {5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] XP_SPIDER = {5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] XP_DEFAULT = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
    private static final long[] XP_VAMPIRE = {20, 75, 240, 840, 2400};
    private static final int LOBBY_CAP = 20;
    private static volatile boolean lobbyRunning;

    private PlayerPeek() {}

    private static boolean on() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.pvExtras && c.pvPeek;
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            if (on()) d.register(ClientCommands.literal("peek")
                    .executes(c -> peek(Minecraft.getInstance().getUser().getName()))
                    .then(ClientCommands.argument("name", StringArgumentType.word())
                            .suggests((c, b) -> {
                                var conn = Minecraft.getInstance().getConnection();
                                String typed = b.getRemaining().toLowerCase(Locale.ROOT);
                                if (conn != null) for (PlayerInfo info : conn.getOnlinePlayers()) {
                                    String n = info.getProfile().name();
                                    if (n.matches("\\w{1,16}") && n.toLowerCase(Locale.ROOT).startsWith(typed)
                                            && !n.equalsIgnoreCase(typed)) b.suggest(n);
                                }
                                return b.buildFuture();
                            })
                            .executes(c -> peek(StringArgumentType.getString(c, "name")))));
            d.register(ClientCommands.literal("tmap").then(ClientCommands.literal("lobbylb")
                    .executes(c -> lobby("purse"))
                    .then(ClientCommands.argument("stat", StringArgumentType.word())
                            .suggests((c, b) -> {
                                for (String s : new String[]{"purse", "skills", "cata"}) {
                                    if (s.startsWith(b.getRemaining().toLowerCase(Locale.ROOT))) b.suggest(s);
                                }
                                return b.buildFuture();
                            })
                            .executes(c -> lobby(StringArgumentType.getString(c, "stat"))))));
        });
    }

    private static int peek(String name) {
        if (!on()) {
            chat(Component.literal("§a[TeslaMaps] §7/peek is off (Settings: Interface > Profile Viewer Extras)"));
            return 1;
        }
        chat(Component.literal("§a[TeslaMaps] §7Peeking §6" + name + "§7..."));
        HypixelApi.nameToUuid(name)
                .thenCompose(uuid -> uuid == null ? CompletableFuture.completedFuture(null)
                        : HypixelApi.getProfiles(uuid).thenApply(resp -> selected(resp, uuid)))
                .thenAccept(profile -> {
                    if (profile == null) {
                        chat(Component.literal("§a[TeslaMaps] §cNo SkyBlock data for " + name));
                        return;
                    }
                    chat(card(name, profile));
                })
                .exceptionally(e -> null);
        return 1;
    }

    private static SkyblockProfile selected(JsonObject resp, String uuid) {
        if (resp == null || !resp.has("profiles") || !resp.get("profiles").isJsonObject()) return null;
        SkyblockProfile fallback = null;
        for (Map.Entry<String, JsonElement> e : resp.getAsJsonObject("profiles").entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject p = e.getValue().getAsJsonObject();
            if (!p.has("data") || !p.get("data").isJsonObject()) continue;
            JsonObject data = p.getAsJsonObject("data");
            JsonObject raw = p.has("raw") && p.get("raw").isJsonObject() ? p.getAsJsonObject("raw") : data;
            String id = p.has("profile_id") ? p.get("profile_id").getAsString() : e.getKey();
            boolean current = p.has("current") && p.get("current").getAsBoolean();
            if (!current && fallback != null) continue;
            SkyblockProfile profile = new SkyblockProfile(id, e.getKey(), raw, data, uuid);
            if (current) return profile;
            fallback = profile;
        }
        return fallback;
    }

    private static Component card(String name, SkyblockProfile p) {
        MutableComponent out = Component.literal("§d§m          §r §b" + name + " §7(" + p.getCuteName() + ") §d§m          §r\n");

        StringBuilder skillHover = new StringBuilder("§aSkills");
        for (SkillData s : p.getSkills().values()) {
            int level = s.hasOverflow() ? s.getOverflowLevel() : s.getLevel();
            skillHover.append("\n§7").append(s.getDisplayName()).append(": §f").append(level)
                    .append(" §8(").append(Kit.compact((long) s.getTotalXp())).append(" XP)");
        }
        out.append(hover("§7Skill Avg: §a" + fmt2(p.getSkillAverage()) + " §8(§6" + fmt2(p.getOverflowSkillAverage()) + "§8)",
                skillHover.toString()));
        out.append("§8 | §7SB Level: §b" + (int) p.getSkyblockLevel() + "\n");

        DungeonData dd = p.getDungeonData();
        double classSum = 0;
        StringBuilder classHover = new StringBuilder("§cClasses");
        for (DungeonData.ClassData cd : dd.getClasses().values()) {
            double level = cd.getLevel() + cd.getProgress();
            classSum += level;
            classHover.append("\n§7").append(cd.getDisplayName()).append(": §f").append(fmt1(level));
        }
        int classes = Math.max(1, dd.getClasses().size());
        out.append(hover("§7Cata: §e" + fmt1(dd.getCatacombsLevel() + dd.getCatacombsProgress())
                + " §8| §7Class Avg: §e" + fmt1(classSum / classes), classHover.toString()));
        out.append("§8 | §7Secrets: §b" + Kit.compact(dd.getSecretsFound()) + "\n§7Slayers: ");

        JsonObject bosses = obj(obj(p.getMemberData(), "slayer"), "slayer_bosses");
        for (int i = 0; i < SLAYER_KEYS.length; i++) {
            long xp = num(obj(bosses, SLAYER_KEYS[i]), "xp");
            long[] table = switch (SLAYER_KEYS[i]) {
                case "zombie" -> XP_ZOMBIE;
                case "spider" -> XP_SPIDER;
                case "vampire" -> XP_VAMPIRE;
                default -> XP_DEFAULT;
            };
            int level = 0;
            for (long t : table) if (xp >= t) level++;
            out.append(hover(SLAYER_SHORT[i] + level, "§a" + SLAYER_NAMES[i] + " §7Level §f" + level + "\n§7XP: §f" + Kit.dots(xp)));
            if (i < SLAYER_KEYS.length - 1) out.append("§8/");
        }

        out.append("\n§7Purse: §6" + Kit.compact((long) p.getCoinPurse()));
        out.append(p.hasBankData()
                ? hover(" §8| §7Bank: §6" + Kit.compact((long) p.getBankBalance()),
                "§7Coop: §6" + Kit.compact((long) p.getCoopBank()) + "\n§7Personal: §6" + Kit.compact((long) p.getPersonalBank()))
                : Component.literal(" §8| §7Bank: §8API off"));
        out.append(Component.literal("  §e[PV]").withStyle(s -> s
                .withClickEvent(new ClickEvent.RunCommand("/pv " + name))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Open the profile viewer")))));
        out.append("\n§d§m                                   ");
        return out;
    }

    private record Entry(String name, double value, String shown) {}

    private static int lobby(String stat) {
        if (!on()) {
            chat(Component.literal("§a[TeslaMaps] §7Lobby leaderboards are off (Settings: Interface > Profile Viewer Extras)"));
            return 1;
        }
        String key = stat.toLowerCase(Locale.ROOT);
        if (!key.equals("purse") && !key.equals("skills") && !key.equals("cata")) {
            chat(Component.literal("§a[TeslaMaps] §7/tmap lobbylb <purse|skills|cata>"));
            return 1;
        }
        if (lobbyRunning) {
            chat(Component.literal("§a[TeslaMaps] §7A lobby leaderboard is already loading"));
            return 1;
        }
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return 1;
        List<String[]> players = new ArrayList<>();
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            var profile = info.getProfile();
            if (profile.id() == null || profile.id().version() != 4 || !profile.name().matches("\\w{3,16}")) continue;
            players.add(new String[]{profile.name(), profile.id().toString().replace("-", "")});
            if (players.size() >= LOBBY_CAP) break;
        }
        if (players.isEmpty()) {
            chat(Component.literal("§a[TeslaMaps] §7No players found in the tab list"));
            return 1;
        }
        lobbyRunning = true;
        chat(Component.literal("§a[TeslaMaps] §7Loading " + key + " for " + players.size() + " players..."));
        CompletableFuture.runAsync(() -> {
            List<Entry> entries = new ArrayList<>();
            try {
                for (String[] player : players) {
                    try {
                        SkyblockProfile p = selected(HypixelApi.getProfiles(player[1]).join(), player[1]);
                        if (p == null) continue;
                        double value = switch (key) {
                            case "purse" -> p.getCoinPurse();
                            case "skills" -> p.getSkillAverage();
                            default -> p.getDungeonData().getCatacombsLevel() + p.getDungeonData().getCatacombsProgress();
                        };
                        String shown = key.equals("purse") ? "§6" + Kit.compact((long) value) : "§a" + fmt2(value);
                        entries.add(new Entry(player[0], value, shown));
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception ignored) {
                    }
                }
            } finally {
                lobbyRunning = false;
            }
            entries.sort((a, b) -> Double.compare(b.value(), a.value()));
            String title = switch (key) {
                case "purse" -> "Purse";
                case "skills" -> "Skill Average";
                default -> "Catacombs";
            };
            MutableComponent out = Component.literal("§d§m        §r §bLobby " + title + " §d§m        §r");
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                String name = e.name();
                out.append(Component.literal("\n§7" + (i + 1) + ". §f" + name + " §8- " + e.shown()).withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand("/pv " + name))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Open " + name + " in the profile viewer")))));
            }
            if (entries.isEmpty()) out.append("\n§7No data (API off or rate limited)");
            chat(out);
        });
        return 1;
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
