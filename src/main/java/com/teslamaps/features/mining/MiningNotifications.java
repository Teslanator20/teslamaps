package com.teslamaps.features.mining;

// After SkyHanni's MiningNotifications.kt, NEU's MiningStuff titanium alert and Skytils' powder ghast / skymall reminder
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Title + sound for rare mining spawns, cold and the Sky Mall. */
public final class MiningNotifications {

    private static final Pattern COLD = Pattern.compile("Cold: -?(\\d+)");
    private static final String SKY_MALL = "New day! Your Sky Mall buff changed!";

    private static Object lastLevel;
    private static boolean coldSent, ropeSent, on, dwarven, skyMallNext, midnight;
    private static int ticks;
    private static long titaniumAt;

    private MiningNotifications() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MiningNotifications").section("Mining").when(() -> cfg().miningNotify)
                .tick(MiningNotifications::tick)
                .serverChat(MiningNotifications::onChat)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Mining Notifications", () -> c.miningNotify, v -> c.miningNotify = v)
                            .desc("Title and sound for rare mining events: mineshaft portal, Suspicious Scrap, Golden/Diamond Goblin, Powder Ghast, Titanium, cold, Sky Mall.")
                            .tags("golden goblin", "diamond goblin", "titanium", "scrap", "powder ghast", "cold", "sky mall", "ascension rope");
                    b.toggle("  Mineshaft Portal", () -> c.miningNotifyMineshaft, v -> c.miningNotifyMineshaft = v);
                    b.toggle("  Suspicious Scrap", () -> c.miningNotifyScrap, v -> c.miningNotifyScrap = v);
                    b.toggle("  Golden / Diamond Goblin", () -> c.miningNotifyGoblin, v -> c.miningNotifyGoblin = v);
                    b.toggle("  Powder Ghast", () -> c.miningNotifyGhast, v -> c.miningNotifyGhast = v);
                    b.toggle("  Titanium Nearby", () -> c.miningNotifyTitanium, v -> c.miningNotifyTitanium = v)
                            .desc("Titanium appearing within 12 blocks");
                    b.toggle("    Only If Exposed To Air", () -> c.miningNotifyTitaniumVisible, v -> c.miningNotifyTitaniumVisible = v);
                    b.toggle("  Cold", () -> c.miningNotifyCold, v -> c.miningNotifyCold = v);
                    b.slider("    Cold Threshold", 1, 100, () -> (float) c.miningNotifyColdAt, v -> c.miningNotifyColdAt = Math.round(v), 0);
                    b.toggle("  Ascension Rope Message", () -> c.miningNotifyRope, v -> c.miningNotifyRope = v)
                            .desc("In a mineshaft at this much cold: a chat line you click to take an Ascension Rope from your sacks");
                    b.slider("    Rope At Cold", 1, 100, () -> (float) c.miningNotifyRopeAt, v -> c.miningNotifyRopeAt = Math.round(v), 0);
                    b.toggle("  Sky Mall Changed", () -> c.miningNotifySkyMall, v -> c.miningNotifySkyMall = v);
                    b.toggle("  Sky Mall Reset Reminder", () -> c.miningNotifySkyMallReset, v -> c.miningNotifySkyMallReset = v)
                            .desc("At 12:00am in the Dwarven Mines, with a clickable /hotm");
                    b.toggle("  Play Sound", () -> c.miningNotifySound, v -> c.miningNotifySound = v);
                });
    }

    public static boolean listening() {
        return on && dwarven && cfg().miningNotify && cfg().miningNotifyTitanium && cfg().section("Mining");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            coldSent = ropeSent = false;
        }
        if (++ticks % 10 != 0) return;
        on = Mining.onMiningIsland();
        String area = SkyblockUtils.getArea();
        dwarven = area.equalsIgnoreCase("Dwarven Mines");
        if (!on) return;
        TeslaMapsConfig c = cfg();
        int cold = -1;
        boolean atMidnight = false;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String line = ScoreboardUtils.cleanLine(raw);
            if (line.contains("12:00am")) atMidnight = true;
            if (!line.startsWith("Cold:")) continue;
            Matcher m = COLD.matcher(line);
            if (m.find()) cold = Integer.parseInt(m.group(1));
        }
        if (cold >= 0) {
            if (c.miningNotifyCold && !coldSent && cold >= c.miningNotifyColdAt) {
                coldSent = true;
                notify("§bCold");
            }
            if (c.miningNotifyRope && !ropeSent && cold >= c.miningNotifyRopeAt && area.equalsIgnoreCase("Mineshaft")) {
                ropeSent = true;
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §e§lCLICK HERE§r§e to grab §ax1 §9Ascension Rope§e from sacks!")
                        .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/gfs ASCENSION_ROPE 1"))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to get from sacks!")))));
            }
        } else if (coldSent || ropeSent) {
            coldSent = ropeSent = false;
        }
        if (atMidnight && !midnight && dwarven && c.miningNotifySkyMallReset && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cSky Mall reset ")
                    .append(Component.literal("§b[HOTM]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/hotm")))));
        }
        midnight = atMidnight;
    }

    private static void onChat(String text) {
        TeslaMapsConfig c = cfg();
        String t = StringUtil.stripTrim(text);
        if (c.miningNotifySkyMall) {
            if (t.equals(SKY_MALL)) { skyMallNext = true; return; }
            if (skyMallNext && t.startsWith("New buff: ")) {
                skyMallNext = false;
                notify("§bSky Mall", "§a" + t.substring(10));
                return;
            }
        }
        if (!on) return;
        if (c.miningNotifyMineshaft && t.equals("WOW! You found a Glacite Mineshaft portal!")) notify("§bMineshaft");
        else if (c.miningNotifyScrap && t.equals("EXCAVATOR! You found a Suspicious Scrap!")) notify("§9Suspicious Scrap");
        else if (c.miningNotifyGoblin && t.equals("A Golden Goblin has spawned!")) notify("§6Golden Goblin");
        else if (c.miningNotifyGoblin && t.equals("A Diamond Goblin has spawned!")) notify("§bDiamond Goblin");
        else if (c.miningNotifyGhast && t.startsWith("Find the Powder Ghast near the")) notify("§cPowder Ghast", "§7" + t.substring(5));
    }

    /** BlockUpdateMixin, main thread, before the block is set. */
    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!newState.is(Blocks.POLISHED_DIORITE) || oldState.is(Blocks.POLISHED_DIORITE)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.isSameThread()) return;
        if (pos.distToCenterSqr(mc.player.position()) > 144) return;
        if (cfg().miningNotifyTitaniumVisible) {
            boolean air = false;
            for (Direction d : Direction.values()) if (mc.level.getBlockState(pos.relative(d)).isAir()) { air = true; break; }
            if (!air) return;
        }
        long now = System.currentTimeMillis();
        if (now - titaniumAt < 3000) return;
        titaniumAt = now;
        notify("§7Titanium");
    }

    private static void notify(String title) {
        notify(title, "");
    }

    private static void notify(String title, String sub) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(5, 30, 5);
        mc.gui.setTitle(Component.literal(title));
        mc.gui.setSubtitle(Component.literal(sub));
        if (cfg().miningNotifySound && mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
    }
}
