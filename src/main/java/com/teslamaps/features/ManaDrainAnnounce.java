package com.teslamaps.features;

// Mana drain: Odin's nether/Misc.kt. Spell casts: IQ's ability announce.
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManaDrainAnnounce {

    private static final Pattern DRAIN = Pattern.compile("^Used Extreme Focus! \\((\\d+) Mana\\)$");
    private static final Pattern SPELL = Pattern.compile("^Casting Spell: (Spirit Spark|Hollowed Rush|Raging Wind|Ichor Pool)!$");

    private ManaDrainAnnounce() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ManaDrainAnnounce").section("Party").when(() -> cfg().manaDrainAnnounce || cfg().spellAnnounce)
                .serverChat(ManaDrainAnnounce::onChat)
                .config("Party", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Ability Callouts");
                    b.toggle("Mana Drain Announce", () -> c.manaDrainAnnounce, v -> c.manaDrainAnnounce = v)
                            .desc("Extreme Focus: sends \"Used N mana (X players nearby)\" to party chat (players within 7 blocks)");
                    b.toggle("Spell Cast Announce (Kuudra)", () -> c.spellAnnounce, v -> c.spellAnnounce = v)
                            .desc("Sends the spell and your coords to party chat while in Kuudra");
                    b.toggle("  Spirit Spark", () -> c.spellAnnounceSpark, v -> c.spellAnnounceSpark = v);
                    b.toggle("  Hollowed Rush", () -> c.spellAnnounceRush, v -> c.spellAnnounceRush = v);
                    b.toggle("  Raging Wind", () -> c.spellAnnounceWind, v -> c.spellAnnounceWind = v);
                    b.toggle("  Ichor Pool", () -> c.spellAnnounceIchor, v -> c.spellAnnounceIchor = v);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("Used Extreme Focus!") && !t.startsWith("Casting Spell: ")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        TeslaMapsConfig c = cfg();
        Matcher m;
        if (c.manaDrainAnnounce && (m = DRAIN.matcher(t)).matches()) {
            int n = 0;
            for (Player p : mc.level.players()) {
                if (p == mc.player || p.getUUID().version() != 4) continue;
                if (p.distanceToSqr(mc.player) < 49) n++;
            }
            Announce.party("Used " + m.group(1) + " mana (" + n + (n == 1 ? " player" : " players") + " nearby)");
            return;
        }
        if (c.spellAnnounce && (m = SPELL.matcher(t)).matches() && SkyblockUtils.getArea().contains("Kuudra")) {
            String spell = m.group(1);
            boolean on = switch (spell) {
                case "Spirit Spark" -> c.spellAnnounceSpark;
                case "Hollowed Rush" -> c.spellAnnounceRush;
                case "Raging Wind" -> c.spellAnnounceWind;
                default -> c.spellAnnounceIchor;
            };
            if (!on) return;
            BlockPos p = mc.player.blockPosition();
            Announce.party(spell + " cast at x: " + p.getX() + ", y: " + p.getY() + ", z: " + p.getZ());
        }
    }
}
