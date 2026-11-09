package com.teslamaps.features.kuudra;

// Notifications: IQ's kuudraNotifications (a/R.java, a/S.java). Spawn side: Shizo's KuudraInfo.kt kuudraSpawnAlert.
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KuudraAlerts {

    static final Pattern PLACED = Pattern.compile("^(?:\\[[^]]+] )?(\\w{1,16}) recovered.*?\\((\\d)/6\\)");
    private static final Pattern DROPPED = Pattern.compile("^(?:\\[[^]]+] )?(\\w{1,16}) dropped Elle's supplies back into the lava! Oops!");
    static final Pattern NO_SPOT = Pattern.compile("^Party > (?:\\[[^]]*] ?)?(\\w{1,16}): No ?(.+?)!?$", Pattern.CASE_INSENSITIVE);

    private static String lastSide = "";

    private KuudraAlerts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Kuudra.LISTENERS.add(KuudraAlerts::onPhase);
        Feature.of("KuudraAlerts").section("Kuudra").when(() -> cfg().kuudraAlerts && Kuudra.inKuudra())
                .serverChat(KuudraAlerts::onServerChat)
                .chat(KuudraAlerts::onChat);
        Feature.of("KuudraSpawnDirection").section("Kuudra").when(() -> cfg().kuudraSpawnDirection && Kuudra.inKuudra())
                .tick(KuudraAlerts::tickDirection)
                .serverChat(KuudraAlerts::onDirectionChat)
                .config("Kuudra", b -> {
            TeslaMapsConfig c = cfg();
            b.label("Phases");
            b.info("Run and phase tracking is always on in Kuudra; the options below build on it");
            b.toggle("Kuudra Spawn Direction", () -> c.kuudraSpawnDirection, v -> c.kuudraSpawnDirection = v)
                    .desc("P4: big RIGHT / LEFT / FRONT / BACK title where Kuudra comes up, ping on a change");
            b.toggle("Kuudra Alerts", () -> c.kuudraAlerts, v -> c.kuudraAlerts = v)
                    .desc("Titles for Kuudra events that get lost in chat");
            b.toggle("  Play Sound", () -> c.kuudraAlertSound, v -> c.kuudraAlertSound = v);
            b.toggle("  Phase Change", () -> c.kuudraAlertPhase, v -> c.kuudraAlertPhase = v);
            b.toggle("  SOS Reminder", () -> c.kuudraAlertSos, v -> c.kuudraAlertSos = v)
                    .desc("\"Starting in 4 seconds\": reminder to place the SOS flare");
            b.toggle("  Buy Upgrades", () -> c.kuudraAlertUpgrades, v -> c.kuudraAlertUpgrades = v)
                    .desc("When Elle fishes up Kuudra");
            b.toggle("  Pick Up Supplies", () -> c.kuudraAlertPickup, v -> c.kuudraAlertPickup = v);
            b.toggle("  Supply Picked / Placed / Dropped", () -> c.kuudraAlertSupplyState, v -> c.kuudraAlertSupplyState = v)
                    .desc("Also \"Already Picking\" when someone else is on the same pile");
            b.toggle("  Supplies 6/6", () -> c.kuudraAlertSupplies, v -> c.kuudraAlertSupplies = v);
            b.toggle("  No <Spot> Calls", () -> c.kuudraAlertNoPre, v -> c.kuudraAlertNoPre = v)
                    .desc("\"Party > X: No Shop!\" as a title");
            b.toggle("  Build Started / Done", () -> c.kuudraAlertBuild, v -> c.kuudraAlertBuild = v);
            b.toggle("  Ichor Pool Cast", () -> c.kuudraAlertIchor, v -> c.kuudraAlertIchor = v);
            b.toggle("  Human Cannonball Bought", () -> c.kuudraAlertCannonball, v -> c.kuudraAlertCannonball = v);
            b.toggle("  No Kuudra Key", () -> c.kuudraAlertNoKey, v -> c.kuudraAlertNoKey = v)
                    .desc("At the end of a run when no Kuudra key is in the inventory");
        });
    }

    static void title(String text, boolean sound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        mc.gui.hud.setTimes(0, 40, 5);
        mc.gui.hud.setTitle(Component.literal(text));
        if (sound) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 1.0f));
    }

    private static void alert(String text) {
        title(text, cfg().kuudraAlertSound);
    }

    private static void onPhase(Kuudra.Phase prev, Kuudra.Phase now) {
        TeslaMapsConfig c = cfg();
        if (!c.kuudraAlerts || !c.section("Kuudra") || !Kuudra.inKuudra()) return;
        if (c.kuudraAlertBuild && prev == Kuudra.Phase.BUILD && (now == Kuudra.Phase.EATEN || now == Kuudra.Phase.DPS)) {
            alert("§a§lBUILD DONE");
        } else if (c.kuudraAlertNoKey && now == Kuudra.Phase.COMPLETED && !Kuudra.failed() && !hasKey()) {
            alert("§c§lNO KUUDRA KEY!");
        } else if (c.kuudraAlertPhase && now.running()) {
            alert("§6§l" + now.label.toUpperCase(Locale.ROOT));
        }
    }

    private static boolean hasKey() {
        var p = Minecraft.getInstance().player;
        if (p == null) return true;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (ItemUtil.skyblockId(inv.getItem(i)).contains("KUUDRA_KEY")) return true;
        }
        return false;
    }

    private static void onServerChat(String raw) {
        TeslaMapsConfig c = cfg();
        String s = StringUtil.stripTrim(raw);
        if (c.kuudraAlertSos && s.startsWith("Starting in 4 second")) alert("§b§lSOS REMINDER");
        else if (c.kuudraAlertUpgrades && s.equals(Kuudra.FISH_UP)) alert("§e§lBUY UPGRADES");
        else if (c.kuudraAlertPickup && s.equals(Kuudra.NOT_AGAIN)) alert("§e§lPICK UP SUPPLIES");
        else if (c.kuudraAlertBuild && s.endsWith("It's time to build the Ballista again! Cover me!")) alert("§a§lBUILD STARTED");
        else if (c.kuudraAlertIchor && s.equals("Casting Spell: Ichor Pool!")) alert("§b§lICHOR");
        else if (c.kuudraAlertCannonball && s.equals("You purchased Human Cannonball!")) alert("§e§lCANNONBALL");
        else if (c.kuudraAlertSupplyState && (s.equals("Someone else is currently trying to pick up these supplies!")
                || s.startsWith("You are already currently picking up"))) alert("§c§lALREADY PICKING");
        else if (c.kuudraAlertSupplyState && s.equals("You retrieved some of Elle's supplies from the Lava!")) title("§a§lPICKED UP", false);
        else if (c.kuudraAlertSupplyState && s.equals("You moved and the Chest slipped out of your hands!")) alert("§c§lDROPPED");
        else {
            Matcher m = PLACED.matcher(s);
            if (m.find()) {
                if (c.kuudraAlertSupplies && m.group(2).equals("6")) alert("§b§l6/6");
                else if (c.kuudraAlertSupplyState && isMe(m.group(1))) title("§a§lPLACED", c.kuudraAlertSound);
                return;
            }
            m = DROPPED.matcher(s);
            if (c.kuudraAlertSupplyState && m.find() && isMe(m.group(1))) alert("§c§lDROPPED");
        }
    }

    private static void onChat(String raw) {
        if (!cfg().kuudraAlertNoPre || Kuudra.phase() != Kuudra.Phase.SUPPLIES) return;
        Matcher m = NO_SPOT.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        String spot = KuudraNoPre.canonical(m.group(2));
        if (spot != null) alert("§4§lNO " + spot.toUpperCase(Locale.ROOT) + "!");
    }

    static boolean isMe(String name) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getGameProfile().name().equalsIgnoreCase(name);
    }

    private static void tickDirection() {
        MagmaCube boss = Kuudra.boss();
        if (boss == null || !boss.isAlive()) {
            lastSide = "";
            return;
        }
        float hp = boss.getHealth();
        if (hp >= 24900f && hp <= 25000f) showSide(boss);
        else lastSide = "";
    }

    private static void onDirectionChat(String raw) {
        if (!StringUtil.stripTrim(raw).equals(Kuudra.POW)) return;
        MagmaCube boss = Kuudra.boss();
        if (boss != null) showSide(boss);
    }

    private static void showSide(MagmaCube boss) {
        double x = boss.getX(), z = boss.getZ();
        String side = x < -128 ? "§c§lRIGHT!" : z > -84 ? "§2§lFRONT!" : x > -72 ? "§a§lLEFT!" : z < -132 ? "§4§lBACK!" : null;
        if (side == null) return;
        boolean changed = !side.equals(lastSide);
        lastSide = side;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(0, 25, 5);
        mc.gui.hud.setTitle(Component.literal(side));
        if (changed) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 1.0f));
    }
}
