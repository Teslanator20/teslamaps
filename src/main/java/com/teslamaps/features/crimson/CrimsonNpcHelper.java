package com.teslamaps.features.crimson;

// Ported from SkyHanni's PabloHelper.kt / SirihHelper.kt / AvoriusHelper.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.GetFromSacks;
import com.teslamaps.features.RescueMission;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrimsonNpcHelper {

    private static final List<Pattern> PABLO = List.of(
            Pattern.compile("\\[NPC] Pablo: (?:✆ )?Are you available\\? I desperately need an? (?<flower>[\\w ]+) today\\."),
            Pattern.compile("\\[NPC] Pablo: (?:✆ )?Bring me that (?<flower>[\\w ]+) as soon as you can!"),
            Pattern.compile("\\[NPC] Pablo: (?:✆ )?Could you bring me an? (?<flower>[\\w ]+)\\?"),
            Pattern.compile("\\[NPC] Pablo: (?:✆ )?I really need an? (?<flower>[\\w ]+) today, do you have one you could spare\\?"));
    private static final Pattern SIRIH = Pattern.compile("\\[NPC] Sirih:(?: ✆)? Oink.");
    private static final Set<String> AVORIUS = Set.of(
            "[NPC] Avorius: I am quite thirsty all the time, it's a rare condition.",
            "[NPC] Avorius: There is no sunlight either, it would be quite accommodating for a Vampire.",
            "[NPC] Avorius: Why are you looking at me that way? I am not a Vampire, you are!");

    private static long pabloAt, sirihAt, avoriusAt;

    private CrimsonNpcHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CrimsonNpcHelper").section("Crimson Isle").when(() -> cfg().crimsonNpcHelper)
                .serverChat(CrimsonNpcHelper::onChat)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Crimson Isle QoL");
                    b.toggle("Pablo / Sirih / Avorius Sack Links", () -> c.crimsonNpcHelper, v -> c.crimsonNpcHelper = v)
                            .desc("When Pablo asks for a flower, Sirih for Sulphur (Barbarian) or Avorius for a Cup of Blood (Mage) and you have none, a click-to-get-from-sacks line shows up")
                            .tags("pablo", "sirih", "avorius", "sacks", "gfs", "flower", "sulphur", "cup of blood");
                });
    }

    private static void onChat(String raw) {
        String text = StringUtil.stripTrim(raw);
        if (!text.startsWith("[NPC] ")) return;
        long now = System.currentTimeMillis();

        if (text.startsWith("[NPC] Pablo:")) {
            if (now - pabloAt < 300_000 || !SkyblockUtils.isInSkyblock()) return;
            for (Pattern p : PABLO) {
                Matcher m = p.matcher(text);
                if (!m.matches()) continue;
                String flower = m.group("flower").trim();
                if (hasByName(flower)) return;
                pabloAt = now;
                GetFromSacks.grabLink(flower, 1);
                return;
            }
            return;
        }
        if (!SkyblockUtils.isInArea("Crimson Isle")) return;
        if (text.startsWith("[NPC] Sirih:")) {
            if (now - sirihAt < 60_000 || !"barb".equals(RescueMission.faction()) || !SIRIH.matcher(text).matches()) return;
            if (hasById("SULPHUR_ORE")) return;
            sirihAt = now;
            GetFromSacks.grabLink("Sulphur", "SULPHUR_ORE", 1);
        } else if (text.startsWith("[NPC] Avorius:")) {
            if (now - avoriusAt < 15_000 || !"mage".equals(RescueMission.faction()) || !AVORIUS.contains(text)) return;
            if (hasById("CUP_OF_BLOOD")) return;
            avoriusAt = now;
            GetFromSacks.grabLink("Cup of Blood", "CUP_OF_BLOOD", 1);
        }
    }

    private static boolean hasByName(String name) {
        var p = Minecraft.getInstance().player;
        if (p == null) return true;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty() && s.getHoverName().getString().contains(name)) return true;
        }
        return false;
    }

    private static boolean hasById(String id) {
        var p = Minecraft.getInstance().player;
        if (p == null) return true;
        for (int i = 0; i < 36; i++) {
            if (id.equals(ItemUtil.skyblockId(p.getInventory().getItem(i)))) return true;
        }
        return false;
    }
}
