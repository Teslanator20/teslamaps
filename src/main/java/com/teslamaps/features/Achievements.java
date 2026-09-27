package com.teslamaps.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from SkyHanni's AchievementManager + a few of its *Achievement files
/** Unlockable, optionally tiered or secret achievements, chat-driven. /tmap achievements lists them. */
public final class Achievements {

    public record Def(String id, String name, String desc, boolean secret, int[] tiers) {}

    public static final class Data {
        public int progress;
        public boolean achieved;
        public long at;
    }

    private static final Pattern WOOD_RNG = Pattern.compile(
            "RNG METER! Reselected the Enchanted Book \\((?:Rejuvenate I|Infinite Quiver VI|Feather Falling VI|Bank I|Ultimate Jerry I)\\) for Catacombs - Floor 1!.*");
    private static final Pattern RNG_XP = Pattern.compile("\\s*RNG Meter - ([\\d,]+) Stored XP");
    private static final Pattern BOOP = Pattern.compile("To .+: Boop!");

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("achievements.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Data> data;

    static {
        def("deaths", "Professional Dier", "Die on SkyBlock", false, 1, 10, 100, 1000);
        def("slayers", "Slayer Grinder", "Finish slayer quests", false, 10, 100, 1000);
        def("dungeons", "Dungeon Runner", "Finish dungeon runs", false, 10, 100, 1000);
        def("crazy_rare", "Crazy Rare", "Get CRAZY RARE drops", false, 1, 10, 100);
        def("rngesus", "Blessed By RNGesus", "Get a PRAY TO RNGESUS drop", false);
        def("rng_3m", "Desperate Slayer", "Get over 3m stored RNG meter XP", false);
        def("rng_10m", "Slayer Psycho", "Get over 10m stored RNG meter XP. I hope you drop the dye soon!", false);
        def("boop", "Social Butterfly", "Annoy people with /boop", false, 10);
        def("wood_rng", "Small RNG Drop", "Use the RNG meter to get a book from the F1 wood chest", true);
        def("ttt_fail", "Outsmarted", "Lose Tic Tac Toe against a dungeon puzzle", true);
    }

    private Achievements() {}

    private static void def(String id, String name, String desc, boolean secret, int... tiers) {
        DEFS.put(id, new Def(id, name, desc, secret, tiers));
    }

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("Achievements").when(() -> cfg().achievements)
                .chat(Achievements::onChat)
                .config("QoL & Misc", b -> {
                    b.label("Achievements");
                    b.toggle("Achievements", () -> cfg().achievements, v -> cfg().achievements = v)
                            .desc("Just for fun: unlockable, tiered and secret achievements with a chat message");
                    b.toggle("  Unlock Sound", () -> cfg().achievementSound, v -> cfg().achievementSound = v);
                    b.button("  Show Achievements (/tmap achievements)", Achievements::list);
                });
    }

