package com.teslamaps.features.fishing;

// Ported from SkyHanni's features/fishing/SeaCreatureCocoonWarning.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SeaCreatureList;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SeaCreatureCocoon {

    public static final String[] RARITIES = {"Any", "Rare", "Epic", "Legendary", "Mythic"};

    private static final Pattern COCOON = Pattern.compile("^CAUGHT! You cocooned an? ([\\w ]+)!$");

    private static int plings;
    private static int plingTick;

    private SeaCreatureCocoon() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SeaCreatureCocoon").section("Fishing").when(() -> cfg().seaCreatureCocoonWarn)
                .serverChat(SeaCreatureCocoon::onChat)
                .tick(SeaCreatureCocoon::tick)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Sea Creature Alerts");
                    b.toggle("Sea Creature Cocoon Warning", () -> c.seaCreatureCocoonWarn, v -> c.seaCreatureCocoonWarn = v)
                            .desc("Title, 5 plings and a chat line when a sea creature gets cocooned")
                            .tags("cocoon", "sea creature", "fishing", "warning");
                    b.dropdown("  From Rarity", RARITIES, () -> c.seaCreatureCocoonRarity, v -> c.seaCreatureCocoonRarity = v);
                    b.channel("  Share", () -> c.seaCreatureCocoonChannel, v -> c.seaCreatureCocoonChannel = v)
                            .desc("Sends \"My <name> has been cocooned!\"");
                });
    }

    static boolean rarityOk(String name, String min) {
        int need = rank(min.toUpperCase(Locale.ROOT));
        if (need == 0) return true;
        SeaCreatureList.Info info = SeaCreatureList.info(name);
        return info != null && rank(info.rarity()) >= need;
    }

    private static int rank(String r) {
        return switch (r) {
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("CAUGHT!")) return;
        Matcher m = COCOON.matcher(t);
        if (!m.matches()) return;
        String name = m.group(1).trim();
        if (SeaCreatureList.info(name) == null) return;
        TeslaMapsConfig c = cfg();
        if (!rarityOk(name, c.seaCreatureCocoonRarity)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String msg = "§c" + name + " Has Been Cocooned";
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + msg));
        mc.gui.setTimes(0, 50, 10);
        mc.gui.setTitle(Component.literal(msg));
        mc.gui.setSubtitle(Component.literal(""));
        plings = 5;
        plingTick = 0;
        Announce.send(c.seaCreatureCocoonChannel, "My " + name + " has been cocooned!");
    }

    private static void tick() {
        if (plings <= 0 || plingTick++ % 3 != 0) return;
        plings--;
        Minecraft mc = Minecraft.getInstance();
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
    }
}
