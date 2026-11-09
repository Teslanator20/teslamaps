package com.teslamaps.features;

// After SkyHanni's gifting/UniqueGiftingOpportunitiesFeatures.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UniqueGifting {

    private static final Pattern GIVEN = Pattern.compile("^\\+1 Unique Gift given! To (.+)!$");
    private static final long EPOCH = 1560275700000L, MONTH = 37_200_000L, YEAR = MONTH * 12;
    private static final TextColor RED = TextColor.fromLegacyFormat(ChatFormatting.RED);

    private static Set<String> gifted;
    private static int timer;
    private static boolean december, holding;

    private UniqueGifting() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("UniqueGifting").section("Events").when(() -> cfg().uniqueGiftHighlight)
                .tick(UniqueGifting::tick)
                .serverChat(UniqueGifting::onChat)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Season of Jerry");
                    b.toggle("Unique Gifting Highlight", () -> c.uniqueGiftHighlight, v -> c.uniqueGiftHighlight = v)
                            .desc("In December: glows every player you have not given a gift yet. NPCs, Ironman and foreign Bingo players are skipped.");
                    b.toggle("  Only While Holding A Gift", () -> c.uniqueGiftHoldingOnly, v -> c.uniqueGiftHoldingOnly = v);
                    b.toggle("  Detect Gifted From Nametag", () -> c.uniqueGiftNametag, v -> c.uniqueGiftNametag = v)
                            .desc("Players with the red gift symbol over their head count as gifted.");
                    b.color("  Highlight Color", () -> c.uniqueGiftColor, v -> c.uniqueGiftColor = v);
                });
    }

    private static Set<String> gifted() {
        if (gifted == null) {
            gifted = new HashSet<>();
            for (String s : cfg().uniqueGiftedPlayers) gifted.add(s.toLowerCase(Locale.ROOT));
        }
        return gifted;
    }

    private static void addGifted(String name) {
        String n = name.trim();
        if (n.isEmpty() || !gifted().add(n.toLowerCase(Locale.ROOT))) return;
        cfg().uniqueGiftedPlayers.add(n);
        TeslaMapsConfig.save();
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("+1 Unique Gift given!")) return;
        Matcher m = GIVEN.matcher(t);
        if (!m.matches()) return;
        String name = m.group(1);
        int sp = name.lastIndexOf(' ');
        if (sp >= 0) name = name.substring(sp + 1);
        addGifted(name);
    }

    private static boolean isDecember() {
        long inYear = (System.currentTimeMillis() - EPOCH) % YEAR;
        return inYear / MONTH == 11;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++timer % 20 == 0) december = isDecember() && SkyblockUtils.isInSkyblock();
        if (timer % 5 == 0) {
            String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
            holding = id != null && id.endsWith("_GIFT");
        }
        if (!december) return;
        if (!cfg().uniqueGiftHighlight || (cfg().uniqueGiftHoldingOnly && !holding)) return;
        if (cfg().uniqueGiftNametag && timer % 10 == 0) scanNametags(mc);
        String own = mc.player.getDisplayName().getString();
        boolean ownIron = own.endsWith("♲"), ownBingo = own.endsWith("Ⓑ");
        int color = TeslaMapsConfig.parseColor(cfg().uniqueGiftColor) & 0xFFFFFF;
        Set<String> g = gifted();
        for (Player p : mc.level.players()) {
            if (!candidate(mc, p)) continue;
            String dn = p.getDisplayName().getString();
            if ((!ownIron && dn.endsWith("♲")) || (!ownBingo && dn.endsWith("Ⓑ"))) continue;
            if (g.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT))) continue;
            EntityGlow.mark(p, color);
        }
    }

    private static boolean candidate(Minecraft mc, Player p) {
        return p != mc.player && p.getUUID().version() == 4 && DeadMobHider.isRealPlayer(p);
    }

    private static void scanNametags(Minecraft mc) {
        for (ArmorStand stand : EntityCache.namedArmorStands()) {
            Component name = stand.getCustomName();
            if (name == null || !name.getString().contains("ꤥ") || !redSymbol(name)) continue;
            Player near = null;
            int count = 0;
            for (Player p : mc.level.players()) {
                if (!candidate(mc, p) || p.distanceToSqr(stand) > 4.0) continue;
                near = p;
                count++;
            }
            if (count == 1) addGifted(near.getGameProfile().name());
        }
    }

    private static boolean redSymbol(Component name) {
        String s = name.getString();
        if (s.contains("§cꤥ") || s.contains("§c§lꤥ")) return true;
        for (Component part : name.toFlatList()) {
            if (part.getString().contains("ꤥ") && RED.equals(part.getStyle().getColor())) return true;
        }
        return false;
    }
}
