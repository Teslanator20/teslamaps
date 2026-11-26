package com.teslamaps.slayer;

// Ported from HypixelSimpleChat's rules/common/Slayer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerChatCompact {

    private static final Pattern OBJECTIVE = Pattern.compile("^» Slay ([\\d,]+) Combat XP worth of (.+?)\\.?$");
    private static final Pattern LEVEL = Pattern.compile("(\\w+) Slayer LVL (\\d+) [-–—] (?:Next LVL in ([\\d,]+) XP|LVL MAXED OUT)");
    private static final String[][] BRANDS = {
            {"Zombie", "§2Revenant"}, {"Spider", "§4Tarantula"}, {"Wol", "§eSven"},
            {"Enderm", "§5Voidgloom"}, {"Blaze", "§6Inferno"}, {"Vampire", "§4Riftstalker"},
    };

    private static boolean emitting;
    private static boolean pendingStart;
    private static long completeAt;
    private static String brand = "";

    private SlayerChatCompact() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SlayerChatCompact").section("Combat").when(() -> cfg().compactSlayerChat)
                .tick(SlayerChatCompact::tick);
    }

    /** From ChatFilter.process. null = untouched, "" = drop, else the replacement. */
    public static String handle(String stripped) {
        if (emitting || !cfg().compactSlayerChat || !cfg().section("Combat") || PlayerChat.current() != null) return null;
        String t = stripped.trim();
        if (t.isEmpty()) return null;
        if (t.equals("SLAYER QUEST STARTED!")) {
            if (pendingStart) emit("§5Slayer §7· started");
            pendingStart = true;
            return "";
        }
        if (t.equals("SLAYER QUEST COMPLETE!")) {
            completeAt = System.currentTimeMillis();
            return "";
        }
        if (t.startsWith("» Slay ")) {
            Matcher m = OBJECTIVE.matcher(t);
            if (m.find()) {
                brand = brand(m.group(2));
                if (!pendingStart) return null;
                pendingStart = false;
                return "§5Slayer §7· " + brand + " §7started\n§7Slay §f" + m.group(1) + " XP §7of " + m.group(2);
            }
        }
        if (pendingStart) {
            pendingStart = false;
            emit("§5Slayer §7· started");
        }
        if (completeAt > 0 && t.contains("Slayer LVL")) {
            Matcher m = LEVEL.matcher(t);
            if (m.find()) {
                completeAt = 0;
                String b = brand.isEmpty() ? brand(m.group(1)) : brand;
                String next = m.group(3) != null ? "§7Next LVL in §d" + m.group(3) + " XP" : "§aLVL MAXED OUT";
                return "§5Slayer " + b + " §a✔ §fcomplete\n§5Slayer §7· §fLVL " + m.group(2) + " §8- " + next;
            }
        }
        return null;
    }

    private static void tick() {
        if (completeAt > 0 && System.currentTimeMillis() - completeAt > 3000) {
            completeAt = 0;
            emit("§5Slayer " + (brand.isEmpty() ? "" : brand + " ") + "§a✔ §fcomplete");
        }
    }

    private static String brand(String mob) {
        for (String[] b : BRANDS) if (mob.startsWith(b[0])) return b[1];
        return "§7" + mob;
    }

    private static void emit(String line) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        emitting = true;
        try {
            player.sendSystemMessage(Component.literal(line));
        } finally {
            emitting = false;
        }
    }
}