    private static Map<String, Data> data() {
        if (data != null) return data;
        data = new HashMap<>();
        try {
            if (Files.exists(FILE)) {
                Map<String, Data> read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Data>>() {}.getType());
                if (read != null) read.forEach((k, v) -> { if (v != null) data.put(k, v); });
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("achievements.json unreadable: {}", e.getMessage());
        }
        return data;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("achievements.json not saved: {}", e.getMessage());
        }
    }

    private static Data get(String id) {
        return data().computeIfAbsent(id, k -> new Data());
    }

    public static void complete(String id) {
        Def def = DEFS.get(id);
        if (def == null) return;
        if (def.tiers().length > 0) {
            setProgress(id, Math.max(get(id).progress, def.tiers()[def.tiers().length - 1]));
            return;
        }
        Data d = get(id);
        if (d.achieved) return;
        d.achieved = true;
        d.at = System.currentTimeMillis();
        save();
        announce(def, 0);
    }

    public static void add(String id, int amount) {
        setProgress(id, get(id).progress + amount);
    }

    public static void setProgress(String id, int progress) {
        Def def = DEFS.get(id);
        if (def == null || def.tiers().length == 0) return;
        Data d = get(id);
        if (progress <= d.progress) return;
        int before = tier(def, d.progress);
        d.progress = progress;
        int after = tier(def, progress);
        if (after >= def.tiers().length && !d.achieved) {
            d.achieved = true;
            d.at = System.currentTimeMillis();
        }
        save();
        if (after > before) announce(def, after);
    }

    private static int tier(Def def, int progress) {
        int t = 0;
        for (int need : def.tiers()) if (progress >= need) t++;
        return t;
    }

    private static String title(Def def, int tier) {
        return tier == 0 || def.tiers().length <= 1 ? def.name() : def.name() + " " + tier;
    }

    private static String hover(Def def) {
        Data d = get(def.id());
        StringBuilder s = new StringBuilder("§7").append(def.desc());
        int[] tiers = def.tiers();
        if (tiers.length > 0) {
            int t = tier(def, d.progress);
            s.append("\n§e").append(d.progress);
            if (t < tiers.length) s.append('/').append(tiers[t]).append(tiers.length > 1 ? " for the next tier" : " to unlock");
            else s.append(" (fully unlocked)");
        }
        return s.toString();
    }

    private static void announce(Def def, int tier) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String hover = hover(def);
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §6Achievement unlocked: §e" + title(def, tier))
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))));
        if (cfg().achievementSound) LoudSound.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    public static void list() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int done = 0;
        for (Def def : DEFS.values()) if (get(def.id()).achieved) done++;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §6Achievements §7(" + done + "/" + DEFS.size() + ")"
                + (cfg().achievements ? "" : " §c- tracking is off")));
        for (Def def : DEFS.values()) {
            Data d = get(def.id());
            int t = tier(def, d.progress);
            boolean shown = d.achieved || t > 0 || !def.secret();
            String line = (d.achieved ? "§a✔ " : t > 0 ? "§e◆ " : "§8✖ ") + (shown ? title(def, t) : "???");
            String hover = shown ? hover(def) : "§7Secret achievement";
            MutableComponent c = Component.literal(" " + line)
                    .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
            mc.player.sendSystemMessage(c);
        }
    }

    private static void onChat(String raw) {
        String line = StringUtil.strip(raw);
        String t = line.trim();
        if (t.isEmpty()) return;
        // own outgoing DM confirmation, reads as player chat
        if (t.startsWith("To ") && t.endsWith(": Boop!") && BOOP.matcher(t).matches()) {
            add("boop", 1);
            return;
        }
        if (!StringUtil.isServerMessage(raw)) return;
        if (t.startsWith("☠ You ") && SkyblockUtils.isInSkyblock()) add("deaths", 1);
        else if (t.startsWith("SLAYER QUEST COMPLETE")) add("slayers", 1);
        else if (t.equals("> EXTRA STATS <")) add("dungeons", 1);
        else if (t.startsWith("CRAZY RARE DROP!")) add("crazy_rare", 1);
        else if (t.startsWith("PRAY TO RNGESUS DROP!")) complete("rngesus");
        else if (t.startsWith("RNG METER! ") && WOOD_RNG.matcher(t).matches()) complete("wood_rng");
        else if (t.startsWith("PUZZLE FAIL! ") && t.endsWith(" lost Tic Tac Toe! Yikes!")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && t.equals("PUZZLE FAIL! " + mc.player.getGameProfile().name() + " lost Tic Tac Toe! Yikes!"))
                complete("ttt_fail");
        } else if (t.startsWith("RNG Meter - ")) {
            Matcher m = RNG_XP.matcher(t);
            if (m.matches()) {
                long xp;
                try {
                    xp = Long.parseLong(m.group(1).replace(",", ""));
                } catch (NumberFormatException e) {
                    return;
                }
                if (xp >= 3_000_000) complete("rng_3m");
                if (xp >= 10_000_000) complete("rng_10m");
            }
        }
    }
}
