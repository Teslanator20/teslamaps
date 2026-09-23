package com.teslamaps.slayer;

// Ported from Skyblocker's SlayerTimer and Skysoft's SlayerTimeToKill (PB part)
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ProfiledFile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Personal best kill time per slayer type and tier, saved per SkyBlock profile. */
public final class SlayerTimer {

    public static final class Pb {
        long ms;
        long ticks = -1;
        long date;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<TreeMap<String, Pb>>() {}.getType();
    private static final ProfiledFile FILE = new ProfiledFile("slayer_pb.json",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("slayer_pb.json"));

    private static Map<String, Pb> pbs = new TreeMap<>();
    private static boolean loaded;

    private SlayerTimer() {}

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        pbs = new TreeMap<>();
        Path p = FILE.path();
        try {
            if (Files.exists(p)) {
                Map<String, Pb> read = GSON.fromJson(Files.readString(p), TYPE);
                if (read != null) pbs = read;
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load slayer PBs", e);
        }
    }

    private static void save() {
        try {
            Path p = FILE.path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(pbs));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save slayer PBs", e);
        }
    }

    /** Records the kill, returns what the kill chat line should append. */
    static String onKill(String key, String label, long ms, long ticks, boolean died) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slayerKillPb || key == null) return "";
        if (died) return " §8(died, not counted)";
        ensure();

        Pb old = pbs.get(key);
        if (old != null && old.ms <= ms) return " §7PB: §e" + format(old.ms);

        Pb pb = new Pb();
        pb.ms = ms;
        pb.ticks = ticks;
        pb.date = System.currentTimeMillis();
        pbs.put(key, pb);
        save();
        if (old == null) return " §d(first PB)";

        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, 60, 10);
        mc.gui.setTitle(Component.literal("NEW PERSONAL BEST!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        mc.gui.setSubtitle(Component.literal("§e" + format(old.ms) + " §3➜ §a" + format(ms)));
        LoudSound.play(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        Announce.send(c.slayerPbChannel, label + " defeated in " + format(ms) + "! NEW PERSONAL BEST!");
        return " §d§lNEW PERSONAL BEST! §e" + format(old.ms) + " §3➜ §a" + format(ms);
    }

    public static void printPbs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ensure();
        if (pbs.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §7No slayer PBs yet"));
            return;
        }
        mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §fSlayer PBs:"));
        for (Map.Entry<String, Pb> e : pbs.entrySet()) {
            Pb pb = e.getValue();
            String ticks = pb.ticks > 0 ? " §8(" + pb.ticks + " ticks)" : "";
            mc.player.sendSystemMessage(Component.literal(" §5" + label(e.getKey()) + "§7: §b" + format(pb.ms) + ticks));
        }
    }

    public static void reset() {
        ensure();
        pbs.clear();
        save();
    }

    private static String label(String key) {
        int i = key.lastIndexOf('_');
        String type = key.substring(0, i);
        String tier = switch (key.substring(i + 1)) {
            case "1" -> "I"; case "2" -> "II"; case "3" -> "III"; case "4" -> "IV"; case "5" -> "V";
            default -> key.substring(i + 1);
        };
        return type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT) + " " + tier;
    }

    static String format(long ms) {
        return String.format(Locale.ROOT, "%.2fs", ms / 1000.0);
    }
}
